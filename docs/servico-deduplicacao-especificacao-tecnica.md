# Especificação Técnica - Serviço de Deduplicação Biométrica

## 1. Visão Geral

### 1.1 Responsabilidades do Serviço

O **Serviço de Deduplicação** é responsável por:

1. **Validar critérios de entrada** das coletas biométricas
2. **Orquestrar chamadas OSIA** para o ABIS Thales
3. **Aplicar regras de negócio** específicas por origem (IIRGD/DETRAN)
4. **Gerenciar estados** das coletas (VALIDA, INCONCLUSIVA, INVALIDA)
5. **Garantir idempotência** no processamento

### 1.2 Tecnologias

- **Framework**: Spring Boot 3.5.x
- **Java**: 21
- **Cliente HTTP**: WebClient (Spring WebFlux) para OSIA
- **Resiliência**: Resilience4j (Circuit Breaker, Retry, Rate Limiter)
- **Cache**: Redis para configurações e controle de idempotência
- **Observabilidade**: Micrometer + Prometheus

---

## 2. Mapeamento de Endpoints OSIA

### 2.1 Endpoints Utilizados

| Operação | Método | Endpoint OSIA | Uso no Fluxo |
|----------|--------|---------------|--------------|
| **Buscar Encounters** | GET | `/v1/persons/{cpf}/encounters` | Verificar coletas existentes |
| **Verificação 1:1** | POST | `/v1/verify/ALL/{cpf}` | Confirmar match biométrico |
| **Identificação 1:N** | POST | `/v1/identify` | Buscar candidatos na base |
| **Criar Encounter** | POST | `/v1/persons/{cpf}/encounters/{idColetaLegado}` | Cadastrar nova coleta |
| **Atualizar Encounter** | PUT | `/v1/persons/{cpf}/encounters/{encounterId}` | Atualizar coleta existente |
| **Deletar Encounter** | DELETE | `/v1/persons/{cpf}/encounters/{encounterId}` | Remover coleta inválida |

### 2.2 Estrutura de Requisição/Resposta

#### GET /v1/persons/{cpf}/encounters

**Response 200:**
```json
{
  "encounters": [
    {
      "encounterId": "enc-123456",
      "biometricData": {
        "fingerprints": [...],
        "face": {...}
      },
      "metadata": {
        "idColetaLegado": "COL-789",
        "origem": "IIRGD",
        "nfiq2Score": 85,
        "dataColeta": "2024-10-15T10:30:00Z"
      }
    }
  ],
  "totalCount": 1
}
```

**Response 404:**
```json
{
  "error": "PERSON_NOT_FOUND",
  "message": "No person found for CPF: 12345678900"
}
```

---

#### POST /v1/verify/ALL/{cpf}

**Request Body:**
```json
{
  "biometricData": {
    "fingerprints": [
      {
        "position": "RIGHT_THUMB",
        "image": "base64_encoded_image",
        "format": "WSQ"
      }
    ],
    "face": {
      "image": "base64_encoded_image",
      "format": "JPEG"
    }
  }
}
```

**Response 200 - Match:**
```json
{
  "verified": true,
  "score": 92.5,
  "encounterId": "enc-123456"
}
```

**Response 200 - No Match:**
```json
{
  "verified": false,
  "score": 45.3,
  "encounterId": null
}
```

---

#### POST /v1/identify

**Request Body:**
```json
{
  "biometricData": {
    "fingerprints": [...],
    "face": {...}
  },
  "filters": {
    "maxCandidates": 10,
    "threshold": 70
  }
}
```

**Response 200:**
```json
{
  "candidates": [
    {
      "personId": "12345678900",
      "encounterId": "enc-456789",
      "score": 88.5
    },
    {
      "personId": "98765432100",
      "encounterId": "enc-789012",
      "score": 75.2
    }
  ],
  "totalMatches": 2
}
```

---

#### POST /v1/persons/{cpf}/encounters/{idColetaLegado}

**Request Body:**
```json
{
  "biometricData": {
    "fingerprints": [...],
    "face": {...}
  },
  "metadata": {
    "origem": "IIRGD",
    "nfiq2Score": 90,
    "dataColeta": "2024-10-15T10:30:00Z",
    "operador": "USR-123"
  }
}
```

**Response 201:**
```json
{
  "encounterId": "enc-987654",
  "personId": "12345678900",
  "status": "ENROLLED"
}
```

