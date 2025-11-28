# Server API Documentation

This document describes the REST API endpoints provided by archivum-server. The scanner will use these endpoints (post-MVP) to communicate scan results.

## Base URL

```
https://archivum.local:8080/api
```

## Authentication

All requests require API key authentication via header:

```
X-API-Key: your-api-key-here
```

## Scanner Endpoints

### Create Source

Register a new source before scanning.

```
POST /api/sources
```

**Request Body:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Dad-Laptop-2018",
  "type": "PARTITION",
  "rootPath": "/dev/sdb1",
  "physicalId": {
    "diskUuid": "abc-123-def",
    "partitionUuid": "def-456-ghi",
    "volumeLabel": "BACKUP_2018",
    "serialNumber": "WD-WCC4E123456",
    "mountPoint": "/media/haris/BACKUP_2018",
    "filesystemType": "ext4",
    "capacity": 4000000000000,
    "usedSpace": 2500000000000,
    "physicalLabel": "WD #17",
    "notes": "Dad's old laptop, full backup"
  },
  "parentSourceId": null,
  "postponed": false,
  "notes": "Full backup from 2018"
}
```

**Response:** `201 Created`
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "success": true,
  "message": "Source created successfully"
}
```

**Errors:**
- `400 Bad Request` - Invalid request body
- `409 Conflict` - Source with same ID already exists
- `401 Unauthorized` - Invalid API key

---

### Check Hashes

Check if hashes already exist in the system (for duplicate optimization).

```
POST /api/hashes/check
```

**Request Body:**
```json
{
  "hashes": [
    "a1b2c3d4e5f6...",
    "b2c3d4e5f6a1...",
    "c3d4e5f6a1b2..."
  ]
}
```

**Response:** `200 OK`
```json
{
  "results": {
    "a1b2c3d4e5f6...": true,   // exists
    "b2c3d4e5f6a1...": false,  // new
    "c3d4e5f6a1b2...": true    // exists
  }
}
```

**Use Case:**

Scanner batches hashes (e.g., every 100 files) and checks which ones exist. For existing hashes, scanner skips EXIF extraction.

---

### Upload File Batch

Send batch of scanned files to server.

```
POST /api/sources/{sourceId}/files
```

**Path Parameters:**
- `sourceId` - UUID of the source

**Request Body:**
```json
{
  "sourceId": "550e8400-e29b-41d4-a716-446655440000",
  "batchNumber": 1,
  "files": [
    {
      "id": "660e8400-e29b-41d4-a716-446655440001",
      "sourceId": "550e8400-e29b-41d4-a716-446655440000",
      "path": "/photos/2018/IMG_1234.jpg",
      "name": "IMG_1234.jpg",
      "extension": "jpg",
      "size": 2457600,
      "sha256": "a1b2c3d4e5f6...",
      "modifiedAt": "2018-06-15T14:30:00Z",
      "createdAt": "2018-06-15T14:30:00Z",
      "accessedAt": "2025-11-28T10:15:00Z",
      "mimeType": "image/jpeg",
      "exif": {
        "cameraMake": "Apple",
        "cameraModel": "iPhone 8",
        "dateTimeOriginal": "2018-06-15T14:30:00Z",
        "width": 4032,
        "height": 3024,
        "orientation": 1,
        "gps": {
          "latitude": 37.7749,
          "longitude": -122.4194,
          "altitude": 15.0
        },
        "iso": 20,
        "aperture": 1.8
      },
      "status": "HASHED",
      "isDuplicate": false,
      "scannedAt": "2025-11-28T10:15:23Z"
    }
    // ... more files
  ]
}
```

**Response:** `201 Created`
```json
{
  "success": true,
  "filesReceived": 1000,
  "duplicatesDetected": 15,
  "message": "Batch processed successfully"
}
```

**Server Actions:**
1. Store file records in database
2. Detect duplicates by hash
3. Update source statistics
4. Trigger background jobs (deduplication analysis)

**Errors:**
- `400 Bad Request` - Invalid request body
- `404 Not Found` - Source not found
- `401 Unauthorized` - Invalid API key

---

### Complete Scan

