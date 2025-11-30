package bio.prodesp.deduplicacao.client;

import bio.prodesp.deduplicacao.commons.model.dto.osia.*;
import bio.prodesp.deduplicacao.commons.exception.OSIAException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Cliente para comunicação com ABIS Thales via protocolo OSIA
 * Implementa circuit breaker e retry para resiliência
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OSIAClient {

    private final WebClient webClient;

    @Value("${osia.base-url}")
    private String osiaBaseUrl;

    @Value("${osia.timeout.seconds:15}")
    private int timeoutSeconds;

    /**
     * GET /v1/persons/{cpf}/encounters
     * Busca encounters existentes para um CPF
     */
    @CircuitBreaker(name = "osiaClient", fallbackMethod = "fallbackBuscarEncounters")
    @Retry(name = "osiaClient")
    public EncountersResponse buscarEncounters(String cpf) {
        log.debug("Buscando encounters para CPF: {}", cpf);

        try {
            return webClient.get()
                    .uri(osiaBaseUrl + "/v1/persons/{cpf}/encounters", cpf)
                    .header("X-Correlation-ID", MDC.get("correlationId"))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, this::handleClientError)
                    .onStatus(HttpStatusCode::is5xxServerError, this::handleServerError)
                    .bodyToMono(EncountersResponse.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();
        } catch (Exception e) {
            log.error("Erro ao buscar encounters para CPF: {}", cpf, e);
            throw new OSIAException("Erro ao buscar encounters: " + e.getMessage(), 500, true);
        }
    }

    /**
     * POST /v1/verify/ALL/{cpf}
     * Executa verificação 1:1 - compara biometria fornecida com encounters do CPF
     */
    @CircuitBreaker(name = "osiaClient", fallbackMethod = "fallbackVerificar11")
    @Retry(name = "osiaClient")
    public VerifyResponse verificar11(String cpf, BiometricDataOSIA biometricData) {
        log.debug("Executando verificação 1:1 para CPF: {}", cpf);

        try {
            return webClient.post()
                    .uri(osiaBaseUrl + "/v1/verify/ALL/{cpf}", cpf)
                    .header("X-Correlation-ID", MDC.get("correlationId"))
                    .bodyValue(biometricData)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, this::handleClientError)
                    .onStatus(HttpStatusCode::is5xxServerError, this::handleServerError)
                    .bodyToMono(VerifyResponse.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();
        } catch (Exception e) {
            log.error("Erro na verificação 1:1 para CPF: {}", cpf, e);
            throw new OSIAException("Erro na verificação 1:1: " + e.getMessage(), 500, true);
        }
    }

    /**
     * POST /v1/identify
     * Executa identificação 1:N - busca candidatos na base biométrica
     */
    @CircuitBreaker(name = "osiaClient", fallbackMethod = "fallbackIdentificar1N")
    @Retry(name = "osiaClient")
    public IdentifyResponse identificar1N(BiometricDataOSIA biometricData) {
        log.debug("Executando identificação 1:N");

        IdentifyRequest request = IdentifyRequest.builder()
                .biometricData(biometricData)
                .filters(IdentifyRequest.IdentifyFilters.builder()
                        .maxCandidates(10)
                        .threshold(70)
                        .build())
                .build();

        try {
            return webClient.post()
                    .uri(osiaBaseUrl + "/v1/identify")
                    .header("X-Correlation-ID", MDC.get("correlationId"))
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, this::handleClientError)
                    .onStatus(HttpStatusCode::is5xxServerError, this::handleServerError)
                    .bodyToMono(IdentifyResponse.class)
                    .timeout(Duration.ofSeconds(20))
                    .block();
        } catch (Exception e) {
            log.error("Erro na identificação 1:N", e);
            throw new OSIAException("Erro na identificação 1:N: " + e.getMessage(), 500, true);
        }
    }

    /**
     * POST /v1/persons/{cpf}/encounters/{idColetaLegado}
     * Cadastra novo encounter no ABIS
     */
    @CircuitBreaker(name = "osiaClient", fallbackMethod = "fallbackCadastrarEncounter")
    @Retry(name = "osiaClient")
    public EnrollResponse cadastrarEncounter(String cpf, String idColetaLegado, BiometricDataOSIA biometricData) {
        log.debug("Cadastrando encounter para CPF: {}, ID: {}", cpf, idColetaLegado);

        try {
            return webClient.post()
                    .uri(osiaBaseUrl + "/v1/persons/{cpf}/encounters/{id}", cpf, idColetaLegado)
                    .header("X-Correlation-ID", MDC.get("correlationId"))
                    .bodyValue(biometricData)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, this::handleClientError)
                    .onStatus(HttpStatusCode::is5xxServerError, this::handleServerError)
                    .bodyToMono(EnrollResponse.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();
        } catch (Exception e) {
            log.error("Erro ao cadastrar encounter para CPF: {}, ID: {}", cpf, idColetaLegado, e);
            throw new OSIAException("Erro ao cadastrar encounter: " + e.getMessage(), 500, true);
        }
    }

    /**
     * DELETE /v1/persons/{cpf}/encounters/{encounterId}
     * Remove encounter do ABIS
     */
    @CircuitBreaker(name = "osiaClient")
    @Retry(name = "osiaClient")
    public void deletarEncounter(String cpf, String encounterId) {
        log.warn("Deletando encounter: {} para CPF: {}", encounterId, cpf);

        try {
            webClient.delete()
                    .uri(osiaBaseUrl + "/v1/persons/{cpf}/encounters/{encounterId}", cpf, encounterId)
                    .header("X-Correlation-ID", MDC.get("correlationId"))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, this::handleClientError)
                    .onStatus(HttpStatusCode::is5xxServerError, this::handleServerError)
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(10))
                    .block();

            log.info("Encounter {} deletado com sucesso para CPF: {}", encounterId, cpf);
        } catch (Exception e) {
            log.error("Erro ao deletar encounter {} para CPF: {}", encounterId, cpf, e);
            throw new OSIAException("Erro ao deletar encounter: " + e.getMessage(), 500, true);
        }
    }

    /**
     * Health check do ABIS
     */
    public void healthCheck() {
        try {
            webClient.get()
                    .uri(osiaBaseUrl + "/health")
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(5))
                    .block();
        } catch (Exception e) {
            throw new OSIAException("ABIS unreachable", 503, true);
        }
    }

    // ========== Error Handlers ==========

    private Mono<Throwable> handleClientError(ClientResponse response) {
        return response.bodyToMono(String.class)
                .flatMap(body -> {
                    log.error("Erro 4xx do OSIA: status={}, body={}", response.statusCode(), body);
                    return Mono.error(new OSIAException(
                            "Client error: " + response.statusCode(),
                            response.statusCode().value(),
                            false // Não retentável
                    ));
                });
    }

    private Mono<Throwable> handleServerError(ClientResponse response) {
        return response.bodyToMono(String.class)
                .flatMap(body -> {
                    log.error("Erro 5xx do OSIA: status={}, body={}", response.statusCode(), body);
                    return Mono.error(new OSIAException(
                            "Server error: " + response.statusCode(),
                            response.statusCode().value(),
                            true // Retentável
                    ));
                });
    }

    // ========== Fallback Methods ==========

    private EncountersResponse fallbackBuscarEncounters(String cpf, Exception e) {
        log.error("Circuit breaker ativado para buscarEncounters - CPF: {}", cpf, e);
        throw new OSIAException("ABIS indisponível", 503, true);
    }

    private VerifyResponse fallbackVerificar11(String cpf, BiometricDataOSIA biometricData, Exception e) {
        log.error("Circuit breaker ativado para verificar11 - CPF: {}", cpf, e);
        throw new OSIAException("ABIS indisponível", 503, true);
    }

    private IdentifyResponse fallbackIdentificar1N(BiometricDataOSIA biometricData, Exception e) {
        log.error("Circuit breaker ativado para identificar1N", e);
        throw new OSIAException("ABIS indisponível", 503, true);
    }

    private EnrollResponse fallbackCadastrarEncounter(String cpf, String idColetaLegado, BiometricDataOSIA biometricData, Exception e) {
        log.error("Circuit breaker ativado para cadastrarEncounter - CPF: {}, ID: {}", cpf, idColetaLegado, e);
        throw new OSIAException("ABIS indisponível", 503, true);
    }
}