**Response 409 - Violação de Unicidade:**
```json
{
  "error": "UNIQUENESS_VIOLATION",
  "message": "Biometric data already enrolled for different person",
  "conflictingPersonId": "98765432100"
}
```

---

## 3. Fluxo IIRGD - Implementação Detalhada

### 3.1 Diagrama de Sequência

```
Client          DeduplicacaoService         OSIAClient          Database          ABISService
  |                      |                       |                   |                   |
  |--processar(coleta)-->|                       |                   |                   |
  |                      |                       |                   |                   |
  |                      |--validarCriterios-----|                   |                   |
  |                      |<------------------OK--|                   |                   |
  |                      |                       |                   |                   |
  |                      |--GET /encounters/{cpf}---------------->   |                   |
  |                      |<----------------200 OK-------------------|                   |
  |                      |                       |                   |                   |
  |                      |--POST /verify/ALL/{cpf}---------------->  |                   |
  |                      |<----------------200 {verified:true}------|                   |
  |                      |                       |                   |                   |
  |                      |--aplicarRegrasNegocio-|                   |                   |
  |                      |                       |                   |                   |
  |                      |--POST /encounters------------------------------->            |
  |                      |<----------------201 Created--------------------------|        |
  |                      |                       |                   |                   |
  |                      |--atualizarStatus(VALIDA)----------------->|                   |
  |                      |<------------------OK---------------------|                   |
  |<--Response(VALIDA)---|                       |                   |                   |
```

### 3.2 Código Java - Serviço Principal