Mark scan as completed.

```
PATCH /api/sources/{sourceId}/complete
```

**Path Parameters:**
- `sourceId` - UUID of the source

**Request Body:**
```json
{
  "sourceId": "550e8400-e29b-41d4-a716-446655440000",
  "totalFiles": 15432,
  "totalSize": 2500000000000,
  "success": true,
  "errorMessage": null
}
```

**Response:** `200 OK`
```json
{
  "success": true,
  "message": "Scan completed successfully"
}
```

**Server Actions:**
1. Update source status to COMPLETED
2. Update statistics
3. Trigger deduplication analysis
4. Send WebSocket notification to UI

**Errors:**
- `404 Not Found` - Source not found
- `400 Bad Request` - Invalid request body

---

### Get Source Status

Get status of a source.

```
GET /api/sources/{sourceId}/status
```

**Path Parameters:**
- `sourceId` - UUID of the source

**Response:** `200 OK`
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Dad-Laptop-2018",
  "type": "PARTITION",
  "status": "SCANNING",
  "totalFiles": 15432,
  "processedFiles": 10000,
  "totalSize": 2500000000000,
  "processedSize": 1500000000000,
  "scanStartedAt": "2025-11-28T10:00:00Z",
  "estimatedCompletionAt": "2025-11-28T12:30:00Z"
}
```

**Use Case:**

Scanner can poll this endpoint to check if scan is still valid or if server has requested pause/cancel.

---

## Source Management Endpoints

### List Sources

Get all sources.

```
GET /api/sources
```

**Query Parameters:**
- `type` - Filter by source type (optional)
- `status` - Filter by status (optional)
- `page` - Page number (default: 0)
- `size` - Page size (default: 20)

**Response:** `200 OK`
```json
{
  "content": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "Dad-Laptop-2018",
      "type": "PARTITION",
      "status": "COMPLETED",
      "totalFiles": 15432,
      "totalSize": 2500000000000,
      "scanCompletedAt": "2025-11-28T12:30:00Z"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 20
}
```

---

### Get Source Details

Get detailed information about a source.

```
GET /api/sources/{sourceId}
```

**Response:** `200 OK`
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Dad-Laptop-2018",
  "type": "PARTITION",
  "rootPath": "/dev/sdb1",
  "physicalId": { /* ... */ },
  "parentSourceId": null,
  "childSourceIds": [],
  "status": "COMPLETED",
  "totalFiles": 15432,
  "totalSize": 2500000000000,
  "scanStartedAt": "2025-11-28T10:00:00Z",
  "scanCompletedAt": "2025-11-28T12:30:00Z",
  "createdAt": "2025-11-28T10:00:00Z"
}
```

---

### Get Source Hierarchy

Get source hierarchy tree.

```
GET /api/sources/{sourceId}/hierarchy
```

