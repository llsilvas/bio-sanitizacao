FROM timon.cloud.prodesp.sp.gov.br/library/maven:3.9.6-eclipse-temurin-21-alpine as builder

# Definir a variável JAVA_HOME
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
# Define o diretório de trabalho

WORKDIR /app

# Copia o pom.xml e baixa as dependências
COPY pom.xml .
COPY settings.xml .
COPY .git .git
RUN mvn dependency:go-offline -gs settings.xml

# Copia o código-fonte
COPY src ./src

# Realiza o build do projeto, usando o profile informado
RUN mvn clean package -U -gs settings.xml -DskipTests

# Identifica o JAR gerado e extrai as camadas
RUN mkdir -p /tmp/extract && \
  JAR_FILE=$(ls target/*.jar | head -n 1) && \
  java -Djarmode=tools -jar "$JAR_FILE" extract --layers --launcher --destination /tmp/extract

RUN ls -la /tmp/extract

# Estágio 2: Imagem final para execução
FROM timon.cloud.prodesp.sp.gov.br/library/maven:3.9.6-eclipse-temurin-21-alpine as final

# Instala o pacote shadow para permitir o uso de groupadd e useradd
RUN apk add --no-cache shadow

# Cria o grupo e o usuário 'spring'
RUN groupadd -r spring && \
  useradd -r -g spring spring

WORKDIR /app

# Copia o artefato construído no estágio anterior
COPY --from=builder --chown=spring:spring /app/target/*.jar app.jar

# Copia as camadas extraídas da pasta temporária
COPY --from=builder --chown=spring:spring /tmp/extract/dependencies/ ./dependencies/
COPY --from=builder --chown=spring:spring /tmp/extract/spring-boot-loader/ ./spring-boot-loader/
COPY --from=builder --chown=spring:spring /tmp/extract/snapshot-dependencies/ ./snapshot-dependencies/
COPY --from=builder --chown=spring:spring /tmp/extract/application/ ./application/

# Copia o arquivo git.properties para a imagem final
COPY --from=builder --chown=spring:spring /app/target/classes/git.properties /app/git.properties

# Exibe o conteúdo do git.properties (será mostrado nos logs do build)
RUN echo "Conteúdo do git.properties:" && cat /app/git.properties

RUN apk add --no-cache bind-tools && sh


ENV SPRING_PROFILES_ACTIVE=""

# Definir variáveis de ambiente
ENV JAVA_OPTS=""

ENV BIOMETRIA_ELASTIC_URL=""
ENV BIOMETRIA_ELASTIC_USERNAME=""
ENV BIOMETRIA_ELASTIC_PASSWORD=""
ENV BIOMETRIA_OAUTH_RESOURCE_SERVER_JWT_ISSUER_URI=""
ENV BIOMETRIA_OAUTH_RESOURCE_SERVER_JWT_JWK_SET_URI=""
ENV BIOMETRIA_OAUTH_CLIENT_PROVIDER_TOKEN_URI=""

# Muda para o usuário 'spring' (não-root)
USER spring:spring

# Expor a porta
EXPOSE 8081

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.profiles.active=${SPRING_PROFILES_ACTIVE}"]
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY target/*.jar app.jar

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseZGC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", \
    "app.jar"]
