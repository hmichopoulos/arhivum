#!/bin/bash
# Post-create script for Codespaces/devcontainer setup
# Builds the project and sets up the development environment

set -e

echo "=== Archivum Development Environment Setup ==="

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