```java
@Service
@Slf4j
public class DeduplicacaoIIRGDService {

    private final OSIAClient osiaClient;
    private final ColetaRepository coletaRepository;
    private final AuditService auditService;
    private final IdempotenciaService idempotenciaService;
    
    @Transactional
    public ResultadoDeduplicacao processar(Coleta coleta) {
        log.info("Iniciando processamento IIRGD para CPF: {}, ID: {}", 
                 coleta.getCpf(), coleta.getIdColetaLegado());
        
        // RN011 - Verificar idempotência
        if (idempotenciaService.jaProcessado(coleta.getCpf(), coleta.getIdColetaLegado())) {
            log.warn("Coleta já processada anteriormente");
            return ResultadoDeduplicacao.jaProcessado();
        }
        
        try {
            // RN003 - Validar critérios de entrada
            validarCriteriosEntrada(coleta);
            
            // RN004 - Buscar encounters existentes
            EncountersResponse encounters = osiaClient.buscarEncounters(coleta.getCpf());
            
            // RN004 - Verificação 1:1
            VerifyResponse verifyResult = osiaClient.verificar11(coleta.getCpf(), coleta.getBiometriaData());
            
            // Aplicar regras de negócio baseado no resultado
            return aplicarRegrasIIRGD(coleta, encounters, verifyResult);
            
        } catch (OSIAException e) {
            return tratarErroOSIA(coleta, e);
        } catch (Exception e) {
            log.error("Erro inesperado no processamento", e);
            return ResultadoDeduplicacao.erro(StatusValidacao.ERRO_REPROCESSAVEL, e.getMessage());
        }
    }
    
    private void validarCriteriosEntrada(Coleta coleta) {
        // RN003 - validacao_pendente = false
        if (coleta.isValidacaoPendente()) {
            throw new CriterioEntradaException("Coleta possui validação pendente");
        }
        
        // RN003 - CPF válido
        if (!CPFValidator.isValid(coleta.getCpf())) {
            throw new CriterioEntradaException("CPF inválido");
        }
        
        // RN003 - Idade > 15 anos
        int idade = calcularIdade(coleta.getDataNascimento());
        if (idade <= 15) {
            throw new CriterioEntradaException("Idade deve ser maior que 15 anos");
        }
    }
    
    private ResultadoDeduplicacao aplicarRegrasIIRGD(
            Coleta coleta, 
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
    
    private ResultadoDeduplicacao processarMatchEncontrado(
            Coleta coleta, 
            EncountersResponse encounters, 
            VerifyResponse verifyResult) {
        
        int totalEncounters = encounters.getTotalCount();
        
        // RN004 - Apenas 1 encounter
        if (totalEncounters == 1) {
            cadastrarNoABIS(coleta);
            atualizarStatus(coleta, StatusValidacao.VALIDA);
            return ResultadoDeduplicacao.sucesso(StatusValidacao.VALIDA);
        }
        
        // RN004 - 2 ou mais encounters - aplicar RN005
        if (totalEncounters >= 2) {
            return aplicarSelecaoPorNFIQ2(coleta, encounters);
        }
        
        return ResultadoDeduplicacao.erro(StatusValidacao.ERRO_REPROCESSAVEL, 
                                          "Estado inconsistente: verify OK mas 0 encounters");
    }
    
    private ResultadoDeduplicacao aplicarSelecaoPorNFIQ2(Coleta coleta, EncountersResponse encounters) {
        // RN005 - Manter as 2 melhores por NFIQ2
        List<Encounter> ordenados = encounters.getEncounters().stream()
                .sorted(Comparator.comparing(e -> e.getMetadata().getNfiq2Score(), 
                                             Comparator.reverseOrder()))
                .collect(Collectors.toList());
        
        Encounter melhor = ordenados.get(0);
        Encounter segundoMelhor = ordenados.size() > 1 ? ordenados.get(1) : null;
        
        // Se a coleta atual é melhor que uma das 2 existentes
        if (coleta.getNfiq2Score() > melhor.getMetadata().getNfiq2Score()) {
            // Substitui a pior das 2
            if (segundoMelhor != null) {
                osiaClient.deletarEncounter(coleta.getCpf(), segundoMelhor.getEncounterId());
            }
            cadastrarNoABIS(coleta);
            atualizarStatus(coleta, StatusValidacao.VALIDA);
            return ResultadoDeduplicacao.sucesso(StatusValidacao.VALIDA);
        }
        
        if (segundoMelhor != null && coleta.getNfiq2Score() > segundoMelhor.getMetadata().getNfiq2Score()) {
            osiaClient.deletarEncounter(coleta.getCpf(), segundoMelhor.getEncounterId());
            cadastrarNoABIS(coleta);
            atualizarStatus(coleta, StatusValidacao.VALIDA);
            return ResultadoDeduplicacao.sucesso(StatusValidacao.VALIDA);
        }
        
        // Coleta atual tem qualidade inferior às 2 existentes
        atualizarStatus(coleta, StatusValidacao.VALIDA);
        auditService.registrar("Coleta descartada por NFIQ2 inferior", coleta);
        return ResultadoDeduplicacao.sucesso(StatusValidacao.VALIDA, "Descartada por qualidade");
    }
    
    private ResultadoDeduplicacao processarBusca1N(Coleta coleta) {
        // RN004 - Executar identificação 1:N
        IdentifyResponse identifyResult = osiaClient.identificar1N(coleta.getBiometriaData());
        
        int matches = identifyResult.getCandidates().size();
        
        // RN008 - Nenhum match - coleta válida
        if (matches == 0) {
            cadastrarNoABIS(coleta);
            atualizarStatus(coleta, StatusValidacao.VALIDA);
            return ResultadoDeduplicacao.sucesso(StatusValidacao.VALIDA);
        }
        
        // 1 ou mais matches - inconclusivo
        String motivo = matches == 1 
            ? "Match único em busca 1:N" 
            : String.format("Múltiplos matches em 1:N (%d candidatos)", matches);
        
        return marcarInconclusivo(coleta, motivo);
    }
    
    private ResultadoDeduplicacao marcarInconclusivo(Coleta coleta, String motivo) {
        // RN006 - Marcar como inconclusivo
        coleta.setStatusValidacao(StatusValidacao.INCONCLUSIVA);
        coleta.setValidacaoPendente(true);
        coleta.setMotivoInconclusivo(motivo);
        coletaRepository.save(coleta);
        
        auditService.registrar("Coleta marcada como INCONCLUSIVA: " + motivo, coleta);
        
        return ResultadoDeduplicacao.inconclusivo(motivo);
    }
    
    private void cadastrarNoABIS(Coleta coleta) {
        try {
            // RN007 - Cadastrar no ABIS
            EnrollResponse response = osiaClient.cadastrarEncounter(
                coleta.getCpf(), 
                coleta.getIdColetaLegado(), 
                coleta.toBiometricData()
            );
            
            // RN011 - Registrar ID do ABIS para controle
            coleta.setAbisRecordId(response.getEncounterId());
            coletaRepository.save(coleta);
            
            auditService.registrarCadastroABIS(coleta, response);
            
        } catch (OSIAException e) {
            // RN010 - Tratar falha no ABIS
            if (e.getStatusCode() == 409) {
                coleta.setStatusValidacao(StatusValidacao.INVALIDA);
                coleta.setMotivoRejeicao("Violação de unicidade no ABIS: " + e.getMessage());
                coletaRepository.save(coleta);
                
                auditService.registrarErro("Violação de unicidade", coleta, e);
                throw new ABISViolacaoUnicidadeException(e.getMessage(), e);
            }
            throw e;
        }
    }
    
    private void atualizarStatus(Coleta coleta, StatusValidacao status) {
        coleta.setStatusValidacao(status);
        coleta.setValidacaoPendente(false);
        coleta.setDataProcessamento(LocalDateTime.now());
        coletaRepository.save(coleta);
    }
    
    private ResultadoDeduplicacao tratarErroOSIA(Coleta coleta, OSIAException e) {
        log.error("Erro na comunicação com OSIA", e);
        
        // Erros recuperáveis
        if (e.isRetryable()) {
            return ResultadoDeduplicacao.erro(StatusValidacao.ERRO_REPROCESSAVEL, e.getMessage());
        }
        
        // Erros não recuperáveis
        coleta.setStatusValidacao(StatusValidacao.INVALIDA);
        coleta.setMotivoRejeicao("Erro OSIA: " + e.getMessage());
        coletaRepository.save(coleta);
        
        return ResultadoDeduplicacao.erro(StatusValidacao.INVALIDA, e.getMessage());
    }
    
    private int calcularIdade(LocalDate dataNascimento) {
        return Period.between(dataNascimento, LocalDate.now()).getYears();
    }
}
```

