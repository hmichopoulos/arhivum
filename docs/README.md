# Archivum Documentation

This directory contains all project documentation, organized by component.

## Structure

```
docs/
├── README.md                    # This file
├── PROJECT.md                   # High-level project overview
├── development/                 # Development guides
│   └── codespaces-setup.md
├── scanner/                     # Scanner-specific documentation
│   ├── README.md
│   ├── REQUIREMENTS.md
│   ├── DESIGN.md
│   └── SCANNER_AGENT.md
├── server/                      # Server-specific documentation
│   ├── README.md
│   ├── DATABASE.md
│   └── API.md
└── shared/                      # Shared concepts and architecture
    ├── README.md
    ├── OVERVIEW.md
    ├── FOLDER_STRUCTURE.md
    └── DEDUPLICATION.md
```

## Quick Links

### Getting Started
- [Project Overview](PROJECT.md) - High-level goals and approach
- [System Architecture](shared/OVERVIEW.md) - Component diagram and data flow

### Scanner
- [Scanner Requirements](scanner/REQUIREMENTS.md) - What the scanner needs to do
- [Scanner Design](scanner/DESIGN.md) - How it's built
- [Scanner Agent Spec](scanner/SCANNER_AGENT.md) - Detailed behavior

### Server
- [Database Schema](server/DATABASE.md) - PostgreSQL tables and relationships
- [API Documentation](server/API.md) - REST endpoints and WebSocket events

### Shared Concepts
- [Folder Structure](shared/FOLDER_STRUCTURE.md) - Target archive taxonomy
- [Deduplication](shared/DEDUPLICATION.md) - How duplicates are detected

### Development
- [CLAUDE.md](../CLAUDE.md) - Coding conventions and development guide
- [Codespaces Setup](development/codespaces-setup.md) - GitHub Codespaces development guide
