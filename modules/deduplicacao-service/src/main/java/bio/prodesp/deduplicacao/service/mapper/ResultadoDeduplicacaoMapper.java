package bio.prodesp.deduplicacao.service.mapper;

import bio.prodesp.deduplicacao.commons.model.dto.ResultadoDeduplicacao;
import bio.prodesp.deduplicacao.service.controller.dto.DeduplicacaoRequest;
import bio.prodesp.deduplicacao.service.controller.dto.DeduplicacaoResponse;
import org.mapstruct.*;

/**
 * Mapper MapStruct para conversão de ResultadoDeduplicacao (commons) para DeduplicacaoResponse (controller DTO)
 *
 * <p>Este mapper é usado pelo controller para transformar o resultado do serviço de deduplicação
 * em uma resposta HTTP adequada para o cliente (batch-processor ou outro consumidor da API).
 */
@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface ResultadoDeduplicacaoMapper {

    /**
     * Converte ResultadoDeduplicacao para DeduplicacaoResponse
     *
     * <p>Combina informações do resultado com o request original para enriquecer a response.
     *
     * @param resultado Resultado do processamento de deduplicação
     * @param request Request original (para copiar idColeta, CPF, sistemaOrigem)
     * @return Response completo para retornar via HTTP
     */
    @Mapping(target = "idColeta", source = "request.idColeta")
    @Mapping(target = "cpf", source = "request.cpf")
    @Mapping(target = "sistemaOrigem", source = "request.sistemaOrigem")
    @Mapping(target = "status", source = "resultado.status")
    @Mapping(target = "abisEncounterId", source = "resultado.abisEncounterId")
    @Mapping(target = "matchScore", source = "resultado.matchScore")
    @Mapping(target = "motivoInconclusivo", source = "resultado.motivoInconclusivo")
    @Mapping(target = "motivoRejeicao", source = "resultado.motivoRejeicao")
    @Mapping(target = "dataProcessamento", source = "resultado.dataProcessamento")
    @Mapping(target = "mensagem", source = "resultado.mensagem")
    @Mapping(target = "jaProcessado", source = "resultado.jaProcessado")
    @Mapping(target = "requerAnaliseManual", expression = "java(resultado.requerAnaliseManual())")
    DeduplicacaoResponse toResponse(ResultadoDeduplicacao resultado, DeduplicacaoRequest request);

    /**
     * Converte apenas ResultadoDeduplicacao (sem request)
     *
     * <p>Use este método quando não tiver acesso ao request original.
     * O idColeta, CPF e sistemaOrigem ficarão null na response.
     *
     * @param resultado Resultado do processamento
     * @return Response parcial
     */
    @Mapping(target = "status", source = "status")
    @Mapping(target = "abisEncounterId", source = "abisEncounterId")
    @Mapping(target = "matchScore", source = "matchScore")
    @Mapping(target = "motivoInconclusivo", source = "motivoInconclusivo")
    @Mapping(target = "motivoRejeicao", source = "motivoRejeicao")
    @Mapping(target = "dataProcessamento", source = "dataProcessamento")
    @Mapping(target = "mensagem", source = "mensagem")
    @Mapping(target = "jaProcessado", source = "jaProcessado")
    @Mapping(target = "requerAnaliseManual", expression = "java(resultado.requerAnaliseManual())")
    DeduplicacaoResponse toResponse(ResultadoDeduplicacao resultado);
}