### 3.3 Cliente OSIA com Resiliência

```java
@Service
@Slf4j
public class OSIAClient {

    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    
    @Value("${osia.base-url}")
    private String osiaBaseUrl;
    
    public EncountersResponse buscarEncounters(String cpf) {
        return circuitBreaker.executeSupplier(() -> 
            retry.executeSupplier(() -> {
                log.debug("Buscando encounters para CPF: {}", cpf);
                
                return webClient.get()
                    .uri(osiaBaseUrl + "/v1/persons/{cpf}/encounters", cpf)
                    .header("X-Correlation-ID", MDC.get("correlationId"))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, this::handleClientError)
                    .onStatus(HttpStatusCode::is5xxServerError, this::handleServerError)
                    .bodyToMono(EncountersResponse.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            })
        );
    }
    
    public VerifyResponse verificar11(String cpf, BiometricData biometricData) {
        return circuitBreaker.executeSupplier(() ->
            retry.executeSupplier(() -> {
                log.debug("Executando verificação 1:1 para CPF: {}", cpf);
                
                return webClient.post()
                    .uri(osiaBaseUrl + "/v1/verify/ALL/{cpf}", cpf)
                    .header("X-Correlation-ID", MDC.get("correlationId"))
                    .bodyValue(biometricData)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, this::handleClientError)
                    .onStatus(HttpStatusCode::is5xxServerError, this::handleServerError)
                    .bodyToMono(VerifyResponse.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();
            })
        );
    }
    
    public IdentifyResponse identificar1N(BiometricData biometricData) {
        return circuitBreaker.executeSupplier(() ->
            retry.executeSupplier(() -> {
                log.debug("Executando identificação 1:N");
                
                IdentifyRequest request = IdentifyRequest.builder()
                    .biometricData(biometricData)
                    .filters(IdentifyFilters.builder()
                        .maxCandidates(10)
                        .threshold(70)
                        .build())
                    .build();
                
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
            })
        );
    }
    
    public EnrollResponse cadastrarEncounter(String cpf, String idColetaLegado, BiometricData biometricData) {
        return circuitBreaker.executeSupplier(() ->
            retry.executeSupplier(() -> {
                log.debug("Cadastrando encounter para CPF: {}, ID: {}", cpf, idColetaLegado);
                
                return webClient.post()
                    .uri(osiaBaseUrl + "/v1/persons/{cpf}/encounters/{id}", cpf, idColetaLegado)
                    .header("X-Correlation-ID", MDC.get("correlationId"))
                    .bodyValue(biometricData)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, this::handleClientError)
                    .onStatus(HttpStatusCode::is5xxServerError, this::handleServerError)
                    .bodyToMono(EnrollResponse.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();
            })
        );
    }
    
    public void deletarEncounter(String cpf, String encounterId) {
        circuitBreaker.executeRunnable(() ->
            retry.executeRunnable(() -> {
                log.warn("Deletando encounter: {} para CPF: {}", encounterId, cpf);
                
                webClient.delete()
                    .uri(osiaBaseUrl + "/v1/persons/{cpf}/encounters/{encounterId}", cpf, encounterId)
                    .header("X-Correlation-ID", MDC.get("correlationId"))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, this::handleClientError)
                    .onStatus(HttpStatusCode::is5xxServerError, this::handleServerError)
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(10))
                    .block();
            })
        );
    }
    
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
}
```

