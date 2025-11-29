# Exemplo de Chamada ao Endpoint de Deduplicação

Este documento demonstra como chamar o endpoint de deduplicação do `deduplicacao-service` a partir do `batch-processor`.

## Endpoint

```
POST http://localhost:8080/api/v1/deduplicacao/processar
Content-Type: application/json
```

## Request Body - Exemplo 1: Coleta Válida (CPF terminando em 0-6)

```json
{
  "idColeta": "COL-2024-000001",
  "cpf": "12345678900",
  "dataNascimento": "1990-05-15T00:00:00.000+00:00",
  "sistemaOrigem": "IIRGD",
  "dadosBiometricos": [
    {
      "tipo": "IMPRESSAO_DIGITAL",
      "posicao": "POLEGAR_DIREITO",
      "template": "base64encodedtemplate...",
      "nfiq2Score": 85,
      "formato": "ISO_19794_2"
    },
    {
      "tipo": "IMPRESSAO_DIGITAL",
      "posicao": "INDICADOR_DIREITO",
      "template": "base64encodedtemplate...",
      "nfiq2Score": 90,
      "formato": "ISO_19794_2"
    }
  ]
}
```

### Response Esperada (Match Encontrado)

```json
{
  "idColeta": "COL-2024-000001",
  "cpf": "12345678900",
  "status": "VALIDA",
  "abisEncounterId": "550e8400-e29b-41d4-a716-446655440000",
  "matchScore": 85.5,
  "dataProcessamento": "2025-11-29T14:15:30",
  "mensagem": "Match biométrico encontrado com score acima do threshold",
  "jaProcessado": false,
  "requerAnaliseManual": false,
  "sistemaOrigem": "IIRGD"
}
```

## Request Body - Exemplo 2: Caso Inconclusivo (CPF terminando em 7)

```json
{
  "idColeta": "COL-2024-000002",
  "cpf": "98765432107",
  "dataNascimento": "1985-03-20T00:00:00.000+00:00",
  "sistemaOrigem": "IIRGD",
  "dadosBiometricos": [
    {
      "tipo": "IMPRESSAO_DIGITAL",
      "posicao": "POLEGAR_DIREITO",
      "template": "base64encodedtemplate...",
      "nfiq2Score": 75,
      "formato": "ISO_19794_2"
    }
  ]
}
```

### Response Esperada (Inconclusivo)

```json
{
  "idColeta": "COL-2024-000002",
  "cpf": "98765432107",
  "status": "INCONCLUSIVA",
  "matchScore": 55.0,
  "motivoInconclusivo": "Pessoa encontrada no ABIS mas score biométrico abaixo do threshold (55% < 70%)",
  "dataProcessamento": "2025-11-29T14:15:35",
  "jaProcessado": false,
  "requerAnaliseManual": true,
  "sistemaOrigem": "IIRGD"
}
```

## Request Body - Exemplo 3: Nova Pessoa Cadastrada (CPF terminando em 8)

```json
{
  "idColeta": "COL-2024-000003",
  "cpf": "11122233348",
  "dataNascimento": "1995-08-10T00:00:00.000+00:00",
  "sistemaOrigem": "IIRGD",
  "dadosBiometricos": [
    {
      "tipo": "IMPRESSAO_DIGITAL",
      "posicao": "POLEGAR_DIREITO",
      "template": "base64encodedtemplate...",
      "nfiq2Score": 92,
      "formato": "ISO_19794_2"
    },
    {
      "tipo": "IMPRESSAO_DIGITAL",
      "posicao": "INDICADOR_DIREITO",
      "template": "base64encodedtemplate...",
      "nfiq2Score": 88,
      "formato": "ISO_19794_2"
    }
  ]
}
```

### Response Esperada (Nova Pessoa)

```json
{
  "idColeta": "COL-2024-000003",
  "cpf": "11122233348",
  "status": "VALIDA",
  "abisEncounterId": "660f9511-f3ac-52e5-b827-557766551111",
  "dataProcessamento": "2025-11-29T14:15:40",
  "mensagem": "Nova pessoa cadastrada no ABIS (nenhum match encontrado em busca 1:N)",
  "jaProcessado": false,
  "requerAnaliseManual": false,
  "sistemaOrigem": "IIRGD"
}
```

## Request Body - Exemplo 4: Inválida - Qualidade Insuficiente (CPF terminando em 9)

