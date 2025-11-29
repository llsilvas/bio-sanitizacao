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
     */
    private ResultadoDeduplicacao callDeduplicationService(ColetaRecord record) {
        try {
            String url = deduplicacaoServiceUrl + "/api/v1/deduplicacao/processar";

            log.debug("Partition {}: calling deduplication service for coleta {} - URL: {}",
                    partitionNumber,
                    record.getIdColeta(),
                    url);

            // Envia a coleta completa para o serviço
            ResultadoDeduplicacao resultado = restTemplate.postForObject(
                    url,
                    record.getColeta(),
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
}
