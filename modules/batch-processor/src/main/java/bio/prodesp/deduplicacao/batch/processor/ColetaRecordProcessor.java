package bio.prodesp.deduplicacao.batch.processor;

import bio.prodesp.deduplicacao.commons.model.dto.ColetaRecord;
import bio.prodesp.deduplicacao.commons.model.dto.ResultadoDeduplicacao;
import bio.prodesp.deduplicacao.commons.model.enums.StatusValidacao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Processor para processar e deduplicar coletas biométricas
 * Chama o serviço de deduplicação para validar cada coleta
 */
@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class ColetaRecordProcessor implements ItemProcessor<ColetaRecord, ColetaRecord> {

    private final RestTemplate restTemplate;

    @Value("${deduplicacao.service.url:http://localhost:8080}")
    private String deduplicacaoServiceUrl;

    @Value("#{stepExecutionContext['partitionNumber']}")
    private Integer partitionNumber;

    @Override
    public ColetaRecord process(ColetaRecord record) throws Exception {
        log.debug("Partition {}: processing coleta ID {} (CPF: {})",
                partitionNumber, record.getIdColeta(), record.getCpf());

        try {
            // Chama o serviço de deduplicação para validar a coleta
            ResultadoDeduplicacao resultado = callDeduplicationService(record);

            if (resultado != null) {
                // Atualiza o record com os resultados do processamento
                record.setStatusValidacao(resultado.getStatus());
                record.setAbisEncounterId(resultado.getAbisEncounterId());
                record.setMatchScore(resultado.getMatchScore());
                record.setMotivoInconclusivo(resultado.getMotivoInconclusivo());
                record.setMotivoRejeicao(resultado.getMotivoRejeicao());
                record.setProcessado(true);

                log.info("Partition {}: coleta {} (CPF: {}) processed with status {}",
                        partitionNumber,
                        record.getIdColeta(),
                        record.getCpf(),
                        resultado.getStatus());

                if (resultado.getStatus() == StatusValidacao.VALIDA) {
                    log.debug("Partition {}: coleta {} validated successfully - ABIS ID: {}, Score: {}",
                            partitionNumber,
                            record.getIdColeta(),
                            resultado.getAbisEncounterId(),
                            resultado.getMatchScore());
                } else if (resultado.getStatus() == StatusValidacao.INCONCLUSIVA) {
                    log.warn("Partition {}: coleta {} marked as inconclusive - Motivo: {}",
                            partitionNumber,
                            record.getIdColeta(),
                            resultado.getMotivoInconclusivo());
                } else if (resultado.getStatus() == StatusValidacao.INVALIDA) {
                    log.warn("Partition {}: coleta {} marked as invalid - Motivo: {}",
                            partitionNumber,
                            record.getIdColeta(),
                            resultado.getMotivoRejeicao());
                }
            } else {
                // Se o serviço não retornou resultado, marca como erro reprocessável
                record.setStatusValidacao(StatusValidacao.ERRO_REPROCESSAVEL);
                record.setProcessado(false);
                record.setMensagemErro("Serviço de deduplicação não retornou resultado");

                log.warn("Partition {}: coleta {} could not be processed - no result from service",
                        partitionNumber,
                        record.getIdColeta());
            }

            return record;

        } catch (Exception e) {
            log.error("Partition {}: error processing coleta {} (CPF: {}): {}",
                    partitionNumber,
                    record.getIdColeta(),
                    record.getCpf(),
                    e.getMessage(), e);

            // Marca como erro reprocessável
            record.setStatusValidacao(StatusValidacao.ERRO_REPROCESSAVEL);
            record.setProcessado(false);
            record.setMensagemErro("Erro ao processar: " + e.getMessage());

            // Não propaga a exceção para permitir que o Spring Batch continue
            // O registro será marcado como erro e poderá ser reprocessado
            return record;
        }
    }

    /**
     * Chama o serviço de deduplicação via REST
     * Endpoint: POST /api/v1/deduplicacao/processar
     *
     * <p>IMPORTANTE: O endpoint espera DeduplicacaoRequest (DTO), não ColetaMetadata (domain).
     * Como o batch-processor não tem dependência do deduplicacao-service, fazemos a conversão manualmente.
     */
    private ResultadoDeduplicacao callDeduplicationService(ColetaRecord record) {
        try {
            String url = deduplicacaoServiceUrl + "/api/v1/deduplicacao/processar";

            log.debug("Partition {}: calling deduplication service for coleta {} - URL: {}",
                    partitionNumber,
                    record.getIdColeta(),
                    url);

            // Converter ColetaMetadata para DeduplicacaoRequest (DTO esperado pelo endpoint)
            Object request = convertToDeduplicacaoRequest(record.getColeta());

            // Envia o DTO correto para o serviço
            ResultadoDeduplicacao resultado = restTemplate.postForObject(
                    url,
                    request,
                    ResultadoDeduplicacao.class
            );

            return resultado;

        } catch (Exception e) {
            log.error("Partition {}: failed to call deduplication service for coleta {} (CPF: {}): {}",
                    partitionNumber,
                    record.getIdColeta(),
                    record.getCpf(),
                    e.getMessage());
            return null;
        }
    }

    /**
     * Converte ColetaMetadata para DeduplicacaoRequest (estrutura esperada pelo controller)
     *
     * <p>Esta conversão manual é necessária porque batch-processor não depende de deduplicacao-service.
     * Em produção, considere extrair os DTOs para um módulo compartilhado ou usar biblioteca de conversão.
     *
     * @param coleta Domain model do commons
     * @return Map simulando DeduplicacaoRequest
     */
    private Object convertToDeduplicacaoRequest(bio.prodesp.deduplicacao.commons.model.domain.ColetaMetadata coleta) {
        // Usa Map para evitar dependência circular entre módulos
        java.util.Map<String, Object> request = new java.util.HashMap<>();
        request.put("idColeta", coleta.getIdColeta());
        request.put("cpf", coleta.getCpf());
        request.put("dataNascimento", coleta.getDataNascimento());
        request.put("sistemaOrigem", "IIRGD");

        // Converter dados biométricos
        if (coleta.getDadosBiometricos() != null && !coleta.getDadosBiometricos().isEmpty()) {
            java.util.List<java.util.Map<String, Object>> dadosBiometricosDTO = new java.util.ArrayList<>();

            for (bio.prodesp.deduplicacao.commons.model.domain.DadoBiometricoMetadata dado : coleta.getDadosBiometricos()) {
                java.util.Map<String, Object> dadoDTO = new java.util.HashMap<>();
                dadoDTO.put("tipo", dado.getTipo());

                // Converter posicaoDedo (Integer) para posicao (String descritiva)
                if (dado.getPosicaoDedo() != null) {
                    dadoDTO.put("posicao", convertPosicaoDedoToString(dado.getPosicaoDedo()));
                }

                // Extrair primeiro template se existir
                if (dado.getTemplates() != null && !dado.getTemplates().isEmpty()) {
                    bio.prodesp.deduplicacao.commons.model.domain.TemplateMetadata templateMeta =
                        dado.getTemplates().get(0);

                    // Codifica byte array para Base64 string
                    if (templateMeta.getDados() != null) {
                        String templateBase64 = java.util.Base64.getEncoder()
                            .encodeToString(templateMeta.getDados());
                        dadoDTO.put("template", templateBase64);
                    }

                    dadoDTO.put("formato", templateMeta.getFormato());
                }

                dadoDTO.put("nfiq2Score", dado.getQualidadeNfiq());
                dadosBiometricosDTO.add(dadoDTO);
            }

            request.put("dadosBiometricos", dadosBiometricosDTO);
        }

        return request;
    }

    /**
     * Converte código de posição do dedo (Integer) para String descritiva
     * Sincronizado com DeduplicacaoRequestMapper do deduplicacao-service
     */
    private String convertPosicaoDedoToString(Integer posicaoDedo) {
        if (posicaoDedo == null) {
            return null;
        }
        return switch (posicaoDedo) {
            case 1 -> "POLEGAR_DIREITO";
            case 2 -> "INDICADOR_DIREITO";
            case 3 -> "MEDIO_DIREITO";
            case 4 -> "ANELAR_DIREITO";
            case 5 -> "MINIMO_DIREITO";
            case 6 -> "POLEGAR_ESQUERDO";
            case 7 -> "INDICADOR_ESQUERDO";
            case 8 -> "MEDIO_ESQUERDO";
            case 9 -> "ANELAR_ESQUERDO";
            case 10 -> "MINIMO_ESQUERDO";
            default -> "DESCONHECIDO";
        };
    }
}
