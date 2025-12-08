#!/bin/bash
# Post-create script for Codespaces/devcontainer setup
# Builds the project and sets up the development environment

set -e

echo "=== Archivum Development Environment Setup ==="

# Start PostgreSQL container
echo "Starting PostgreSQL..."
docker run -d --name archivum-db \
  -e POSTGRES_DB=archivum \
  -e POSTGRES_USER=archivum \
  -e POSTGRES_PASSWORD=archivum \
  -p 5432:5432 \
  --restart unless-stopped \
  postgres:16

# Wait for PostgreSQL to be ready
echo "Waiting for PostgreSQL to be ready..."
for i in {1..30}; do
  if docker exec archivum-db pg_isready -U archivum -d archivum > /dev/null 2>&1; then
    echo "PostgreSQL is ready!"
    break
  fi
  sleep 1
done

# Build all Gradle projects
echo "Building Gradle projects..."
./gradlew build -x test

# Install the scanner CLI
echo "Installing archivum-scanner CLI..."
./gradlew :archivum-scanner:installDist

# Add CLI to PATH by creating a symlink
CLI_PATH="$PWD/archivum-scanner/build/install/archivum-scanner/bin/archivum-scanner"
if [ -f "$CLI_PATH" ]; then
    sudo ln -sf "$CLI_PATH" /usr/local/bin/archivum-scanner
    echo "CLI installed at: /usr/local/bin/archivum-scanner"
fi

# Install UI dependencies
echo "Installing UI dependencies..."
cd archivum-ui
npm install
cd ..

# Setup test fixtures
echo "Setting up test fixtures..."
bash scripts/setup-test-fixtures.sh

echo "=== Setup Complete ==="
echo ""
echo "Available commands:"
echo "  archivum-scanner scan <path>  - Scan a directory"
echo "  archivum-scanner upload <dir> - Upload scan results"
echo ""
echo "Start services with:"
echo "  Backend: ./gradlew :archivum-server:bootRun"
echo "  UI:      cd archivum-ui && npm run dev"
echo ""
echo "Test fixtures available at: ~/test-fixtures/"
