# Exemplos de Chamada aos Endpoints OSIA/ABIS simulados no WireMock

Este documento apresenta exemplos de requisições HTTP para os principais endpoints OSIA/ABIS simulados pelo WireMock em http://localhost:9090.

## 1. /osia/abis/v1/search

**POST http://localhost:9090/osia/abis/v1/search**

**Headers:**
```http
Content-Type: application/json
```

**Body:**
```json
{
  "searchCriteria": {
    "biometricType": "FINGERPRINT",
    "value": "123456789"
  }
}
```

**Resposta:**
```json
{
  "requestId": "<valor aleatório>",
  "status": "ACCEPTED"
}
```

---

## 2. /osia/v1/verify

**POST http://localhost:9090/osia/v1/verify**

**Headers:**
```http
Content-Type: application/json
```

**Body:**
```json
{
  "biometricType": "FINGERPRINT",
  "value": "123456789"
}
```

**Resposta:**
```json
{
  "result": "success",
  "match": true,
  "personId": "123456",
  "score": 98.7
}
```

---

## 3. /osia/v1/identify

**POST http://localhost:9090/osia/v1/identify**

**Headers:**
```http
Content-Type: application/json
```

**Body:**
```json
{
  "biometricType": "FACE",
  "value": "987654321"
}
```

**Resposta:**
```json
{
  "result": "success",
  "personId": "789012",
  "score": 95.3
}
```

---

## 4. /osia/v1/create-encounter

**POST http://localhost:9090/osia/v1/create-encounter**

**Headers:**
```http
Content-Type: application/json
```

**Body:**
```json
{
  "personId": "123456",
  "encounterData": {
    "location": "SP",
    "date": "2025-11-29"
  }
}
```

**Resposta:**
```json
{
  "result": "created",
  "encounterId": "enc-001"
}
```

---

## 5. /osia/v1/update-encounter

**PUT http://localhost:9090/osia/v1/update-encounter**

**Headers:**
```http
Content-Type: application/json
```

**Body:**
```json
{
  "encounterId": "enc-001",
  "encounterData": {
    "location": "RJ",
    "date": "2025-12-01"
  }
}
```

**Resposta:**
```json
{
  "result": "updated",
  "encounterId": "enc-001"
}
```

---

## 6. /osia/v1/delete-person/{id}

**DELETE http://localhost:9090/osia/v1/delete-person/123456**

**Headers:**
```http
Content-Type: application/json
```

**Resposta:**
Status 204 (No Content)

---

## 7. Erro genérico

**POST http://localhost:9090/osia/v1/qualquer-endpoint**

**Resposta:**
```json
{
  "result": "error",
  "message": "Erro técnico simulado pelo WireMock."
}
```

---

> Para testar, utilize Apidog, Postman, curl ou qualquer ferramenta de API. Certifique-se de que o WireMock está rodando na porta 9090.