---

## 4. Fluxo DETRAN - Implementação Detalhada

### 4.1 Código Java

```java
@Service
@Slf4j
public class DeduplicacaoDETRANService {

    private final OSIAClient osiaClient;
    private final ColetaRepository coletaRepository;
    private final AuditService auditService;
    
    @Transactional
    public ResultadoDeduplicacao processar(Coleta coleta) {
        log.info("Iniciando processamento DETRAN para CPF: {}, ID: {}", 
                 coleta.getCpf(), coleta.getIdColetaLegado());
        
        try {
            // RN015 - Verificar pré-requisito: coleta prévia no ABIS
            EncountersResponse encounters = osiaClient.buscarEncounters(coleta.getCpf());
            
            if (encounters.getTotalCount() == 0) {
                return marcarInconclusivo(coleta, "DETRAN sem coleta prévia no ABIS");
            }
            
            // RN015 - Executar verificação 1:1
            VerifyResponse verifyResult = osiaClient.verificar11(coleta.getCpf(), coleta.getBiometriaData());
            
            // RN016 - CNH sem match é inconclusivo
            if (!verifyResult.isVerified()) {
                return marcarInconclusivo(coleta, "DETRAN: verificação 1:1 sem match (suspeito)");
            }
            
            // Match encontrado - processar normalmente
            cadastrarNoABIS(coleta);
            atualizarStatus(coleta, StatusValidacao.VALIDA);
            return ResultadoDeduplicacao.sucesso(StatusValidacao.VALIDA);
            
        } catch (OSIAException e) {
            return tratarErroOSIA(coleta, e);
        }
    }
    
    private ResultadoDeduplicacao marcarInconclusivo(Coleta coleta, String motivo) {
        coleta.setStatusValidacao(StatusValidacao.INCONCLUSIVA);
        coleta.setValidacaoPendente(true);
        coleta.setMotivoInconclusivo(motivo);
        coletaRepository.save(coleta);
        
        auditService.registrar("DETRAN - Coleta marcada como INCONCLUSIVA: " + motivo, coleta);
        
        return ResultadoDeduplicacao.inconclusivo(motivo);
    }
    
    private void cadastrarNoABIS(Coleta coleta) {
        // Mesma implementação do IIRGD
    }
    
    private void atualizarStatus(Coleta coleta, StatusValidacao status) {
        // Mesma implementação do IIRGD
    }
    
    private ResultadoDeduplicacao tratarErroOSIA(Coleta coleta, OSIAException e) {
        // Mesma implementação do IIRGD
    }
}
```

---

## 5. Configuração de Resiliência

### 5.1 application.yml

```yaml
resilience4j:
  circuitbreaker:
    instances:
      osiaClient:
        registerHealthIndicator: true
        slidingWindowSize: 100
        minimumNumberOfCalls: 10
        permittedNumberOfCallsInHalfOpenState: 5
        automaticTransitionFromOpenToHalfOpenEnabled: true
        waitDurationInOpenState: 30s
        failureRateThreshold: 50
        slowCallRateThreshold: 50
        slowCallDurationThreshold: 10s
        recordExceptions:
          - com.sp.biometria.exception.OSIAException
        
  retry:
    instances:
      osiaClient:
        maxAttempts: 3
        waitDuration: 1s
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - org.springframework.web.reactive.function.client.WebClientRequestException
          - java.net.SocketTimeoutException
        ignoreExceptions:
          - com.sp.biometria.exception.OSIAClientException
          
  ratelimiter:
    instances:
      osiaClient:
        registerHealthIndicator: true
        limitForPeriod: 100
        limitRefreshPeriod: 1s
        timeoutDuration: 5s
```

---

## 6. DTOs e Modelos

