# Docker Local Development

Este diretório contém os arquivos necessários para executar toda a infraestrutura localmente usando Docker Compose.

## Pré-requisitos

- Docker 20.10+
- Docker Compose 2.0+
- Mínimo 8GB RAM disponível
- Mínimo 20GB de espaço em disco

## Infraestrutura Local

A stack local inclui:
- **SQL Server 2022**: Databases `batch_metadata` e `deduplicacao`
- **Redis 7**: Cache e distributed locks
- **OpenSearch 2.11**: Armazenamento de dados biométricos
- **OpenSearch Dashboards**: Interface de visualização (opcional)
- **Deduplicação Service**: API REST (porta 8080)
- **Batch Processor**: Processamento em lote (porta 8081)

## Como Executar

### 1. Iniciar toda a infraestrutura

```bash
cd /home/lsilva/Dev/workspace-biometria/bio-sanitizacao
docker-compose up -d
```

### 2. Verificar status dos serviços

```bash
docker-compose ps
```

### 3. Ver logs

```bash
# Todos os serviços
docker-compose logs -f

# Serviço específico
docker-compose logs -f deduplicacao-service
docker-compose logs -f batch-processor
```

### 4. Parar os serviços

```bash
docker-compose down
```

### 5. Parar e remover volumes (reset completo)

```bash
docker-compose down -v
```

## Acessos

### Aplicações
- **Deduplicação Service**: http://localhost:8080
  - Health: http://localhost:8080/actuator/health
  - Metrics: http://localhost:8080/actuator/prometheus

- **Batch Processor**: http://localhost:8081
  - Health: http://localhost:8081/actuator/health
  - Metrics: http://localhost:8081/actuator/prometheus

### Infraestrutura
- **SQL Server**: localhost:1433
  - User: `sa`
  - Password: `YourStrong@Passw0rd`
  - Databases: `batch_metadata`, `deduplicacao`

- **Redis**: localhost:6379
  - Sem senha

- **OpenSearch**: https://localhost:9200
  - User: `admin`
  - Password: `Admin@123`

- **OpenSearch Dashboards**: http://localhost:5601

## Profiles Spring Boot

### Local (application-local.yml)
```bash
export SPRING_PROFILES_ACTIVE=local
```
- Configurado para infraestrutura Docker local
- Logging DEBUG para desenvolvimento
- Pool de conexões reduzido
- Grid size: 5 partições
- Chunk size: 50 registros

### Dev (application-dev.yml)
```bash
export SPRING_PROFILES_ACTIVE=dev
```
- Configurado via variáveis de ambiente
- Logging DEBUG para troubleshooting
- Pool médio de conexões
- Grid size: 10 partições
- Chunk size: 100 registros

### Staging (application-staging.yml)
```bash
export SPRING_PROFILES_ACTIVE=staging
```
- Configurações intermediárias
- Logging INFO
- Pool maior de conexões
- Grid size: 20 partições
- Chunk size: 200 registros
- Circuit breakers configurados
- Graceful shutdown habilitado

### Production (application-prod.yml)
```bash
export SPRING_PROFILES_ACTIVE=prod
```
- Otimizado para alto throughput
- Logging WARN/ERROR
- Pool máximo de conexões (100+)
- Grid size: 50 partições
- Chunk size: 500 registros
- Circuit breakers, retries e rate limiters
- Cache de segundo nível (Hibernate + Redis)
- Graceful shutdown com 60s timeout
- Health probes para Kubernetes

## Executando Aplicações Localmente (sem Docker)

### Deduplicação Service
```bash
cd modules/deduplicacao-service
export SPRING_PROFILES_ACTIVE=local
mvn spring-boot:run
```

### Batch Processor
```bash
cd modules/batch-processor
export SPRING_PROFILES_ACTIVE=local
mvn spring-boot:run
```

## Troubleshooting

### SQL Server não inicia
- Verifique se a porta 1433 está livre
- Aumente a memória do Docker (mínimo 4GB)

### OpenSearch falha no healthcheck
- OpenSearch precisa de pelo menos 2GB RAM
- Aguarde até 2 minutos para inicialização completa

### Aplicações não conectam ao SQL Server
- Aguarde o healthcheck do SQL Server ficar healthy
- Verifique logs: `docker-compose logs sqlserver`

### OpenSearch SSL error
- Para desenvolvimento local, use `OPENSEARCH_SSL_VERIFY=false`
- Certificado é auto-assinado

## Dados de Teste

Para popular o OpenSearch com dados de teste:

```bash
# TODO: Adicionar script de carga de dados de teste
```

## Performance Tuning

### Para desenvolvimento local
- Reduza `BATCH_PARTITION_GRID_SIZE` para 3-5
- Use chunk-size menor (50-100)
- Limite memória do OpenSearch: 1-2GB

### Para simular produção local
- Aumente recursos do Docker para 8GB+
- Configure grid-size: 10-20
- Configure chunk-size: 200-500

## Monitoramento

### Prometheus Metrics
```bash
curl http://localhost:8080/actuator/prometheus
curl http://localhost:8081/actuator/prometheus
```

### Redis
```bash
docker exec -it bio-redis redis-cli
> INFO stats
> KEYS *
```

### SQL Server
```bash
docker exec -it bio-sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "YourStrong@Passw0rd" -C
> SELECT name FROM sys.databases;
> GO
```

### OpenSearch
```bash
curl -k -u admin:Admin@123 https://localhost:9200/_cat/indices?v
curl -k -u admin:Admin@123 https://localhost:9200/_cluster/health?pretty
```