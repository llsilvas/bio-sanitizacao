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
 * Cliente HTTP para comunicação com ABIS Thales via protocolo OSIA (Open Standard for Identity API).
 *
 * <p>Implementa as operações biométricas padrão OSIA para gerenciamento de encounters
 * e verificação/identificação biométrica. Todas as operações incluem:
 * <ul>
 *   <li><b>Circuit Breaker</b>: Proteção contra falhas em cascata (Resilience4j)</li>
 *   <li><b>Retry</b>: Tentativas automáticas com backoff exponencial</li>
 *   <li><b>Timeouts</b>: Limites configuráveis por operação</li>
 *   <li><b>Correlation ID</b>: Rastreamento de requisições via MDC</li>
 * </ul>
 *
 * <h2>Operações OSIA Suportadas:</h2>
 * <ul>
 *   <li>{@code GET /v1/persons/{cpf}/encounters} - Lista encounters existentes</li>
 *   <li>{@code POST /v1/verify/ALL/{cpf}} - Verificação biométrica 1:1</li>
 *   <li>{@code POST /v1/identify} - Identificação biométrica 1:N</li>
 *   <li>{@code POST /v1/persons/{cpf}/encounters/{id}} - Cadastro de encounter</li>
 *   <li>{@code DELETE /v1/persons/{cpf}/encounters/{id}} - Remoção de encounter</li>
 * </ul>
 *
 * <h2>Configuração de Resiliência:</h2>
 * <pre>
 * Circuit Breaker:
 *   - Janela deslizante: 10 chamadas
 *   - Threshold de falha: 50%
 *   - Half-open: 3 chamadas de teste
 *   - Tempo em aberto: 30s
 *
 * Retry:
 *   - Máximo de tentativas: 3
 *   - Backoff exponencial: 1s, 2s, 4s
 *   - Exceções retentáveis: WebClientRequestException, ConnectException, TimeoutException
 * </pre>
 *
 * @author Bio Sanitização Team
 * @version 1.0.0
 * @since 2025-01
 * @see <a href="https://osia-standard.org">OSIA Standard Specification</a>
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
     * Busca todos os encounters biométricos existentes para um CPF no ABIS.
     *
     * <p>Endpoint: {@code GET /v1/persons/{cpf}/encounters}
     *
     * <p><b>Comportamento:</b></p>
     * <ul>
     *   <li>Retorna lista de encounters (máximo 2 esperado conforme RN005)</li>
     *   <li>Se CPF não possui encounters, retorna lista vazia (não é erro)</li>
     *   <li>Inclui metadados de qualidade (NFIQ2) para cada encounter</li>
     * </ul>
     *
     * <p><b>Resiliência:</b> Circuit breaker + 3 tentativas com backoff exponencial
     *
     * @param cpf CPF do titular (11 dígitos sem formatação)
     * @return Response contendo lista de encounters e metadados
     * @throws OSIAException se falha na comunicação ou resposta inválida
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
     * Executa verificação biométrica 1:1 (RN004).
     *
     * <p>Endpoint: {@code POST /v1/verify/ALL/{cpf}}
     *
     * <p><b>Funcionamento:</b></p>
     * <ul>
     *   <li>Compara biometria fornecida com <b>todos os encounters</b> existentes do CPF</li>
     *   <li>Retorna match score (0-100) para melhor correspondência encontrada</li>
     *   <li>Considera threshold configurado no ABIS (tipicamente 70%)</li>
     *   <li>{@code isVerified()=true} se score >= threshold</li>
     *   <li>{@code pessoaEncontrada()=true} se CPF tem encounters, independente do score</li>
     * </ul>
     *
     * <p><b>Importante</b>: Operação "ALL" verifica contra todas as modalidades biométricas
     * (digitais, face, iris) disponíveis nos encounters.
     *
     * @param cpf CPF do titular para buscar encounters
     * @param biometricData Templates biométricos a serem verificados
     * @return Resultado da verificação com score e status
     * @throws OSIAException se falha na comunicação ou biometria inválida
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
     * Executa identificação biométrica 1:N contra toda a base ABIS (RN008).
     *
     * <p>Endpoint: {@code POST /v1/identify}
     *
     * <p><b>Funcionamento:</b></p>
     * <ul>
     *   <li>Compara biometria fornecida com <b>TODA a base</b> de encounters no ABIS</li>
     *   <li>Retorna até 10 candidatos ordenados por score (configurável)</li>
     *   <li>Threshold mínimo: 70% (matches abaixo são desconsiderados)</li>
     *   <li>Operação mais custosa - timeout estendido para 20s</li>
     * </ul>
     *
     * <p><b>Casos de Uso:</b></p>
     * <ul>
     *   <li>Quando verify 1:1 não encontra pessoa (CPF sem encounters)</li>
     *   <li>Detecção de possível duplicação de identidade</li>
     *   <li>Validação de unicidade biométrica antes de cadastrar</li>
     * </ul>
     *
     * <p><b>Performance</b>: Pode levar vários segundos em bases grandes.
     * Usar apenas quando necessário (RN008).
     *
     * @param biometricData Templates biométricos para buscar
     * @return Lista de candidatos com CPF e scores, ordenados do maior para menor
     * @throws OSIAException se falha na comunicação ou timeout
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