```json
{
  "idColeta": "COL-2024-000004",
  "cpf": "55544433329",
  "dataNascimento": "1988-12-25T00:00:00.000+00:00",
  "sistemaOrigem": "IIRGD",
  "dadosBiometricos": [
    {
      "tipo": "IMPRESSAO_DIGITAL",
      "posicao": "POLEGAR_DIREITO",
      "template": "base64encodedtemplate...",
      "nfiq2Score": 30,
      "formato": "ISO_19794_2"
    }
  ]
}
```

### Response Esperada (Inválida)

```json
{
  "idColeta": "COL-2024-000004",
  "cpf": "55544433329",
  "status": "INVALIDA",
  "motivoRejeicao": "Qualidade biométrica insuficiente - NFIQ2 score abaixo do mínimo",
  "dataProcessamento": "2025-11-29T14:15:45",
  "jaProcessado": false,
  "requerAnaliseManual": false,
  "sistemaOrigem": "IIRGD"
}
```

## Erro de Validação

### Request Inválido (CPF sem 11 dígitos)

```json
{
  "idColeta": "COL-2024-000005",
  "cpf": "12345",
  "dataNascimento": "1990-01-01T00:00:00.000+00:00",
  "sistemaOrigem": "IIRGD"
}
```

### Response de Erro (400 Bad Request)

```json
{
  "status": 400,
  "error": "Validation Failed",
  "message": "Erro de validação nos campos da requisição",
  "timestamp": "2025-11-29T14:15:50",
  "path": "/api/v1/deduplicacao/processar",
  "validationErrors": {
    "cpf": "CPF deve conter 11 dígitos"
  }
}
```

## Testando com cURL

### Exemplo 1: Match Encontrado

```bash
curl -X POST http://localhost:8080/api/v1/deduplicacao/processar \
  -H "Content-Type: application/json" \
  -d '{
    "idColeta": "COL-2024-000001",
    "cpf": "12345678900",
    "dataNascimento": "1990-05-15T00:00:00.000+00:00",
    "sistemaOrigem": "IIRGD",
    "dadosBiometricos": [
      {
        "tipo": "IMPRESSAO_DIGITAL",
        "posicao": "POLEGAR_DIREITO",
        "template": "base64encodedtemplate...",
        "nfiq2Score": 85,
        "formato": "ISO_19794_2"
      }
    ]
  }'
```

### Exemplo 2: Health Check

```bash
curl http://localhost:8080/api/v1/deduplicacao/health
```

Response:
```json
{
  "status": "UP",
  "service": "deduplicacao-service",
  "timestamp": "2025-11-29T14:16:00",
  "abisStatus": "UP"
}
```

## Swagger UI

Acesse a documentação interativa da API em:

```
http://localhost:8080/swagger-ui.html
```

## Integração com Batch Processor

O `batch-processor` deve chamar este endpoint durante o processamento de cada chunk de coletas:

```java
@Service
public class DeduplicacaoServiceClient {

    private final RestTemplate restTemplate;

    @Value("${deduplicacao.service.url}")
    private String deduplicacaoServiceUrl;

    public DeduplicacaoResponse processar(DeduplicacaoRequest request) {
        String url = deduplicacaoServiceUrl + "/api/v1/deduplicacao/processar";

        return restTemplate.postForObject(
            url,
            request,
            DeduplicacaoResponse.class
        );
    }
}
```

## Status de Validação

| Status | Descrição | Requer Análise Manual |
|--------|-----------|----------------------|
| `VALIDA` | Coleta validada com sucesso (match encontrado ou nova pessoa cadastrada) | Não |
| `INCONCLUSIVA` | Pessoa encontrada mas score biométrico abaixo do threshold | Sim |
| `INVALIDA` | Coleta rejeitada por não atender critérios de qualidade | Não |
| `ERRO_REPROCESSAVEL` | Erro temporário (ABIS indisponível, timeout, etc.) | Sim |

## Notas de Implementação

### Mock atual:
- O endpoint está mockado para testes
- O comportamento depende do **último dígito do CPF**:
  - **0-6**: Match encontrado (VALIDA)
  - **7**: Inconclusivo (score < threshold)
  - **8**: Nova pessoa cadastrada (VALIDA)
  - **9**: Inválida (qualidade insuficiente)

### Implementação real (TODO):
Quando conectado ao ABIS Thales via OSIA:
1. Validar critérios de entrada (RN003)
2. Buscar encounters existentes (RN004)
3. Executar verificação 1:1 (RN004)
4. Aplicar seleção por NFIQ2 (RN005)
5. Executar identificação 1:N se necessário (RN007)
6. Cadastrar encounter se não houver match (RN008)
7. Marcar como inconclusivo/inválido conforme regras (RN009/RN010)
8. Implementar idempotência (RN011)