```java
// Response do endpoint GET /v1/persons/{cpf}/encounters
@Data
@Builder
public class EncountersResponse {
    private List<Encounter> encounters;
    private int totalCount;
}

@Data
@Builder
public class Encounter {
    private String encounterId;
    private BiometricData biometricData;
    private EncounterMetadata metadata;
}

@Data
@Builder
public class EncounterMetadata {
    private String idColetaLegado;
    private String origem;
    private Integer nfiq2Score;
    private LocalDateTime dataColeta;
}

// Response do POST /v1/verify/ALL/{cpf}
@Data
@Builder
public class VerifyResponse {
    private boolean verified;
    private Double score;
    private String encounterId;
    
    public boolean isPessoaEncontrada() {
        return encounterId != null;
    }
}

// Response do POST /v1/identify
@Data
@Builder
public class IdentifyResponse {
    private List<Candidate> candidates;
    private int totalMatches;
}

@Data
@Builder
public class Candidate {
    private String personId;
    private String encounterId;
    private Double score;
}

// Response do POST /v1/persons/{cpf}/encounters/{id}
@Data
@Builder
public class EnrollResponse {
    private String encounterId;
    private String personId;
    private String status;
}

// Request para POST /v1/identify
@Data
@Builder
public class IdentifyRequest {
    private BiometricData biometricData;
    private IdentifyFilters filters;
}

@Data
@Builder
public class IdentifyFilters {
    private Integer maxCandidates;
    private Integer threshold;
}

// Dados biométricos
@Data
@Builder
public class BiometricData {
    private List<Fingerprint> fingerprints;
    private FaceImage face;
}

@Data
@Builder
public class Fingerprint {
    private String position; // RIGHT_THUMB, LEFT_INDEX, etc.
    private String image; // Base64
    private String format; // WSQ, PNG, etc.
}

@Data
@Builder
public class FaceImage {
    private String image; // Base64
    private String format; // JPEG, PNG
}
```

---

## 7. Tabela de Decisão Consolidada

| Origem | Encounters Existentes | Verify 1:1 | Identify 1:N | Decisão | Ação ABIS | Status |
|--------|----------------------|------------|--------------|---------|-----------|--------|
| **IIRGD** | 0 | N/A | 0 matches | **VALIDA** | Inserir | VALIDA |
| **IIRGD** | 0 | N/A | 1 match | **INCONCLUSIVA** | Não inserir | INCONCLUSIVA |
| **IIRGD** | 0 | N/A | 2+ matches | **INCONCLUSIVA** | Não inserir | INCONCLUSIVA |
| **IIRGD** | 1+ | Verified=true | - | **VALIDA** (se 1 enc) | Inserir/Atualizar | VALIDA |
| **IIRGD** | 2+ | Verified=true | - | **Aplicar NFIQ2** | Manter 2 melhores | VALIDA |
| **IIRGD** | 1+ | Verified=false | - | **INCONCLUSIVA** | Não inserir | INCONCLUSIVA |
| **DETRAN** | 0 | N/A | - | **INCONCLUSIVA** | Não inserir | INCONCLUSIVA |
| **DETRAN** | 1+ | Verified=true | - | **VALIDA** | Inserir/Atualizar | VALIDA |
| **DETRAN** | 1+ | Verified=false | - | **INCONCLUSIVA** | Não inserir | INCONCLUSIVA |

---

## 8. Testes Unitários

