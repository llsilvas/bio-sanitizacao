# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Bio Sanitização** is a biometric deduplication and sanitization system for the State of São Paulo. It provides REST APIs and batch processing capabilities for managing and deduplicating biometric data.

This is a multi-module Maven project targeting Java 21 with Spring Boot 3.2.0, using PostgreSQL for data persistence, Redis for caching/locks, and OpenSearch for data search capabilities.

## Architecture

The system is organized into three main modules:

### `modules/deduplicacao-commons`
Shared library containing:
- Domain models and DTOs (`model/domain`, `model/dto`)
- Custom exceptions (`exception`)
- Utility functions (`util`)
- Enum types (`model/enums`)

This module is a simple JAR library (no Spring Boot) with minimal dependencies: Jackson, Jakarta Validation, and JPA annotations.

### `modules/deduplicacao-service`
REST microservice for biometric deduplication APIs:
- **Port**: 8080
- **Main class**: `DeduplicacaoServiceApplication`
- **Database**: PostgreSQL (schema validation only, DDL must be pre-applied)
- **Cache**: Redis (via Spring Data Redis and Redisson)
- **Key capabilities**:
  - REST controllers for querying/managing biometric data
  - Integration with external systems (DETRAN, MES, ABIS) in `service/integration/`
  - Service layer for business logic (`service/service`)
  - Repository layer with Spring Data JPA (`service/repository`)
  - Resilience4j for fault tolerance
  - Prometheus metrics via Micrometer

### `modules/batch-processor`
Spring Batch job processor for parallel data processing:
- **Port**: 8081
- **Main class**: `BatchProcessorApplication`
- **Databases**: PostgreSQL for application data + separate batch metadata database
- **Key capabilities**:
  - Partitioned batch jobs for processing biometric records
  - OpenSearch integration for data indexing
  - Redis-based distributed locks (Redisson) to prevent concurrent job execution
  - Calls deduplicacao-service APIs for deduplication logic
  - Chunk-based processing (default chunk size: 100 records)
  - Configurable partition grid size (default: 10)

## Build & Test Commands

All commands use Maven from the project root unless specified otherwise.

### Full Build
```bash
mvn clean package
```
Builds all modules, runs tests, and packages JAR files for deployment.

### Build Individual Modules
```bash
# Commons (dependency for others)
mvn -f modules/deduplicacao-commons/pom.xml clean package

# Service
mvn -f modules/deduplicacao-service/pom.xml clean package

# Batch processor
mvn -f modules/batch-processor/pom.xml clean package
```

### Unit Tests
```bash
mvn test
```
Runs JUnit tests for all modules. Reports are generated in `modules/*/target/surefire-reports/`.

### Single Test Class/Method
```bash
# Run specific test class
mvn -Dtest=YourTestClassName test

# Run specific test method
mvn -Dtest=YourTestClassName#testMethodName test
```

### Integration Tests (requires services running)
```bash
mvn verify
```
Runs integration tests. Requires PostgreSQL and Redis to be available.

### Code Quality
```bash
# SonarQube analysis (requires SonarQube server configured)
mvn sonar:sonar

# Code coverage
mvn clean test jacoco:report
# Coverage reports: modules/*/target/site/jacoco/index.html
```

### Compilation Only (no packaging/tests)
```bash
mvn clean compile
```

## Local Development

### With Docker Compose (Recommended)
From the repository root:
```bash
docker compose -f docker/docker-compose.yml up
```

This starts:
- PostgreSQL 14 (port 5432)
- Redis 7 (port 6379)
- Deduplicacao Service (port 8080)
- Batch Processor (port 8081)

Default credentials:
- PostgreSQL: user=`postgres`, password=`postgres`
- Redis: no password

### Running Services Locally
Prerequisites: PostgreSQL 14 and Redis 7 running on localhost

**Service**:
```bash
mvn -f modules/deduplicacao-service/pom.xml spring-boot:run
```

**Batch Processor**:
```bash
mvn -f modules/batch-processor/pom.xml spring-boot:run
```

