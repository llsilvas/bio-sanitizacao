package bio.prodesp.deduplicacao.service.service;

import bio.prodesp.deduplicacao.commons.exception.ABISViolacaoUnicidadeException;
import bio.prodesp.deduplicacao.commons.exception.CriterioEntradaException;
import bio.prodesp.deduplicacao.commons.exception.OSIAException;
import bio.prodesp.deduplicacao.commons.model.domain.ColetaMetadata;
import bio.prodesp.deduplicacao.commons.model.domain.DadoBiometricoMetadata;
import bio.prodesp.deduplicacao.commons.model.domain.TemplateMetadata;
import bio.prodesp.deduplicacao.commons.model.dto.ResultadoDeduplicacao;
import bio.prodesp.deduplicacao.commons.model.dto.osia.*;
import bio.prodesp.deduplicacao.commons.model.enums.StatusValidacao;
import bio.prodesp.deduplicacao.service.client.OSIAClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Serviço de deduplicação para coletas de origem IIRGD
 * Implementa o fluxo completo conforme especificação técnica
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeduplicacaoService {

    private final OSIAClient osiaClient;
    // TODO: Injetar ColetaRepository quando implementado
    // TODO: Injetar AuditService quando implementado
    // TODO: Injetar IdempotenciaService quando implementado

    /**
     * Processa coleta biométrica de origem IIRGD
     * RN004 - Fluxo completo: validação → verify 1:1 → identify 1:N → cadastro ABIS
     */
    @Transactional
    public ResultadoDeduplicacao processar(ColetaMetadata coleta) {
        log.info("Iniciando processamento IIRGD para CPF: {}, ID: {}",
                coleta.getCpf(), coleta.getIdColeta());

        try {
            // RN003 - Validar critérios de entrada
            validarCriteriosEntrada(coleta);

            // RN004 - Buscar encounters existentes
            EncountersResponse encounters = osiaClient.buscarEncounters(coleta.getCpf());

            // RN004 - Verificação 1:1
            VerifyResponse verifyResult = osiaClient.verificar11(
                    coleta.getCpf(),
                    converterParaBiometricDataOSIA(coleta)
            );

            // Aplicar regras de negócio baseado no resultado
            return aplicarRegrasIIRGD(coleta, encounters, verifyResult);

        } catch (CriterioEntradaException e) {
            log.warn("Critério de entrada não atendido: {}", e.getMessage());
            return ResultadoDeduplicacao.invalido("Critério de entrada: " + e.getMessage());
        } catch (OSIAException e) {
            return tratarErroOSIA(coleta, e);
        } catch (Exception e) {
            log.error("Erro inesperado no processamento", e);
            return ResultadoDeduplicacao.builder()
                    .status(StatusValidacao.ERRO_REPROCESSAVEL)
                    .mensagem(e.getMessage())
                    .build();
        }
    }

    /**
     * RN003 - Validação de critérios de entrada
     */
    private void validarCriteriosEntrada(ColetaMetadata coleta) {
        // validacao_pendente = false
        if (Boolean.TRUE.equals(coleta.getValidacaoPendente())) {
            throw new CriterioEntradaException("Coleta possui validação pendente");
        }

        // CPF válido
        if (!isValidCPF(coleta.getCpf())) {
            throw new CriterioEntradaException("CPF inválido");
        }

        // Idade > 15 anos
        if (coleta.getDataNascimento() != null) {
            int idade = calcularIdade(coleta.getDataNascimento());
            if (idade <= 15) {
                throw new CriterioEntradaException("Idade deve ser maior que 15 anos");
            }
        }
    }

    /**
     * Aplica regras de negócio IIRGD baseado nos resultados OSIA
     */
    private ResultadoDeduplicacao aplicarRegrasIIRGD(
            ColetaMetadata coleta,
            EncountersResponse encounters,
            VerifyResponse verifyResult) {

        // Cenário 1: Verify encontrou e deu match
        if (verifyResult.isVerified()) {
            return processarMatchEncontrado(coleta, encounters, verifyResult);
        }

        // Cenário 2: Verify encontrou mas não deu match (RN009)
        if (verifyResult.isPessoaEncontrada() && !verifyResult.isVerified()) {
            return marcarInconclusivo(coleta, "Pessoa encontrada mas biometria não corresponde");
        }

        // Cenário 3: Verify não encontrou - executar busca 1:N
        return processarBusca1N(coleta);
    }

    /**
     * RN004 - Processa quando verify 1:1 encontrou match
     */
    private ResultadoDeduplicacao processarMatchEncontrado(
            ColetaMetadata coleta,
            EncountersResponse encounters,
            VerifyResponse verifyResult) {

        int totalEncounters = encounters.getTotalCount();

        // RN004 - Apenas 1 encounter
        if (totalEncounters == 1) {
            String abisEncounterId = cadastrarNoABIS(coleta);
            // TODO: atualizarStatus(coleta, StatusValidacao.VALIDA);
            return ResultadoDeduplicacao.builder()
                    .status(StatusValidacao.VALIDA)
                    .abisEncounterId(abisEncounterId)
                    .matchScore(verifyResult.getScore())
                    .build();
        }

        // RN004 - 2 ou mais encounters - aplicar RN005
        if (totalEncounters >= 2) {
            return aplicarSelecaoPorNFIQ2(coleta, encounters);
        }

        return ResultadoDeduplicacao.builder()
                .status(StatusValidacao.ERRO_REPROCESSAVEL)
                .mensagem("Estado inconsistente: verify OK mas 0 encounters")
                .build();
    }

    /**
     * RN005 - Seleção por NFIQ2: manter as 2 melhores coletas
     */
    private ResultadoDeduplicacao aplicarSelecaoPorNFIQ2(
            ColetaMetadata coleta,
            EncountersResponse encounters) {

        // Ordenar por NFIQ2 score (maior = melhor)
        List<Encounter> ordenados = encounters.getEncounters().stream()
                .sorted(Comparator.comparing(
                        e -> e.getMetadata().getNfiq2Score(),
                        Comparator.reverseOrder()))
                .collect(Collectors.toList());

        Encounter melhor = ordenados.get(0);
        Encounter segundoMelhor = ordenados.size() > 1 ? ordenados.get(1) : null;

        // Obter o melhor NFIQ2 score da coleta atual
        Integer nfiq2Coleta = obterMelhorNFIQ2(coleta);

        // Se a coleta atual é melhor que a melhor existente
        if (nfiq2Coleta > melhor.getMetadata().getNfiq2Score()) {
            if (segundoMelhor != null) {
                osiaClient.deletarEncounter(coleta.getCpf(), segundoMelhor.getEncounterId());
            }
            String abisEncounterId = cadastrarNoABIS(coleta);
            // TODO: atualizarStatus(coleta, StatusValidacao.VALIDA);
            return ResultadoDeduplicacao.builder()
                    .status(StatusValidacao.VALIDA)
                    .abisEncounterId(abisEncounterId)
                    .mensagem("Substituiu encounter com menor qualidade")
                    .build();
        }

        // Se a coleta atual é melhor que o segundo melhor
        if (segundoMelhor != null && nfiq2Coleta > segundoMelhor.getMetadata().getNfiq2Score()) {
            osiaClient.deletarEncounter(coleta.getCpf(), segundoMelhor.getEncounterId());
            String abisEncounterId = cadastrarNoABIS(coleta);
            // TODO: atualizarStatus(coleta, StatusValidacao.VALIDA);
            return ResultadoDeduplicacao.builder()
                    .status(StatusValidacao.VALIDA)
                    .abisEncounterId(abisEncounterId)
                    .mensagem("Substituiu segundo melhor encounter")
                    .build();
        }

        // Coleta atual tem qualidade inferior às 2 existentes
        // TODO: atualizarStatus(coleta, StatusValidacao.VALIDA);
        // TODO: auditService.registrar("Coleta descartada por NFIQ2 inferior", coleta);
        log.info("Coleta descartada por NFIQ2 inferior - CPF: {}, NFIQ2: {}", coleta.getCpf(), nfiq2Coleta);
        return ResultadoDeduplicacao.builder()
                .status(StatusValidacao.VALIDA)
                .mensagem("Descartada por qualidade inferior")
                .build();
    }

    /**
     * RN008 - Processa busca 1:N quando verify não encontrou
     */
    private ResultadoDeduplicacao processarBusca1N(ColetaMetadata coleta) {
        // RN004 - Executar identificação 1:N
        IdentifyResponse identifyResult = osiaClient.identificar1N(
                converterParaBiometricDataOSIA(coleta)
        );

        int matches = identifyResult.getCandidates() != null
                ? identifyResult.getCandidates().size()
                : 0;

        // RN008 - Nenhum match - coleta válida
        if (matches == 0) {
            String abisEncounterId = cadastrarNoABIS(coleta);
            // TODO: atualizarStatus(coleta, StatusValidacao.VALIDA);
            return ResultadoDeduplicacao.builder()
                    .status(StatusValidacao.VALIDA)
                    .abisEncounterId(abisEncounterId)
                    .mensagem("Nenhum match em 1:N - cadastrada como nova")
                    .build();
        }

        // 1 ou mais matches - inconclusivo
        String motivo = matches == 1
                ? "Match único em busca 1:N"
                : String.format("Múltiplos matches em 1:N (%d candidatos)", matches);

        return marcarInconclusivo(coleta, motivo);
    }

    /**
     * RN006 - Marca coleta como inconclusiva
     */
    private ResultadoDeduplicacao marcarInconclusivo(ColetaMetadata coleta, String motivo) {
        log.warn("Marcando coleta como INCONCLUSIVA - CPF: {}, Motivo: {}", coleta.getCpf(), motivo);

        // TODO: Implementar quando tiver repository
        // coleta.setStatusValidacao(StatusValidacao.INCONCLUSIVA);
        // coleta.setValidacaoPendente(true);
        // coleta.setMotivoInconclusivo(motivo);
        // coletaRepository.save(coleta);
        // auditService.registrar("Coleta marcada como INCONCLUSIVA: " + motivo, coleta);

        return ResultadoDeduplicacao.builder()
                .status(StatusValidacao.INCONCLUSIVA)
                .motivoInconclusivo(motivo)
                .build();
    }

    /**
     * RN007 - Cadastra coleta no ABIS
     */
    private String cadastrarNoABIS(ColetaMetadata coleta) {
        try {
            log.info("Cadastrando coleta no ABIS - CPF: {}, ID: {}", coleta.getCpf(), coleta.getIdColeta());

            EnrollResponse response = osiaClient.cadastrarEncounter(
                    coleta.getCpf(),
                    coleta.getIdColeta(),
                    converterParaBiometricDataOSIA(coleta)
            );

            log.info("Coleta cadastrada no ABIS com sucesso - Encounter ID: {}", response.getEncounterId());

            // TODO: Registrar ID do ABIS para controle
            // coleta.setAbisRecordId(response.getEncounterId());
            // coletaRepository.save(coleta);
            // auditService.registrarCadastroABIS(coleta, response);

            return response.getEncounterId();

        } catch (OSIAException e) {
            // RN010 - Tratar falha no ABIS
            if (e.getStatusCode() == 409) {
                log.error("Violação de unicidade no ABIS - CPF: {}", coleta.getCpf());
                // TODO: coleta.setStatusValidacao(StatusValidacao.INVALIDA);
                // TODO: coleta.setMotivoRejeicao("Violação de unicidade no ABIS: " + e.getMessage());
                // TODO: coletaRepository.save(coleta);
                // TODO: auditService.registrarErro("Violação de unicidade", coleta, e);
                throw new ABISViolacaoUnicidadeException(e.getMessage(), e);
            }
            throw e;
        }
    }

    /**
     * Trata erros de comunicação com OSIA
     */
    private ResultadoDeduplicacao tratarErroOSIA(ColetaMetadata coleta, OSIAException e) {
        log.error("Erro na comunicação com OSIA - CPF: {}", coleta.getCpf(), e);

        // Erros recuperáveis (5xx) - marcar para reprocessamento
        if (e.isRetryable()) {
            return ResultadoDeduplicacao.builder()
                    .status(StatusValidacao.ERRO_REPROCESSAVEL)
                    .mensagem("Erro OSIA retentável: " + e.getMessage())
                    .build();
        }

        // Erros não recuperáveis (4xx) - marcar como inválido
        log.error("Erro OSIA não retentável - marcando como INVALIDA - CPF: {}", coleta.getCpf());
        // TODO: coleta.setStatusValidacao(StatusValidacao.INVALIDA);
        // TODO: coleta.setMotivoRejeicao("Erro OSIA: " + e.getMessage());
        // TODO: coletaRepository.save(coleta);

        return ResultadoDeduplicacao.builder()
                .status(StatusValidacao.INVALIDA)
                .motivoRejeicao("Erro OSIA: " + e.getMessage())
                .build();
    }

    // ========== Métodos Utilitários ==========

    /**
     * Converte ColetaMetadata para BiometricDataOSIA
     *
     * @param coleta Coleta com dados biométricos
     * @return BiometricDataOSIA formatado para ABIS/OSIA
     * @throws CriterioEntradaException se não houver dados biométricos
     */
    private BiometricDataOSIA converterParaBiometricDataOSIA(ColetaMetadata coleta) {
        if (coleta.getDadosBiometricos() == null || coleta.getDadosBiometricos().isEmpty()) {
            throw new CriterioEntradaException("Nenhum dado biométrico fornecido");
        }

        List<BiometricDataOSIA.FingerprintOSIA> fingerprints = new ArrayList<>();
        BiometricDataOSIA.FaceImageOSIA face = null;
        List<BiometricDataOSIA.IrisImageOSIA> iris = new ArrayList<>();

        for (DadoBiometricoMetadata dado : coleta.getDadosBiometricos()) {
            String tipo = dado.getTipo() != null ? dado.getTipo().toUpperCase() : "";

            switch (tipo) {
                case "IMPRESSAO_DIGITAL", "FINGERPRINT" -> {
                    BiometricDataOSIA.FingerprintOSIA fingerprint = converterParaFingerprint(dado);
                    if (fingerprint != null) {
                        fingerprints.add(fingerprint);
                    }
                }
                case "FACE", "FACIAL" -> {
                    face = converterParaFace(dado);
                }
                case "IRIS" -> {
                    BiometricDataOSIA.IrisImageOSIA irisImage = converterParaIris(dado);
                    if (irisImage != null) {
                        iris.add(irisImage);
                    }
                }
                default -> log.warn("Tipo biométrico desconhecido: {} - ignorando", tipo);
            }
        }

        return BiometricDataOSIA.builder()
                .fingerprints(fingerprints.isEmpty() ? null : fingerprints)
                .face(face)
                .iris(iris.isEmpty() ? null : iris)
                .build();
    }

    /**
     * Converte DadoBiometricoMetadata para FingerprintOSIA
     */
    private BiometricDataOSIA.FingerprintOSIA converterParaFingerprint(DadoBiometricoMetadata dado) {
        if (dado.getTemplates() == null || dado.getTemplates().isEmpty()) {
            log.warn("Dado biométrico sem templates - ignorando");
            return null;
        }

        // Pega o primeiro template (pode haver múltiplos formatos)
        TemplateMetadata template = dado.getTemplates().get(0);

        // Converte byte[] para Base64
        String templateBase64 = null;
        if (template.getDados() != null) {
            templateBase64 = java.util.Base64.getEncoder().encodeToString(template.getDados());
        }

        return BiometricDataOSIA.FingerprintOSIA.builder()
                .position(converterPosicaoParaOSIA(dado.getPosicaoDedo()))
                .template(templateBase64)
                .format(template.getFormato())
                .quality(dado.getQualidadeNfiq())
                .build();
    }

    /**
     * Converte DadoBiometricoMetadata para FaceImageOSIA
     */
    private BiometricDataOSIA.FaceImageOSIA converterParaFace(DadoBiometricoMetadata dado) {
        if (dado.getTemplates() == null || dado.getTemplates().isEmpty()) {
            log.warn("Dado facial sem templates - ignorando");
            return null;
        }

        TemplateMetadata template = dado.getTemplates().get(0);

        String imageBase64 = null;
        if (template.getDados() != null) {
            imageBase64 = java.util.Base64.getEncoder().encodeToString(template.getDados());
        }

        return BiometricDataOSIA.FaceImageOSIA.builder()
                .image(imageBase64)
                .format(template.getFormato())
                .quality(dado.getQualidadeNfiq())
                .build();
    }

    /**
     * Converte DadoBiometricoMetadata para IrisImageOSIA
     */
    private BiometricDataOSIA.IrisImageOSIA converterParaIris(DadoBiometricoMetadata dado) {
        if (dado.getTemplates() == null || dado.getTemplates().isEmpty()) {
            log.warn("Dado de íris sem templates - ignorando");
            return null;
        }

        TemplateMetadata template = dado.getTemplates().get(0);

        String imageBase64 = null;
        if (template.getDados() != null) {
            imageBase64 = java.util.Base64.getEncoder().encodeToString(template.getDados());
        }

        // Determina posição da íris (LEFT/RIGHT) baseado no código
        String position = "UNKNOWN";
        if (dado.getPosicaoDedo() != null) {
            // Convenção: códigos ímpares = direita, pares = esquerda (exemplo)
            // Ajustar conforme tabela real
            position = dado.getPosicaoDedo() % 2 == 0 ? "LEFT" : "RIGHT";
        }

        return BiometricDataOSIA.IrisImageOSIA.builder()
                .position(position)
                .image(imageBase64)
                .format(template.getFormato())
                .quality(dado.getQualidadeNfiq())
                .build();
    }

    /**
     * Converte código de posição do dedo para formato OSIA
     *
     * @param posicaoDedo Código numérico (1-10)
     * @return String OSIA format (RIGHT_THUMB, LEFT_INDEX, etc)
     */
    private String converterPosicaoParaOSIA(Integer posicaoDedo) {
        if (posicaoDedo == null) {
            return "UNKNOWN";
        }

        return switch (posicaoDedo) {
            case 1 -> "RIGHT_THUMB";
            case 2 -> "RIGHT_INDEX";
            case 3 -> "RIGHT_MIDDLE";
            case 4 -> "RIGHT_RING";
            case 5 -> "RIGHT_LITTLE";
            case 6 -> "LEFT_THUMB";
            case 7 -> "LEFT_INDEX";
            case 8 -> "LEFT_MIDDLE";
            case 9 -> "LEFT_RING";
            case 10 -> "LEFT_LITTLE";
            default -> "UNKNOWN";
        };
    }

    /**
     * Obtém o melhor (maior) score NFIQ2 dentre todos os dados biométricos da coleta
     *
     * @param coleta Coleta com dados biométricos
     * @return Maior NFIQ2 score encontrado, ou 0 se nenhum score disponível
     */
    private Integer obterMelhorNFIQ2(ColetaMetadata coleta) {
        if (coleta.getDadosBiometricos() == null || coleta.getDadosBiometricos().isEmpty()) {
            log.warn("Nenhum dado biométrico para extrair NFIQ2 - retornando 0");
            return 0;
        }

        return coleta.getDadosBiometricos().stream()
                .map(DadoBiometricoMetadata::getQualidadeNfiq)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0);
    }

    /**
     * Valida CPF (implementação simplificada)
     */
    private boolean isValidCPF(String cpf) {
        if (cpf == null || cpf.trim().isEmpty()) {
            return false;
        }
        // Remove caracteres não numéricos
        String cpfNumeros = cpf.replaceAll("\\D", "");
        // Verifica se tem 11 dígitos
        return cpfNumeros.length() == 11;
    }

    /**
     * Calcula idade a partir de java.util.Date
     */
    private int calcularIdade(Date dataNascimento) {
        if (dataNascimento == null) {
            return 0;
        }
        LocalDate nascimento = dataNascimento.toInstant()
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate();
        return Period.between(nascimento, LocalDate.now()).getYears();
    }
}