```java
@SpringBootTest
class DeduplicacaoIIRGDServiceTest {

    @Mock
    private OSIAClient osiaClient;
    
    @Mock
    private ColetaRepository coletaRepository;
    
    @InjectMocks
    private DeduplicacaoIIRGDService service;
    
    @Test
    @DisplayName("TA-IIRGD-001: Entrada válida + 1:1 encontrado + 1 encounter = VALIDA")
    void testEntradaValidaComUmEncounter() {
        // Arrange
        Coleta coleta = criarColetaValida();
        
        when(osiaClient.buscarEncounters(anyString()))
            .thenReturn(EncountersResponse.builder()
                .encounters(List.of(criarEncounter()))
                .totalCount(1)
                .build());
                
        when(osiaClient.verificar11(anyString(), any()))
            .thenReturn(VerifyResponse.builder()
                .verified(true)
                .score(92.5)
                .encounterId("enc-123")
                .build());
                
        when(osiaClient.cadastrarEncounter(anyString(), anyString(), any()))
            .thenReturn(EnrollResponse.builder()
                .encounterId("enc-456")
                .personId("12345678900")
                .status("ENROLLED")
                .build());
        
        // Act
        ResultadoDeduplicacao resultado = service.processar(coleta);
        
        // Assert
        assertEquals(StatusValidacao.VALIDA, resultado.getStatus());
        verify(osiaClient, times(1)).cadastrarEncounter(anyString(), anyString(), any());
        verify(coletaRepository, times(1)).save(argThat(c -> 
            c.getStatusValidacao() == StatusValidacao.VALIDA && 
            !c.isValidacaoPendente()
        ));
    }
    
    @Test
    @DisplayName("TA-IIRGD-003: Entrada válida + 1:1 encontrado sem match biométrico = INCONCLUSIVA")
    void testVerifyEncontradoSemMatch() {
        // Arrange
        Coleta coleta = criarColetaValida();
        
        when(osiaClient.buscarEncounters(anyString()))
            .thenReturn(EncountersResponse.builder()
                .encounters(List.of(criarEncounter()))
                .totalCount(1)
                .build());
                
        when(osiaClient.verificar11(anyString(), any()))
            .thenReturn(VerifyResponse.builder()
                .verified(false)
                .score(45.3)
                .encounterId("enc-123")
                .build());
        
        // Act
        ResultadoDeduplicacao resultado = service.processar(coleta);
        
        // Assert
        assertEquals(StatusValidacao.INCONCLUSIVA, resultado.getStatus());
        assertTrue(resultado.getMotivo().contains("biometria não corresponde"));
        verify(osiaClient, never()).cadastrarEncounter(anyString(), anyString(), any());
        verify(coletaRepository, times(1)).save(argThat(c -> 
            c.getStatusValidacao() == StatusValidacao.INCONCLUSIVA && 
            c.isValidacaoPendente()
        ));
    }
    
    @Test
    @DisplayName("TA-IIRGD-006: Entrada válida + 1:1 não encontrado + 1:N 0 matches = VALIDA")
    void testVerifyNaoEncontradoIdentify0Matches() {
        // Arrange
        Coleta coleta = criarColetaValida();
        
        when(osiaClient.buscarEncounters(anyString()))
            .thenReturn(EncountersResponse.builder()
                .encounters(Collections.emptyList())
                .totalCount(0)
                .build());
                
        when(osiaClient.verificar11(anyString(), any()))
            .thenReturn(VerifyResponse.builder()
                .verified(false)
                .score(null)
                .encounterId(null)
                .build());
                
        when(osiaClient.identificar1N(any()))
            .thenReturn(IdentifyResponse.builder()
                .candidates(Collections.emptyList())
                .totalMatches(0)
                .build());
                
        when(osiaClient.cadastrarEncounter(anyString(), anyString(), any()))
            .thenReturn(EnrollResponse.builder()
                .encounterId("enc-new")
                .personId("12345678900")
                .status("ENROLLED")
                .build());
        
        // Act
        ResultadoDeduplicacao resultado = service.processar(coleta);
        
        // Assert
        assertEquals(StatusValidacao.VALIDA, resultado.getStatus());
        verify(osiaClient, times(1)).cadastrarEncounter(anyString(), anyString(), any());
    }
    
    @Test
    @DisplayName("TA-DETRAN-001: Sem coleta prévia no ABIS = INCONCLUSIVA")
    void testDetranSemColetaPrevia() {
        // Arrange
        Coleta coleta = criarColetaDETRAN();
        
        when(osiaClient.buscarEncounters(anyString()))
            .thenReturn(EncountersResponse.builder()
                .encounters(Collections.emptyList())
                .totalCount(0)
                .build());
        
        // Act
        DeduplicacaoDETRANService detranService = new DeduplicacaoDETRANService(
            osiaClient, coletaRepository, auditService
        );
        ResultadoDeduplicacao resultado = detranService.processar(coleta);
        
        // Assert
        assertEquals(StatusValidacao.INCONCLUSIVA, resultado.getStatus());
        assertTrue(resultado.getMotivo().contains("sem coleta prévia"));
        verify(osiaClient, never()).verificar11(anyString(), any());
    }
    
    private Coleta criarColetaValida() {
        return Coleta.builder()
            .cpf("12345678900")
            .idColetaLegado("COL-123")
            .origem("IIRGD")
            .validacaoPendente(false)
            .dataNascimento(LocalDate.of(2000, 1, 1))
            .nfiq2Score(85)
            .biometriaData(BiometricData.builder().build())
            .build();
    }
    
    private Coleta criarColetaDETRAN() {
        Coleta coleta = criarColetaValida();
        coleta.setOrigem("DETRAN");
        return coleta;
    }
    
    private Encounter criarEncounter() {
        return Encounter.builder()
            .encounterId("enc-123")
            .metadata(EncounterMetadata.builder()
                .nfiq2Score(80)
                .origem("IIRGD")
                .build())
            .build();
    }
}
```

