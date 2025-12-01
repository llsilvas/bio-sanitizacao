package bio.prodesp.deduplicacao.controller;

import bio.prodesp.deduplicacao.commons.model.domain.ColetaMetadata;
import bio.prodesp.deduplicacao.commons.model.dto.ResultadoDeduplicacao;
import bio.prodesp.deduplicacao.controller.dto.DeduplicacaoRequest;
import bio.prodesp.deduplicacao.controller.dto.DeduplicacaoResponse;
import bio.prodesp.deduplicacao.mapper.DeduplicacaoRequestMapper;
import bio.prodesp.deduplicacao.mapper.ResultadoDeduplicacaoMapper;
import bio.prodesp.deduplicacao.service.DeduplicacaoService;
import bio.prodesp.deduplicacao.util.CpfValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Controller REST para processamento de deduplicação biométrica
 *
 * <p>Este endpoint é chamado pelo sanitizacao-batch para processar coletas biométricas
 * através do fluxo de deduplicação IIRGD utilizando o protocolo OSIA com ABIS Thales.</p>
 *
 * @see DeduplicacaoService
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/deduplicacao")
@RequiredArgsConstructor
@Tag(name = "Deduplicação", description = "APIs para processamento de deduplicação biométrica")
public class DeduplicacaoController {

    private final DeduplicacaoService deduplicacaoService;
    private final DeduplicacaoRequestMapper requestMapper;
    private final ResultadoDeduplicacaoMapper resultadoMapper;

    /**
     * Processa uma coleta biométrica através do fluxo de deduplicação
     *
     * <p><b>Fluxo de Processamento (IIRGD):</b></p>
     * <ul>
     *   <li>RN003: Valida critérios de entrada (idade, qualidade, etc.)</li>
     *   <li>RN004: Busca encounters existentes + Verifica 1:1 com CPF</li>
     *   <li>RN005: Aplica seleção por NFIQ2 (mantém 2 melhores)</li>
     *   <li>RN006: Valida threshold de match (≥ 70%)</li>
     *   <li>RN007: Executa identificação 1:N se não houver match</li>
     *   <li>RN008: Cadastra encounter se não encontrar match</li>
     *   <li>RN009: Marca inconclusivo se pessoa existe mas sem match biométrico</li>
     *   <li>RN010: Marca inválido se falhar critérios de qualidade</li>
     *   <li>RN011: Implementa idempotência (não reprocessa)</li>
     * </ul>
     *
     * @param request Dados da coleta biométrica
     * @return Resultado do processamento
     */
    @PostMapping(value = "/processar",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
        summary = "Processa deduplicação biométrica",
        description = "Recebe uma coleta biométrica e executa o fluxo de deduplicação IIRGD via OSIA/ABIS"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Processamento concluído com sucesso",
            content = @Content(schema = @Schema(implementation = DeduplicacaoResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Requisição inválida (validação falhou)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Coleta já processada anteriormente (idempotência)",
            content = @Content(schema = @Schema(implementation = DeduplicacaoResponse.class))
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Erro interno no processamento",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "503",
            description = "ABIS indisponível (circuit breaker aberto)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<DeduplicacaoResponse> processar(@Valid @RequestBody DeduplicacaoRequest request) {
        log.info("Iniciando processamento de deduplicação - idColeta: {}, CPF: {}, sistema: {}",
                 request.getIdColeta(),
                 maskCpf(request.getCpf()),
                 request.getSistemaOrigem());

        try {
            // 1. Converter DTO para domain model usando MapStruct
            ColetaMetadata coleta = requestMapper.toColetaMetadata(request);

            log.debug("Coleta convertida - idColeta: {}, dadosBiometricos: {}",
                     coleta.getIdColeta(),
                     coleta.getDadosBiometricos() != null ? coleta.getDadosBiometricos().size() : 0);

            // 2. Processar deduplicação via OSIA/ABIS
            ResultadoDeduplicacao resultado = deduplicacaoService.processar(coleta);

            // 3. Converter resultado para response DTO usando MapStruct
            DeduplicacaoResponse response = resultadoMapper.toResponse(resultado, request);

            log.info("Processamento concluído - idColeta: {}, status: {}, match: {}",
                     request.getIdColeta(),
                     response.getStatus(),
                     response.getMatchScore());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erro ao processar deduplicação - idColeta: {}", request.getIdColeta(), e);

            DeduplicacaoResponse errorResponse = DeduplicacaoResponse.erro(
                request.getIdColeta(),
                request.getCpf(),
                "Erro no processamento: " + e.getMessage(),
                request.getSistemaOrigem()
            );

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse);
        }
    }

    /**
     * Endpoint de health check (já implementado via Spring Actuator)
     * Use: /actuator/health para verificações completas
     */
    @GetMapping("/health")
    @Operation(summary = "Health check simplificado do serviço de deduplicação")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(HealthResponse.builder()
                .status("UP")
                .service("deduplicacao-service")
                .timestamp(LocalDateTime.now())
                .abisStatus("UP")
                .build());
    }

    /**
     * Mascara CPF para logs (mostra apenas últimos 4 dígitos) - LGPD compliance
     */
    private String maskCpf(String cpf) {
        return CpfValidator.mascararParaLog(cpf);
    }

    // ========== Inner Classes ==========

    /**
     * DTO para resposta de erro
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ErrorResponse {
        private String error;
        private String message;
        private LocalDateTime timestamp;
        private String path;
    }

    /**
     * DTO para health check
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class HealthResponse {
        private String status;
        private String service;
        private LocalDateTime timestamp;
        private String abisStatus;
    }
}