**Response:** `200 OK`
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "WD-4TB-Black",
  "type": "DISK",
  "children": [
    {
      "id": "660e8400-e29b-41d4-a716-446655440001",
      "name": "Partition-1",
      "type": "PARTITION",
      "children": [
        {
          "id": "770e8400-e29b-41d4-a716-446655440002",
          "name": "photos-2017.tar.gz",
          "type": "ARCHIVE_TAR",
          "postponed": true,
          "children": []
        }
      ]
    }
  ]
}
```

---

## File Query Endpoints

### Query Files

Search for files with filters.

```
GET /api/files
```

**Query Parameters:**
- `sourceId` - Filter by source (optional)
- `hash` - Filter by hash (optional)
- `extension` - Filter by extension (optional)
- `minSize` - Minimum file size (optional)
- `maxSize` - Maximum file size (optional)
- `hasExif` - Filter by EXIF presence (optional)
- `page` - Page number (default: 0)
- `size` - Page size (default: 20)

**Response:** `200 OK`
```json
{
  "content": [
    {
      "id": "660e8400-e29b-41d4-a716-446655440001",
      "sourceId": "550e8400-e29b-41d4-a716-446655440000",
      "path": "/photos/2018/IMG_1234.jpg",
      "name": "IMG_1234.jpg",
      "size": 2457600,
      "sha256": "a1b2c3d4e5f6...",
      "modifiedAt": "2018-06-15T14:30:00Z",
      "isDuplicate": false,
      "scannedAt": "2025-11-28T10:15:23Z"
    }
  ],
  "totalElements": 15432,
  "totalPages": 772,
  "number": 0,
  "size": 20
}
```

---

### Get File Details

Get detailed information about a file.

```
GET /api/files/{fileId}
```

**Response:** `200 OK`
```json
{
  "id": "660e8400-e29b-41d4-a716-446655440001",
  "sourceId": "550e8400-e29b-41d4-a716-446655440000",
  "path": "/photos/2018/IMG_1234.jpg",
  "name": "IMG_1234.jpg",
  "extension": "jpg",
  "size": 2457600,
  "sha256": "a1b2c3d4e5f6...",
  "modifiedAt": "2018-06-15T14:30:00Z",
  "createdAt": "2018-06-15T14:30:00Z",
  "mimeType": "image/jpeg",
  "exif": {
    "cameraMake": "Apple",
    "cameraModel": "iPhone 8",
    "dateTimeOriginal": "2018-06-15T14:30:00Z",
    "width": 4032,
    "height": 3024,
    "orientation": 1,
    "gps": {
      "latitude": 37.7749,
      "longitude": -122.4194,
      "altitude": 15.0
    }
  },
  "status": "ANALYZED",
  "isDuplicate": false,
  "scannedAt": "2025-11-28T10:15:23Z"
}
```

---

## WebSocket Endpoints

### Scan Progress

Real-time scan progress updates.

```
WebSocket: ws://archivum.local:8080/ws/scan-progress/{sourceId}
```

**Message Format:**
```json
{
  "sourceId": "550e8400-e29b-41d4-a716-446655440000",
  "processedFiles": 10000,
  "totalFiles": 15432,
  "processedSize": 1500000000000,
  "totalSize": 2500000000000,
  "currentFile": "/photos/2018/IMG_5678.jpg",
  "timestamp": "2025-11-28T11:15:23Z"
}
```

**Client Usage:**

Scanner can send progress updates via WebSocket instead of waiting for batch completion.

---

## Error Responses

All endpoints may return these error responses:

**400 Bad Request**
```json
{
  "error": "Bad Request",
  "message": "Invalid request body",
  "details": [
    "Field 'name' is required",
    "Field 'type' must be one of: DISK, PARTITION, ..."
  ],
  "timestamp": "2025-11-28T10:00:00Z"
}
```

**401 Unauthorized**
```json
{
  "error": "Unauthorized",
  "message": "Invalid or missing API key",
  "timestamp": "2025-11-28T10:00:00Z"
}
```

**404 Not Found**
```json
{
  "error": "Not Found",
  "message": "Source not found: 550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2025-11-28T10:00:00Z"
}
```

**500 Internal Server Error**
```json
{
  "error": "Internal Server Error",
  "message": "An unexpected error occurred",
  "timestamp": "2025-11-28T10:00:00Z"
}
```

---

## Rate Limiting

API rate limits (per API key):

- **Source creation:** 10 requests/minute
- **Hash checks:** 100 requests/minute
- **File batch uploads:** 1000 requests/hour
- **Query endpoints:** 100 requests/minute

**Rate Limit Response:** `429 Too Many Requests`
```json
{
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Try again in 60 seconds.",
  "retryAfter": 60,
  "timestamp": "2025-11-28T10:00:00Z"
}
```

---

## MVP Implementation Status

For MVP (dry-run mode), these endpoints are **not yet implemented**. Scanner writes to local JSON files instead.

**Post-MVP:**
1. Implement server endpoints
2. Update scanner `OutputService` to use `ApiClientService`
3. Add `--server` flag to enable server communication
4. Keep `--dry-run` flag for local-only operation

**Migration Path:**

```java
// MVP
OutputService output = new JsonOutput(outputDir);

// Post-MVP
OutputService output = config.isDryRun()
    ? new JsonOutput(outputDir)
    : new ApiClientService(serverUrl, apiKey);
```
