package bio.prodesp.deduplicacao.mapper;

import bio.prodesp.deduplicacao.commons.model.domain.ColetaMetadata;
import bio.prodesp.deduplicacao.commons.model.domain.DadoBiometricoMetadata;
import bio.prodesp.deduplicacao.controller.dto.DeduplicacaoRequest;
import org.mapstruct.*;

import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper MapStruct para conversão entre DeduplicacaoRequest (DTO) e ColetaMetadata (Domain)
 *
 * <p>IMPORTANTE: A ordem dos annotation processors no pom.xml é crítica:
 * 1. MapStruct processor
 * 2. Lombok
 * 3. Lombok-MapStruct binding
 */
@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface DeduplicacaoRequestMapper {

    /**
     * Converte DeduplicacaoRequest (DTO do controller) para ColetaMetadata (domain model)
     *
     * @param request DTO recebido do batch ou controller
     * @return ColetaMetadata para processamento pelo service
     */
    @Mapping(target = "idColeta", source = "idColeta")
    @Mapping(target = "cpf", source = "cpf")
    @Mapping(target = "dataNascimento", source = "dataNascimento")
    @Mapping(target = "dadosBiometricos", source = "dadosBiometricos", qualifiedByName = "mapDadosBiometricos")
    @Mapping(target = "validacaoPendente", constant = "false")
    @Mapping(target = "valida", ignore = true)  // Será preenchido após processamento
    ColetaMetadata toColetaMetadata(DeduplicacaoRequest request);

    /**
     * Converte ColetaMetadata (domain) para DeduplicacaoRequest (DTO)
     *
     * @param coleta Domain model
     * @return DTO para envio via REST
     */
    @Mapping(target = "idColeta", source = "idColeta")
    @Mapping(target = "cpf", source = "cpf")
    @Mapping(target = "dataNascimento", source = "dataNascimento")
    @Mapping(target = "sistemaOrigem", constant = "IIRGD")  // Default
    @Mapping(target = "dadosBiometricos", source = "dadosBiometricos", qualifiedByName = "mapDadosBiometricosDTO")
    DeduplicacaoRequest toDeduplicacaoRequest(ColetaMetadata coleta);

    /**
     * Mapeia lista de dados biométricos do DTO para domain
     *
     * <p>Mapeamento:
     * - DTO.nfiq2Score → Domain.qualidadeNfiq
     * - DTO.posicao (String) → Domain.posicaoDedo (Integer) - conversão manual necessária
     * - DTO.template → Domain.templates (List<TemplateMetadata>) - conversão complexa
     */
    @Named("mapDadosBiometricos")
    default Collection<DadoBiometricoMetadata> mapDadosBiometricos(
            List<DeduplicacaoRequest.DadoBiometricoDTO> dadosDTO) {

        if (dadosDTO == null) {
            return null;
        }

        return dadosDTO.stream()
                .<DadoBiometricoMetadata>map(dto -> {
                    // Criar template list se houver template
                    List<bio.prodesp.deduplicacao.commons.model.domain.TemplateMetadata> templates = null;
                    if (dto.getTemplate() != null) {
                        // Decodifica Base64 string para byte array
                        byte[] templateBytes = Base64.getDecoder().decode(dto.getTemplate());
                        templates = List.of(
                            bio.prodesp.deduplicacao.commons.model.domain.TemplateMetadata.builder()
                                .dados(templateBytes)
                                .formato(dto.getFormato() != null ? dto.getFormato() : "ISO_19794_2")
                                .build()
                        );
                    }

                    return DadoBiometricoMetadata.builder()
                            .tipo(dto.getTipo())
                            .posicaoDedo(convertPosicaoToInteger(dto.getPosicao()))
                            .qualidadeNfiq(dto.getNfiq2Score())
                            .templates(templates)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Mapeia collection de dados biométricos do domain para DTO
     */
    @Named("mapDadosBiometricosDTO")
    default List<DeduplicacaoRequest.DadoBiometricoDTO> mapDadosBiometricosDTO(
            Collection<DadoBiometricoMetadata> dadosDomain) {

        if (dadosDomain == null) {
            return null;
        }

        return dadosDomain.stream()
                .<DeduplicacaoRequest.DadoBiometricoDTO>map(domain -> {
                    // Extrair primeiro template se existir
                    String template = null;
                    String formato = null;
                    if (domain.getTemplates() != null && !domain.getTemplates().isEmpty()) {
                        bio.prodesp.deduplicacao.commons.model.domain.TemplateMetadata templateMeta =
                            domain.getTemplates().get(0);
                        // Codifica byte array para Base64 string
                        if (templateMeta.getDados() != null) {
                            template = Base64.getEncoder().encodeToString(templateMeta.getDados());
                        }
                        formato = templateMeta.getFormato();
                    }

                    return DeduplicacaoRequest.DadoBiometricoDTO.builder()
                            .tipo(domain.getTipo())
                            .posicao(convertPosicaoToString(domain.getPosicaoDedo()))
                            .template(template)
                            .nfiq2Score(domain.getQualidadeNfiq())
                            .formato(formato)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Converte posição de String (ex: "POLEGAR_DIREITO") para Integer (código da posição)
     * TODO: Implementar mapeamento real baseado na tabela de códigos
     */
    default Integer convertPosicaoToInteger(String posicao) {
        if (posicao == null) {
            return null;
        }
        // Mapeamento simplificado - ajustar conforme tabela real
        return switch (posicao.toUpperCase()) {
            case "POLEGAR_DIREITO" -> 1;
            case "INDICADOR_DIREITO" -> 2;
            case "MEDIO_DIREITO" -> 3;
            case "ANELAR_DIREITO" -> 4;
            case "MINIMO_DIREITO" -> 5;
            case "POLEGAR_ESQUERDO" -> 6;
            case "INDICADOR_ESQUERDO" -> 7;
            case "MEDIO_ESQUERDO" -> 8;
            case "ANELAR_ESQUERDO" -> 9;
            case "MINIMO_ESQUERDO" -> 10;
            default -> 0;
        };
    }

    /**
     * Converte posição de Integer (código) para String descritiva
     */
    default String convertPosicaoToString(Integer posicaoDedo) {
        if (posicaoDedo == null) {
            return null;
        }
        // Mapeamento reverso
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