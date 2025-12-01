package bio.prodesp.deduplicacao.service;

import bio.prodesp.deduplicacao.commons.exception.ABISViolacaoUnicidadeException;
import bio.prodesp.deduplicacao.commons.exception.CriterioEntradaException;
import bio.prodesp.deduplicacao.commons.exception.OSIAException;
import bio.prodesp.deduplicacao.commons.model.domain.ColetaMetadata;
import bio.prodesp.deduplicacao.commons.model.domain.DadoBiometricoMetadata;
import bio.prodesp.deduplicacao.commons.model.domain.TemplateMetadata;
import bio.prodesp.deduplicacao.commons.model.dto.ResultadoDeduplicacao;
import bio.prodesp.deduplicacao.commons.model.dto.osia.*;
import bio.prodesp.deduplicacao.commons.model.enums.StatusValidacao;
import bio.prodesp.deduplicacao.repository.ColetaRepository;
import bio.prodesp.deduplicacao.service.audit.AuditService;
import bio.prodesp.deduplicacao.client.OSIAClient;
import bio.prodesp.deduplicacao.service.idempotencia.IdempotenciaService;
import bio.prodesp.deduplicacao.util.CpfValidator;
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
 * Serviço de deduplicação biométrica para coletas de origem IIRGD.
 *
 * <p>Implementa o fluxo completo de processamento conforme especificação técnica,
 * incluindo validação de entrada, verificação biométrica 1:1, busca 1:N, e cadastro no ABIS.
 *
 * <h2>Regras de Negócio Implementadas:</h2>
 * <ul>
 *   <li><b>RN003</b>: Validação de critérios de entrada (idade, CPF, qualidade)</li>
 *   <li><b>RN004</b>: Verificação 1:1 contra encounters existentes</li>
 *   <li><b>RN005</b>: Seleção por qualidade NFIQ2 (mantém 2 melhores)</li>
 *   <li><b>RN006</b>: Marcação de coletas inconclusivas</li>
 *   <li><b>RN007</b>: Cadastro de novos encounters no ABIS</li>
 *   <li><b>RN008</b>: Busca 1:N quando verify não encontra</li>
 *   <li><b>RN009</b>: Análise de múltiplos matches</li>
 *   <li><b>RN010</b>: Tratamento de falhas do ABIS</li>
 * </ul>
 *
 * <h2>Características Técnicas:</h2>
 * <ul>
 *   <li><b>Idempotência</b>: Redis (L1) + OpenSearch (L2) com TTL de 24h</li>
 *   <li><b>Transacional</b>: Operações atômicas com rollback automático</li>
 *   <li><b>Auditoria</b>: Logging assíncrono de todas as operações (LGPD)</li>
 *   <li><b>Resiliência</b>: Circuit breaker e retry via Resilience4j</li>
 * </ul>
 *
 * @author Bio Sanitização Team
 * @version 1.0.0
 * @since 2025-01
 * @see OSIAClient
 * @see IdempotenciaService
 * @see AuditService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeduplicacaoService {

    private final OSIAClient osiaClient;
    private final ColetaRepository coletaRepository;
    private final AuditService auditService;
    private final IdempotenciaService idempotenciaService;

    /**
     * Processa uma coleta biométrica através do fluxo completo de deduplicação IIRGD.
     *
     * <p><b>Fluxo de Processamento:</b></p>
     * <ol>
     *   <li><b>Verificação de Idempotência</b>: Consulta Redis/OpenSearch para evitar reprocessamento</li>
     *   <li><b>Validação RN003</b>: Valida critérios de entrada (CPF, idade, qualidade)</li>
     *   <li><b>Busca Encounters RN004</b>: Consulta encounters existentes no ABIS via CPF</li>
     *   <li><b>Verificação 1:1 RN004</b>: Compara biometria com encounters encontrados</li>
     *   <li><b>Aplicação de Regras RN005-RN010</b>: Decide ação baseado nos resultados</li>
     *   <li><b>Atualização OpenSearch</b>: Partial update do documento (não substituição)</li>
     *   <li><b>Auditoria</b>: Registro assíncrono para compliance LGPD</li>
     *   <li><b>Registro de Idempotência</b>: Armazena resultado no Redis (24h TTL)</li>
     * </ol>
     *
     * <p><b>Possíveis Status de Retorno:</b></p>
     * <ul>
     *   <li>{@link StatusValidacao#VALIDA}: Coleta processada com sucesso</li>
     *   <li>{@link StatusValidacao#INCONCLUSIVA}: Requer análise manual (RN006)</li>
     *   <li>{@link StatusValidacao#INVALIDA}: Falhou em critérios de entrada ou qualidade</li>
     *   <li>{@link StatusValidacao#ERRO_REPROCESSAVEL}: Falha temporária, pode reprocessar</li>
     * </ul>
     *
     * <p><b>Tratamento de Exceções:</b></p>
     * <ul>
     *   <li>{@link CriterioEntradaException}: Retorna INVALIDA com motivo específico</li>
     *   <li>{@link ABISViolacaoUnicidadeException}: Retorna INVALIDA (conflito de duplicação)</li>
     *   <li>{@link OSIAException}: Retorna ERRO_REPROCESSAVEL ou INVALIDA conforme retryable</li>
     *   <li>{@link Exception}: Retorna ERRO_REPROCESSAVEL para falhas inesperadas</li>
     * </ul>
     *
     * @param coleta Metadados da coleta biométrica contendo CPF, templates, e informações pessoais
     * @return Resultado do processamento com status, matchScore, e identificadores ABIS
     * @throws IllegalArgumentException se coleta for null ou inválida
     * @see #validarCriteriosEntrada(ColetaMetadata)
     * @see #aplicarRegrasIIRGD(ColetaMetadata, EncountersResponse, VerifyResponse)
     */
    @Transactional
    public ResultadoDeduplicacao processar(ColetaMetadata coleta) {
        String idempotencyKey = idempotenciaService.gerarChave(
            coleta.getCpf(),
            coleta.getIdColeta()
        );

        // ✅ VERIFICAR IDEMPOTÊNCIA (Redis → OpenSearch fallback)
        java.util.Optional<ResultadoDeduplicacao> resultadoAnterior =
            idempotenciaService.verificar(idempotencyKey);

        if (resultadoAnterior.isPresent()) {
            log.info("Retornando resultado já processado - CPF: {}, ID: {}",
                    coleta.getCpf(), coleta.getIdColeta());
            return resultadoAnterior.get();
        }

        log.info("Iniciando processamento IIRGD para CPF: {}, ID: {}",
                coleta.getCpf(), coleta.getIdColeta());

        long startTime = System.currentTimeMillis();

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
            ResultadoDeduplicacao resultado = aplicarRegrasIIRGD(coleta, encounters, verifyResult);

            // ✅ ATUALIZAR OPENSEARCH (partial update)
            boolean atualizado = coletaRepository.atualizarStatusProcessamento(
                coleta.getIdColeta(),
                resultado.getStatus(),
                resultado.getAbisEncounterId(),
                resultado.getMotivoInconclusivo(),
                resultado.getMotivoRejeicao(),
                resultado.getMatchScore() != null ? resultado.getMatchScore().doubleValue() : null
            );

            if (!atualizado) {
                log.error("Falha ao atualizar OpenSearch - ID: {}", coleta.getIdColeta());
            }

            // ✅ REGISTRAR AUDITORIA
            long duracao = System.currentTimeMillis() - startTime;
            auditService.registrarComDuracao(
                "PROCESSAMENTO_CONCLUIDO",
                coleta.getCpf(),
                coleta.getIdColeta(),
                duracao,
                "Status: " + resultado.getStatus()
            );

            // ✅ REGISTRAR IDEMPOTÊNCIA (Redis - 24h TTL)
            idempotenciaService.registrar(idempotencyKey, resultado, 1440);

            return resultado;

        } catch (CriterioEntradaException e) {
            log.warn("Critério de entrada não atendido: {}", e.getMessage());

            ResultadoDeduplicacao resultado = ResultadoDeduplicacao.invalido(
                    "Critério de entrada: " + e.getMessage()
            );

            // ✅ AUDITAR FALHA DE VALIDAÇÃO
            auditService.registrarErro(
                    "CRITERIO_ENTRADA",
                    coleta.getCpf(),
                    coleta.getIdColeta(),
                    e
            );

            return resultado;

        }catch (ABISViolacaoUnicidadeException e){
            log.warn("Violação de unicidade no ABIS: {}", e.getMessage());

            ResultadoDeduplicacao resultado = ResultadoDeduplicacao.builder()
                    .status(StatusValidacao.INVALIDA)
                    .motivoRejeicao("Violação de unicidade no ABIS: " + e.getMessage())
                    .build();

            Exception auditException =
                    (!(e.getCause() instanceof Exception)) ? e : (Exception) e.getCause();

            // ✅ AUDITAR FALHA DE VALIDAÇÃO
            auditService.registrarErro(
                    "VIOLACAO_UNICIDADE_ABIS",
                    coleta.getCpf(),
                    coleta.getIdColeta(),
                    auditException
            );

            coletaRepository.marcarInvalida(coleta.getIdColeta(), "Violação de unicidade no ABIS: " + e.getMessage());

            return resultado;


        } catch (OSIAException e) {
            ResultadoDeduplicacao resultado = tratarErroOSIA(coleta, e);

            // ✅ AUDITAR ERRO OSIA
            auditService.registrarErro(
                "ERRO_OSIA",
                coleta.getCpf(),
                coleta.getIdColeta(),
                e
            );

            // ✅ MARCAR ERRO NO OPENSEARCH
            if (resultado.getStatus() == StatusValidacao.ERRO_REPROCESSAVEL) {
                coletaRepository.marcarErroReprocessavel(coleta.getIdColeta(), e.getMessage());
            } else if (resultado.getStatus() == StatusValidacao.INVALIDA) {
                coletaRepository.marcarInvalida(coleta.getIdColeta(), e.getMessage());
            }

            return resultado;

        } catch (Exception e) {
            log.error("Erro inesperado no processamento", e);

            // ✅ AUDITAR ERRO INESPERADO
            auditService.registrarErro(
                "ERRO_INESPERADO",
                coleta.getCpf(),
                coleta.getIdColeta(),
                e
            );

            // ✅ MARCAR ERRO REPROCESSÁVEL NO OPENSEARCH
            coletaRepository.marcarErroReprocessavel(coleta.getIdColeta(), e.getMessage());

            return ResultadoDeduplicacao.builder()
                    .status(StatusValidacao.ERRO_REPROCESSAVEL)
                    .mensagem(e.getMessage())
                    .build();
        }
    }

    /**
     * Valida critérios de entrada conforme RN003.
     *
     * <p><b>Critérios Validados:</b></p>
     * <ul>
     *   <li><b>Validação Pendente</b>: Deve ser {@code false} (coleta já validada)</li>
     *   <li><b>CPF</b>: Deve ser válido (11 dígitos + verificadores corretos)</li>
     *   <li><b>Idade</b>: Deve ser maior que 15 anos (baseado na data de nascimento)</li>
     *   <li><b>Dados Biométricos</b>: Deve conter ao menos um template (validado implicitamente)</li>
     * </ul>
     *
     * @param coleta Coleta a ser validada
     * @throws CriterioEntradaException se qualquer critério não for atendido
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
     * Aplica regras de negócio IIRGD baseado nos resultados da verificação biométrica.
     *
     * <p><b>Fluxo de Decisão:</b></p>
     * <pre>
     * 1. SE verify.isVerified() = true
     *    → Processo com RN004/RN005 (match encontrado)
     *
     * 2. SE verify.pessoaEncontrada() = true MAS verify.isVerified() = false
     *    → RN006: Marca como INCONCLUSIVA (pessoa existe mas biometria não bate)
     *
     * 3. SE verify.pessoaEncontrada() = false
     *    → RN008: Executa busca 1:N (identify)
     *       → Se 0 matches: Cadastra como nova pessoa (RN007)
     *       → Se 1 match: RN006 INCONCLUSIVA (possível duplicação)
     *       → Se 2+ matches: RN009 INCONCLUSIVA (múltiplas correspondências)
     * </pre>
     *
     * @param coleta Coleta sendo processada
     * @param encounters Encounters existentes retornados do ABIS
     * @param verifyResult Resultado da verificação 1:1
     * @return Resultado da deduplicação com status e informações de match
     * @see #processarMatchEncontrado(ColetaMetadata, EncountersResponse, VerifyResponse)
     * @see #processarBusca1N(ColetaMetadata)
     * @see #marcarInconclusivo(ColetaMetadata, String)
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
     * Processa coleta quando a verificação 1:1 encontrou match biométrico (RN004/RN005).
     *
     * <p><b>Lógica de Processamento por Quantidade de Encounters:</b></p>
     * <ul>
     *   <li><b>1 encounter</b>: Cadastra coleta atual como segundo encounter (RN004)</li>
     *   <li><b>2 encounters</b>: Aplica RN005 - mantém os 2 melhores por NFIQ2:
     *     <ul>
     *       <li>Se coleta atual é melhor que o melhor: Substitui o 2º melhor</li>
     *       <li>Se coleta atual é melhor que o 2º: Substitui o 2º melhor</li>
     *       <li>Se coleta atual é pior que ambos: Descarta (retorna VALIDA sem cadastrar)</li>
     *     </ul>
     *   </li>
     *   <li><b>Outros</b>: Situação anômala, marca como inconclusiva</li>
     * </ul>
     *
     * <p><b>Nota</b>: Score NFIQ2 determina qualidade - maior é melhor (0-100).
     *
     * @param coleta Coleta sendo processada
     * @param encounters Encounters retornados do ABIS (1 ou 2 esperados)
     * @param verifyResult Resultado do verify contendo matchScore
     * @return Resultado com status VALIDA e abisEncounterId quando cadastrado
     * @see #obterMelhorNFIQ2(ColetaMetadata)
     * @see #cadastrarNoABIS(ColetaMetadata)
     */
    private ResultadoDeduplicacao processarMatchEncontrado(
            ColetaMetadata coleta,
            EncountersResponse encounters,
            VerifyResponse verifyResult) {

        int totalEncounters = encounters.getTotalCount();

        // RN004 - Apenas 1 encounter
        if (totalEncounters == 1) {
            String abisEncounterId = cadastrarNoABIS(coleta);
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
            return ResultadoDeduplicacao.builder()
                    .status(StatusValidacao.VALIDA)
                    .abisEncounterId(abisEncounterId)
                    .mensagem("Substituiu segundo melhor encounter")
                    .build();
        }

        // Coleta atual tem qualidade inferior às 2 existentes
        log.info("Coleta descartada por NFIQ2 inferior - CPF: {}, NFIQ2: {}",
                coleta.getCpf(), nfiq2Coleta);
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
        log.warn("Marcando coleta como INCONCLUSIVA - CPF: {}, Motivo: {}",
                coleta.getCpf(), motivo);

        // ✅ AUDITAR INCONCLUSIVA
        auditService.registrar(
            "COLETA_INCONCLUSIVA",
            coleta.getCpf(),
            coleta.getIdColeta(),
            motivo
        );

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
            log.info("Cadastrando coleta no ABIS - CPF: {}, ID: {}",
                    coleta.getCpf(), coleta.getIdColeta());

            EnrollResponse response = osiaClient.cadastrarEncounter(
                    coleta.getCpf(),
                    coleta.getIdColeta(),
                    converterParaBiometricDataOSIA(coleta)
            );

            log.info("Coleta cadastrada no ABIS com sucesso - Encounter ID: {}", response.getEncounterId());

            // ✅ ATUALIZAR ABIS ENCOUNTER ID NO OPENSEARCH
            coletaRepository.atualizarAbisEncounterId(coleta.getIdColeta(), response.getEncounterId());

            // ✅ AUDITAR CADASTRO NO ABIS
            auditService.registrarCadastroABIS(
                coleta.getCpf(),
                coleta.getIdColeta(),
                response.getEncounterId(),
                "ENROLL"
            );

            return response.getEncounterId();

        } catch (OSIAException e) {

            // RN010 - Tratar falha no ABIS
            if (e.getStatusCode() == 409) {
                log.error("Violação de unicidade no ABIS - CPF: {}",
                        coleta.getCpf());

                throw new ABISViolacaoUnicidadeException(e.getMessage(), e);
            }
            throw e;
        }
    }

    /**
     * Trata erros de comunicação com OSIA
     */
    private ResultadoDeduplicacao tratarErroOSIA(ColetaMetadata coleta, OSIAException e) {
        log.error("Erro na comunicação com OSIA - CPF: {}",
                coleta.getCpf(), e);

        if(e instanceof ABISViolacaoUnicidadeException){
            return ResultadoDeduplicacao.builder()
                    .status(StatusValidacao.INVALIDA)
                    .motivoRejeicao("Violação de unicidade no ABIS: " + e.getMessage())
                    .build();
        }

        // Erros recuperáveis (5xx) - marcar para reprocessamento
        if (e.isRetryable()) {
            return ResultadoDeduplicacao.builder()
                    .status(StatusValidacao.ERRO_REPROCESSAVEL)
                    .mensagem("Erro OSIA retentável: " + e.getMessage())
                    .build();
        }

        // Erros não recuperáveis (4xx) - marcar como inválido
        log.error("Erro OSIA não retentável - marcando como INVALIDA - CPF: {}",
                coleta.getCpf());

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
     * Valida CPF com dígitos verificadores
     */
    private boolean isValidCPF(String cpf) {
        return CpfValidator.isValid(cpf);
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