Environment variables for local overrides:
- `DB_HOST`, `DB_USER`, `DB_PASSWORD` (default: localhost, postgres, postgres)
- `REDIS_HOST`, `REDIS_PORT` (default: localhost, 6379)
- `OPENSEARCH_HOST`, `OPENSEARCH_PORT`, `OPENSEARCH_USER`, `OPENSEARCH_PASSWORD`
- `DEDUPLICACAO_SERVICE_URL` (for batch processor, default: http://localhost:8080)

### Health Checks
- Service: `http://localhost:8080/actuator/health`
- Batch: `http://localhost:8081/actuator/health`
- Metrics: `http://localhost:8080/actuator/prometheus`

## Key Dependencies & Versions

| Component | Version | Purpose |
|-----------|---------|---------|
| Java | 21 | Language/runtime |
| Spring Boot | 3.2.0 | Application framework |
| Spring Batch | 5.1.0 | Batch job framework |
| PostgreSQL Driver | 42.7.1 | Database |
| Redis/Redisson | 3.25.0 | Cache & distributed locks |
| OpenSearch | 2.11.0 | Search & indexing |
| Resilience4j | 2.1.0 | Circuit breakers, retries |
| Lombok | 1.18.30 | Annotation processor for boilerplate |
| MapStruct | 1.5.5.Final | DTO mapping |

## CI/CD Pipeline (GitLab)

The `.gitlab-ci.yml` defines a comprehensive pipeline with stages:
1. **build** - Compiles all modules
2. **test** - Unit and integration tests with coverage
3. **sonar** - Code quality analysis
4. **package** - JAR packaging
5. **docker-build** - Docker image build and ECR push
6. **deploy-staging** - Kubernetes deployment to staging
7. **integration-test** - Full system integration tests
8. **deploy-production** - Kubernetes deployment to production

Pipeline runs on: merge requests, main branch, develop branch, and tags.

Maven cache is leveraged (`~/.m2/repository`) to speed up builds.

## Kubernetes Deployment

Manifests located in `k8s/`:
- `base/` - Base Kustomize configurations
- `overlays/` - Environment-specific overlays (staging, production)

Deploy with: `kustomize build k8s/overlays/production | kubectl apply -f -`

## Docker Images

Both services use multi-stage Docker builds with Alpine base images:
- Base: `eclipse-temurin:21-jre-alpine`
- User: Non-root `spring` user for security
- JVM Options: ZGC garbage collector, 75% RAM limit
- Health checks: HTTP probe to `/actuator/health`

## Configuration & Environment

### Service (deduplicacao-service)

**application.yml** - PostgreSQL connection, Redis cache configuration, JPA settings
```yaml
server.port: 8080
spring.datasource: deduplicacao database
spring.cache.type: redis
```

**Actuator endpoints exposed**: health, info, metrics, prometheus

### Batch Processor (batch-processor)

**application.yml** - Separate batch metadata database, OpenSearch connection, service URL
```yaml
server.port: 8081
spring.datasource: batch_metadata database (separate from app data)
batch.chunk-size: 100
batch.partition.grid-size: 10 (configurable via env)
```

Batch jobs are **disabled by default** (`spring.batch.job.enabled: false`); triggered externally via API or scheduling.

## Important Implementation Notes

1. **Database Schema Management**: DDL must be pre-applied; Hibernate is set to `validate` mode only. Migrations should be managed externally (Flyway/Liquibase if needed).

2. **Distributed Locking**: Redis locks via Redisson prevent concurrent batch job execution across multiple instances.

3. **Resilience**: Resilience4j provides circuit breakers and retry policies for external integrations (DETRAN, MES, ABIS).

4. **Partition Strategy**: Batch processor uses Spring Batch's partitioned step reader for parallel processing across configurable grid size.

5. **Metrics**: Both services expose Prometheus metrics; critical for monitoring in production.

6. **Integration Points**: Service integrates with external biometric systems in the `integration/` package:
   - DETRAN integration
   - MES (?) integration
   - ABIS integration

## Code Structure Conventions

- **Packages**: Follow `bio.prodesp.deduplicacao.*` naming scheme
- **Controllers**: HTTP request handling (`service/controller`)
- **Services**: Business logic (`service/service`)
- **Repositories**: Data access (`service/repository`)
- **DTOs**: Request/response models (`model/request`, `model/response`)
- **Domain**: Entity models in commons (`commons/model/domain`)

## Debugging Tips

- Enable DEBUG logging for package: Set `logging.level.br.gov.sp.prodesp: DEBUG` in application.yml
- View Spring Boot startup info with `-Dinfo.app.encoding=@project.build.sourceEncoding@`
- Use Spring Boot DevTools for faster restart during development (add to pom.xml if needed)
- Check redis-cli for cache state: `redis-cli KEYS '*'`
