#!/bin/bash

# Script para build do projeto usando Java 21
# O projeto requer Java 21, conforme configurado no pom.xml

JAVA_21_HOME="/home/lsilva/.sdkman/candidates/java/21.0.7-tem"

if [ ! -d "$JAVA_21_HOME" ]; then
    echo "ERROR: Java 21 not found at $JAVA_21_HOME"
    echo "Please install Java 21 using: sdk install java 21.0.7-tem"
    exit 1
fi

echo "Using Java 21: $JAVA_21_HOME"
export JAVA_HOME="$JAVA_21_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

java -version

# Execute o comando Maven com os argumentos passados
if [ $# -eq 0 ]; then
    # Sem argumentos, executa build completo
    mvn clean package -DskipTests
else
    # Com argumentos, executa o comando especificado
    mvn "$@"
fi