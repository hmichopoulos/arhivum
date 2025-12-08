FROM mcr.microsoft.com/devcontainers/base:ubuntu

# Install dependencies for downloading and extracting test fixtures
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    unzip \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Create directory for test fixtures
RUN mkdir -p /home/vscode/test-fixtures && chown vscode:vscode /home/vscode/test-fixtures
