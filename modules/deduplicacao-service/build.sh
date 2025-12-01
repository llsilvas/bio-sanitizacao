#!/bin/bash

# Build script for deduplicacao-service Docker image
# This script ensures the JAR is built before creating the Docker image

set -e  # Exit on any error

echo "=========================================="
echo "Building deduplicacao-service Docker Image"
echo "=========================================="

# Step 1: Build JAR with Maven
echo ""
echo "[1/2] Building JAR with Maven..."
mvn clean package -DskipTests -f ../../pom.xml -pl modules/deduplicacao-service -am

# Step 2: Build Docker image
echo ""
echo "[2/2] Building Docker image..."
docker build -t deduplicacao-service:latest .

echo ""
echo "=========================================="
echo "✅ Build completed successfully!"
echo "=========================================="
echo ""
echo "Image: deduplicacao-service:latest"
echo ""
echo "To run the container:"
echo "  docker run -p 8080:8080 deduplicacao-service:latest"
echo ""
echo "Or use docker-compose:"
echo "  cd ../../docker && docker-compose up deduplicacao-service"
echo ""