---

## 9. Métricas e Observabilidade

### 9.1 Métricas Customizadas

```java
@Component
public class DeduplicacaoMetrics {

    private final MeterRegistry meterRegistry;
    
    private final Counter coletasProcessadas;
    private final Counter coletasValidas;
    private final Counter coletasInconclusivas;
    private final Counter coletasInvalidas;
    private final Timer tempoProcessamento;
    private final Counter chamadasOSIA;
    
    public DeduplicacaoMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        this.coletasProcessadas = Counter.builder("deduplicacao.coletas.processadas")
            .tag("modulo", "deduplicacao")
            .description("Total de coletas processadas")
            .register(meterRegistry);
            
        this.coletasValidas = Counter.builder("deduplicacao.coletas.validas")
            .tag("modulo", "deduplicacao")
            .description("Coletas validadas com sucesso")
            .register(meterRegistry);
            
        this.coletasInconclusivas = Counter.builder("deduplicacao.coletas.inconclusivas")
            .tag("modulo", "deduplicacao")
            .description("Coletas que requerem análise manual")
            .register(meterRegistry);
            
        this.coletasInvalidas = Counter.builder("deduplicacao.coletas.invalidas")
            .tag("modulo", "deduplicacao")
            .description("Coletas invalidadas")
            .register(meterRegistry);
            
        this.tempoProcessamento = Timer.builder("deduplicacao.tempo.processamento")
            .tag("modulo", "deduplicacao")
            .description("Tempo de processamento de coletas")
            .register(meterRegistry);
            
        this.chamadasOSIA = Counter.builder("deduplicacao.osia.chamadas")
            .tag("modulo", "deduplicacao")
            .description("Chamadas ao ABIS OSIA")
            .register(meterRegistry);
    }
    
    public void registrarProcessamento(Coleta coleta, ResultadoDeduplicacao resultado, Duration duracao) {
        coletasProcessadas.increment();
        tempoProcessamento.record(duracao);
        
        switch (resultado.getStatus()) {
            case VALIDA -> coletasValidas.increment();
            case INCONCLUSIVA -> coletasInconclusivas.increment();
            case INVALIDA -> coletasInvalidas.increment();
        }
    }
    
    public void registrarChamadaOSIA(String endpoint, boolean sucesso) {
        chamadasOSIA.increment();
        
        Counter.builder("deduplicacao.osia.chamadas.endpoint")
            .tag("endpoint", endpoint)
            .tag("sucesso", String.valueOf(sucesso))
            .register(meterRegistry)
            .increment();
    }
}
```

---

## 10. Considerações de Deploy

### 10.1 Variáveis de Ambiente

```properties
# OSIA Configuration
OSIA_BASE_URL=https://abis.sp.gov.br/osia
OSIA_TIMEOUT_SECONDS=15
OSIA_MAX_RETRIES=3

# Circuit Breaker
CB_FAILURE_RATE_THRESHOLD=50
CB_SLOW_CALL_THRESHOLD=10
CB_WAIT_DURATION_OPEN=30

# Thread Pool
DEDUPLICACAO_THREAD_POOL_SIZE=10
DEDUPLICACAO_QUEUE_CAPACITY=100
```

### 10.2 Health Check

```java
@Component
public class OSIAHealthIndicator implements HealthIndicator {

    private final OSIAClient osiaClient;
    
    @Override
    public Health health() {
        try {
            // Verificar conectividade básica
            osiaClient.healthCheck();
            return Health.up()
                .withDetail("osia", "connected")
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("osia", "unreachable")
                .withException(e)
                .build();
        }
    }
}
```

---

## 11. Próximos Passos

1. **Implementar Serviço de Auditoria** completo
2. **Configurar Redis** para idempotência
3. **Criar Dashboard Grafana** com métricas customizadas
4. **Implementar testes de integração** com WireMock para OSIA
5. **Configurar pipeline CI/CD** com testes automatizados
6. **Documentar API OpenAPI/Swagger**

---

**Documento gerado em:** 28/11/2024  
**Versão:** 1.0  
**Autor:** Arquitetura Biometria SP
