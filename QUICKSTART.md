# Archivum - Quick Start Guide

## One-Time Setup

### 1. Prerequisites
```bash
# Check versions
java --version   # Should be 21+
node --version   # Should be 20+
docker --version # For PostgreSQL
```

### 2. Install Frontend Dependencies
```bash
cd archivum-ui
npm install
```

## Running the Application

### Step 1: Start PostgreSQL (Docker)
```bash
docker run -d --name archivum-db \
  -e POSTGRES_DB=archivum \
  -e POSTGRES_USER=archivum \
  -e POSTGRES_PASSWORD=archivum \
  -p 5432:5432 \
  postgres:16
```

**Check it's running:**
```bash
docker ps | grep archivum-db
```

### Step 2: Start Backend Server
Open a **new terminal**:
```bash
cd /home/haris/code/arhivum
./gradlew :archivum-server:bootRun
```

**Wait for:** `Started ArchivumServerApplication in X seconds`

### Step 3: Start Frontend UI
Open a **new terminal**:
```bash
cd /home/haris/code/arhivum/archivum-ui
npm run dev
```

**Wait for:** `Local: http://localhost:3000/`

### Step 4: Open Browser
```
http://localhost:3000
```

## Using the Scanner

### Scan a Directory
```bash
cd /home/haris/code/arhivum
./archivum-scanner/build/install/archivum-scanner/bin/archivum-scanner scan /path/to/directory
```

### Upload Previous Scan Results

**Upload one scan:**
```bash
./archivum-scanner/build/install/archivum-scanner/bin/archivum-scanner upload \
  ./archivum-scanner/output/[scan-id]
```

**Upload all scans:**
```bash
for dir in ./archivum-scanner/output/*/; do
  if [ -f "$dir/source.json" ]; then
    echo "Uploading: $dir"
    ./archivum-scanner/build/install/archivum-scanner/bin/archivum-scanner upload "$dir"
  fi
done
```

## Accessing the Application

| Service | URL | Purpose |
|---------|-----|---------|
| **Web UI** | http://localhost:3000 | Browse sources and files |
| **Backend API** | http://localhost:8080/api | REST API endpoints |
| **PostgreSQL** | localhost:5432 | Database (user: archivum) |

### Web UI Pages

- **Sources List**: http://localhost:3000/sources
  - View all scanned disks
  - Filter by status, type
  - See statistics

- **Source Details**: Click any source card
  - Browse all files in that source
  - Filter by extension or duplicates
  - Sort by name, size, date

- **Code Projects**: http://localhost:3000/code-projects
  - View detected code projects
  - See duplicates and statistics

## Stopping the Application

```bash
# Stop frontend: Ctrl+C in frontend terminal
# Stop backend: Ctrl+C in backend terminal
# Stop PostgreSQL:
docker stop archivum-db

# Restart PostgreSQL later:
docker start archivum-db
```

## Troubleshooting

### PostgreSQL not connecting
```bash
# Check if running:
docker ps | grep archivum-db

# If not running, start it:
docker start archivum-db

# If doesn't exist, create it (see Step 1)
```

### Backend fails to start
```bash
# Check PostgreSQL is running first
docker ps | grep archivum-db

# Run from project ROOT, not archivum-server directory
cd /home/haris/code/arhivum
./gradlew :archivum-server:bootRun
```

### Frontend port 3000 already in use
Edit `archivum-ui/vite.config.ts` and change port to 3001 or another port.

### No sources showing in UI
You need to either:
1. Upload previous scan results (see "Upload Previous Scan Results" above)
2. Run a new scan (see "Scan a Directory" above)

Then **refresh the browser**.

## Database Management

### Connect to PostgreSQL
```bash
docker exec -it archivum-db psql -U archivum -d archivum
```

### Useful SQL Queries
```sql
-- List all sources
SELECT id, name, type, status, processed_files, total_files FROM source;

-- Count files by source
SELECT s.name, COUNT(f.id) as file_count
FROM source s
LEFT JOIN scanned_file f ON f.source_id = s.id
GROUP BY s.id, s.name;

-- List duplicates
SELECT * FROM scanned_file WHERE is_duplicate = true LIMIT 10;

-- Exit psql
\q
```

## Next Steps

1. ✅ Scan your first directory
2. ✅ Upload the results to the server
3. ✅ Browse files in the web UI
4. 🔲 Implement deduplication logic
5. 🔲 Add file classification
6. 🔲 Create migration workflows

## Getting Help

- Check `CLAUDE.md` for detailed documentation
- Check `archivum-ui/README.md` for frontend-specific docs
- Check logs in the terminal where backend is running
