# Guia de Profiles e Ambientes

Este documento descreve os profiles Spring Boot criados para os diferentes ambientes e como configurá-los para máximo desempenho.

## 📋 Índice

1. [Pré-requisitos](#pré-requisitos)
2. [Build do Projeto](#build-do-projeto)
3. [Profiles Disponíveis](#profiles-disponíveis)
4. [Configuração por Ambiente](#configuração-por-ambiente)
5. [Throughput e Performance](#throughput-e-performance)
6. [Deployment](#deployment)

## Pré-requisitos

### Java 21

⚠️ **IMPORTANTE**: Este projeto requer Java 21. Se você estiver usando outra versão, mude para Java 21:

```bash
# Com SDKMAN
sdk use java 21.0.7-tem

# Verificar versão
java -version
```

### Build Script

Use o script `build.sh` que configura automaticamente o Java 21:

```bash
# Tornar executável (primeira vez)
chmod +x build.sh

# Build completo
./build.sh

# Build específico
./build.sh clean compile -DskipTests
./build.sh clean package
./build.sh test
```

## Build do Projeto

### Compilação

```bash
# Build completo com testes
./build.sh clean package

# Build sem testes (mais rápido)
./build.sh clean package -DskipTests

# Apenas compilação
./build.sh clean compile -DskipTests
```

### Verificar Build

```bash
# Verificar JARs gerados
ls -lh modules/*/target/*.jar

# Deve mostrar:
# modules/deduplicacao-commons/target/deduplicacao-commons-1.0.0-SNAPSHOT.jar
# modules/deduplicacao-service/target/deduplicacao-service-1.0.0-SNAPSHOT.jar
# modules/batch-processor/target/batch-processor-1.0.0-SNAPSHOT.jar
```

## Profiles Disponíveis

### 1. Local (`application-local.yml`)

**Uso**: Desenvolvimento local com Docker Compose

**Características**:
- SQL Server local (localhost:1433)
- Redis local (localhost:6379)
- OpenSearch local (localhost:9200)
- Logging DEBUG
- Pool de conexões reduzido
- Grid size: 5 partições
- Chunk size: 50 registros

**Como usar**:
```bash
export SPRING_PROFILES_ACTIVE=local
./build.sh spring-boot:run -pl modules/batch-processor
```

### 2. Dev (`application-dev.yml`)

**Uso**: Ambiente de desenvolvimento

**Características**:
- Configurado via variáveis de ambiente
- Logging DEBUG para troubleshooting
- Pool médio de conexões
- Grid size: 10 partições
- Chunk size: 100 registros

**Variáveis necessárias**:
```bash
export SPRING_PROFILES_ACTIVE=dev
export DB_HOST=dev-sqlserver.example.com
export DB_PORT=1433
export DB_NAME=batch_metadata
export DB_USER=app_user
export DB_PASSWORD=your_password
export REDIS_HOST=dev-redis.example.com
export REDIS_PORT=6379
export OPENSEARCH_HOST=dev-opensearch.example.com
export OPENSEARCH_PORT=9200
export OPENSEARCH_USER=admin
export OPENSEARCH_PASSWORD=admin_password
export DEDUPLICACAO_SERVICE_URL=http://dev-service:8080
```

### 3. Staging (`application-staging.yml`)

**Uso**: Ambiente de homologação

**Características**:
- Configurações intermediárias
- Logging INFO
- Pool maior de conexões (40-50)
- Grid size: 20 partições
- Chunk size: 200 registros
- Circuit breakers configurados
- Graceful shutdown habilitado (30s)

**Performance esperada**: ~50K docs/min

### 4. Production (`application-prod.yml`)

**Uso**: Ambiente de produção com alto throughput

**Características**:
- Otimizado para 100M+ documentos
- Logging WARN/ERROR apenas
- Pool máximo de conexões (100+)
- Grid size: 50 partições
- Chunk size: 500 registros
- Circuit breakers, retries e rate limiters
- Cache de segundo nível (Hibernate + Redis)
- Graceful shutdown com 60s timeout
- Health probes para Kubernetes

**Performance esperada**: ~150K docs/min

**Otimizações incluídas**:
- ✅ ZGC Garbage Collector
- ✅ Connection pooling otimizado
- ✅ Batch inserts/updates (100 por vez)
- ✅ Resilience4j (circuit breakers, retries, bulkhead)
- ✅ Rate limiters para APIs externas
- ✅ Cache L2 com Redisson
- ✅ Prometheus metrics

## Configuração por Ambiente

### Local (Docker Compose)

1. **Subir infraestrutura**:
```bash
docker-compose up -d
```

2. **Verificar serviços**:
```bash
docker-compose ps
```

3. **Executar aplicações**:
```bash
# Batch Processor
export SPRING_PROFILES_ACTIVE=local
./build.sh spring-boot:run -pl modules/batch-processor

# Deduplicação Service (em outro terminal)
export SPRING_PROFILES_ACTIVE=local
./build.sh spring-boot:run -pl modules/deduplicacao-service
```

4. **Verificar saúde**:
```bash
curl http://localhost:8081/actuator/health  # Batch
curl http://localhost:8080/actuator/health  # Service
```

### Dev/Staging/Prod

1. **Configurar variáveis de ambiente** (exemplo para staging):
```bash
export SPRING_PROFILES_ACTIVE=staging
export DB_HOST=staging-rds.us-east-1.rds.amazonaws.com
export DB_PORT=1433
export DB_NAME=batch_metadata
export DB_USER=batch_app
export DB_PASSWORD=$(aws secretsmanager get-secret-value --secret-id staging/db/password --query SecretString --output text)

export REDIS_HOST=staging-redis.abc123.cache.amazonaws.com
export REDIS_PORT=6379

export OPENSEARCH_HOST=vpc-staging-opensearch-xyz.us-east-1.es.amazonaws.com
export OPENSEARCH_PORT=443
export OPENSEARCH_SCHEME=https
export OPENSEARCH_USER=admin
export OPENSEARCH_PASSWORD=$(aws secretsmanager get-secret-value --secret-id staging/opensearch/password --query SecretString --output text)

export DEDUPLICACAO_SERVICE_URL=http://deduplicacao-service:8080

export BATCH_PARTITION_GRID_SIZE=20
```

2. **Executar aplicação**:
```bash
java -jar modules/batch-processor/target/batch-processor-1.0.0-SNAPSHOT.jar
```

## Throughput e Performance

### Cálculo de Throughput

| Profile | Grid Size | Chunk Size | Workers | Throughput Estimado | Tempo para 100M docs |
|---------|-----------|------------|---------|---------------------|----------------------|
| Local   | 5         | 50         | 5       | ~5K docs/min        | ~14 dias             |
| Dev     | 10        | 100        | 10      | ~15K docs/min       | ~4.6 dias            |
| Staging | 20        | 200        | 20      | ~50K docs/min       | ~33 horas            |
| **Prod**| **50**    | **500**    | **50**  | **~150K docs/min**  | **~11 horas**        |

### Ajuste Fino para Produção

Para otimizar ainda mais o throughput em produção:

#### 1. **Aumentar Grid Size** (mais partições paralelas)
```bash
export BATCH_PARTITION_GRID_SIZE=100  # Default: 50
```

#### 2. **Aumentar Chunk Size** (mais registros por batch)
```bash
export BATCH_CHUNK_SIZE=1000  # Default: 500
```

#### 3. **Ajustar Thread Pool**

Editar [application-prod.yml](modules/batch-processor/src/main/resources/application-prod.yml):
```yaml
server:
  tomcat:
    threads:
      max: 800  # Aumentar de 400
      min-spare: 100  # Aumentar de 50
```

#### 4. **Configurar JVM Heap**

Para processar 100M+ docs, recomenda-se:
```bash
export JAVA_OPTS="-Xms4g -Xmx16g -XX:+UseZGC -XX:MaxRAMPercentage=75.0"
```

#### 5. **Otimizar OpenSearch Scroll**
```bash
export OPENSEARCH_SCROLL_SIZE=5000  # Default: 2000
export OPENSEARCH_SCROLL_TIMEOUT=15m  # Default: 10m
```

### Monitoramento de Performance

#### Metrics Prometheus

```bash
# Batch Processor
curl http://localhost:8081/actuator/prometheus | grep batch_

# Deduplicação Service
curl http://localhost:8080/actuator/prometheus | grep dedup_
```

#### Logs de Progresso

```bash
# Acompanhar progresso do batch
tail -f /var/log/batch-processor/application.log | grep "Partition.*writing"

# Ver estatísticas finais
tail -f /var/log/batch-processor/application.log | grep "Job.*finished"
```

## Deployment

### Kubernetes (EKS)

#### ConfigMap para Environment Variables

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: batch-processor-config
  namespace: bio-deduplicacao
data:
  SPRING_PROFILES_ACTIVE: "prod"
  DB_HOST: "prod-rds.us-east-1.rds.amazonaws.com"
  DB_PORT: "1433"
  DB_NAME: "batch_metadata"
  REDIS_HOST: "prod-elasticache.abc123.cache.amazonaws.com"
  REDIS_PORT: "6379"
  OPENSEARCH_HOST: "vpc-prod-opensearch.us-east-1.es.amazonaws.com"
  OPENSEARCH_PORT: "443"
  OPENSEARCH_SCHEME: "https"
  OPENSEARCH_USER: "admin"
  DEDUPLICACAO_SERVICE_URL: "http://deduplicacao-service:8080"
  BATCH_PARTITION_GRID_SIZE: "50"
  BATCH_CHUNK_SIZE: "500"
  JAVA_OPTS: "-Xms4g -Xmx16g -XX:+UseZGC -XX:MaxRAMPercentage=75.0"
```

#### Deployment com Resources

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: batch-processor
  namespace: bio-deduplicacao
spec:
  replicas: 1  # Apenas 1 réplica devido ao distributed lock
  selector:
    matchLabels:
      app: batch-processor
  template:
    metadata:
      labels:
        app: batch-processor
    spec:
      containers:
      - name: batch-processor
        image: your-ecr-repo/batch-processor:latest
        envFrom:
        - configMapRef:
            name: batch-processor-config
        - secretRef:
            name: batch-processor-secrets
        resources:
          requests:
            memory: "8Gi"
            cpu: "4000m"
          limits:
            memory: "20Gi"
            cpu: "8000m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8081
          initialDelaySeconds: 90
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8081
          initialDelaySeconds: 60
          periodSeconds: 10
```

#### CronJob para Execução Agendada

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: biometric-deduplication-job
  namespace: bio-deduplicacao
spec:
  schedule: "0 2 * * *"  # Diariamente às 2h da manhã
  successfulJobsHistoryLimit: 3
  failedJobsHistoryLimit: 3
  jobTemplate:
    spec:
      template:
        spec:
          restartPolicy: OnFailure
          containers:
          - name: batch-processor
            image: your-ecr-repo/batch-processor:latest
            envFrom:
            - configMapRef:
                name: batch-processor-config
            - secretRef:
                name: batch-processor-secrets
            resources:
              requests:
                memory: "8Gi"
                cpu: "4000m"
              limits:
                memory: "20Gi"
                cpu: "8000m"
```

### Docker

```bash
# Build da imagem
docker build -t batch-processor:latest -f modules/batch-processor/Dockerfile .

# Run com variáveis de ambiente
docker run -d \
  --name batch-processor \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_HOST=your-db-host \
  -e DB_PASSWORD=your-password \
  -e REDIS_HOST=your-redis-host \
  -e OPENSEARCH_HOST=your-opensearch-host \
  -p 8081:8081 \
  batch-processor:latest
```

## Troubleshooting

### Build Failures

**Problema**: `COMPILATION ERROR: java.lang.ExceptionInInitializerError`

**Solução**: Use Java 21
```bash
sdk use java 21.0.7-tem
./build.sh clean compile
```

### Runtime Issues

**Problema**: `Cannot start job: distributed lock already held`

**Solução**: Outro job está executando ou o lock não foi liberado
```bash
# Conectar ao Redis e liberar lock
redis-cli -h your-redis-host
> DEL biometric-deduplication-job
```

**Problema**: `OutOfMemoryError: Java heap space`

**Solução**: Aumentar heap do JVM
```bash
export JAVA_OPTS="-Xms8g -Xmx20g -XX:+UseZGC"
```

**Problema**: Low throughput em produção

**Soluções**:
1. Aumentar `BATCH_PARTITION_GRID_SIZE`
2. Aumentar `BATCH_CHUNK_SIZE`
3. Verificar latência de rede para OpenSearch/DB
4. Escalar recursos (CPU/RAM) do pod/container

### Logs

```bash
# Nivel DEBUG para troubleshooting
export LOGGING_LEVEL_BR_GOV_SP_PRODESP=DEBUG

# Ver stack traces completos
tail -f /var/log/batch-processor/application.log | grep -A 20 "ERROR"
```

## Contato e Suporte

Para questões sobre configuração ou performance, consulte:
- [CLAUDE.md](CLAUDE.md) - Overview do projeto
- [docker/README.md](docker/README.md) - Guia Docker local
- GitLab Issues - Reportar problemas