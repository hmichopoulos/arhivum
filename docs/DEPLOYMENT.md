# Archivum Deployment Guide

Complete guide for deploying Archivum on a dedicated Linux machine.

## Table of Contents

- [Hardware Requirements](#hardware-requirements)
- [Software Prerequisites](#software-prerequisites)
- [Installation Steps](#installation-steps)
- [PostgreSQL Setup](#postgresql-setup)
- [Backend Deployment](#backend-deployment)
- [Frontend Deployment](#frontend-deployment)
- [Scanner Setup](#scanner-setup)
- [Systemd Services](#systemd-services)
- [Performance Tuning](#performance-tuning)
- [Monitoring](#monitoring)

---

## Hardware Requirements

### Recommended Specs for 80TB Dataset

**For a dedicated Linux machine scanning ~20 × 4TB external HDDs:**

| Component | Minimum | Recommended | Notes |
|-----------|---------|-------------|-------|
| **CPU** | 4 cores (Intel i5/Ryzen 5) | 8+ cores (Intel i7/Ryzen 7) | Multi-core helps with parallel SHA-256 hashing |
| **RAM** | 16GB | 32GB | PostgreSQL caching + Spring Boot + OS |
| **Storage** | 256GB SSD | 512GB NVMe SSD | OS + PostgreSQL database (~50GB) + logs |
| **Network** | 1Gbps | 10Gbps | To Synology NAS for staging/migration |
| **USB Ports** | 4+ USB 3.0 | 8+ USB 3.0/3.1 | For external HDDs (or use USB hubs) |
| **OS** | Ubuntu 22.04 LTS | Ubuntu 22.04 LTS | Or Debian 12+ |

### Storage Breakdown

- **OS**: 20-30GB
- **PostgreSQL database**: 25-50GB (for 16M files)
- **Application**: 1-2GB (JARs, Node modules)
- **Logs**: 10-20GB (rotated)
- **Temp/staging**: 50-100GB (for scanner output batches)

**Total**: ~100-200GB (500GB SSD recommended for safety)

### Database Size Estimation

With **80TB of files**:
- Average file size: 5MB → ~16 million files
- Metadata per file: 1-2KB
- **Files table**: ~30GB (with indexes)
- **Code projects**: ~100MB
- **Duplicate groups**: 1-5GB
- **Folder zones**: <100MB

**Total database size**: ~35-50GB

---

## Software Prerequisites

### Required Software

1. **Operating System**: Ubuntu 22.04 LTS (recommended)
2. **Java**: OpenJDK 21
3. **PostgreSQL**: 16.x
4. **Node.js**: 20.x LTS (for UI)
5. **Git**: For cloning repository
6. **Nginx**: For serving UI and reverse proxy (optional but recommended)

---

## Installation Steps

### 1. System Update

```bash
sudo apt update && sudo apt upgrade -y
```

### 2. Install Java 21

```bash
# Add OpenJDK repository
sudo apt install -y openjdk-21-jdk

# Verify installation
java -version
# Output should show: openjdk version "21.x.x"
```

### 3. Install PostgreSQL 16

```bash
# Add PostgreSQL repository
sudo apt install -y wget
sudo sh -c 'echo "deb http://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" > /etc/apt/sources.list.d/pgdg.list'
wget --quiet -O - https://www.postgresql.org/media/keys/ACCC4CF8.asc | sudo apt-key add -

# Install PostgreSQL
sudo apt update
sudo apt install -y postgresql-16 postgresql-contrib-16

# Verify installation
sudo systemctl status postgresql
```

### 4. Install Node.js 20

```bash
# Install Node.js 20 via nvm (recommended)
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.0/install.sh | bash
source ~/.bashrc

nvm install 20
nvm use 20
nvm alias default 20

# Verify installation
node -v  # Should show v20.x.x
npm -v   # Should show 10.x.x
```

### 5. Install Nginx (Optional)

```bash
sudo apt install -y nginx

# Enable and start
sudo systemctl enable nginx
sudo systemctl start nginx
```

### 6. Clone Archivum Repository

```bash
# Create application directory
sudo mkdir -p /opt/archivum
sudo chown $USER:$USER /opt/archivum

# Clone repository
cd /opt/archivum
git clone https://github.com/hmichopoulos/arhivum.git .

# Or if already cloned elsewhere, copy it
# cp -r ~/code/arhivum /opt/archivum
```

---

## PostgreSQL Setup

### 1. Create Database and User

```bash
# Switch to postgres user
sudo -u postgres psql

# In psql:
CREATE DATABASE archivum;
CREATE USER archivum WITH ENCRYPTED PASSWORD 'your_secure_password_here';
GRANT ALL PRIVILEGES ON DATABASE archivum TO archivum;

# PostgreSQL 15+ requires additional grants
\c archivum
GRANT ALL ON SCHEMA public TO archivum;

\q
```

### 2. Configure PostgreSQL for Performance

Edit `/etc/postgresql/16/main/postgresql.conf`:

```bash
sudo nano /etc/postgresql/16/main/postgresql.conf
```

**Recommended settings for 32GB RAM system:**

```conf
# Memory Settings
shared_buffers = 8GB              # 25% of total RAM
effective_cache_size = 24GB       # 75% of total RAM
work_mem = 64MB                   # For sorting/hashing operations
maintenance_work_mem = 2GB        # For VACUUM, CREATE INDEX

# Checkpoint Settings (reduce I/O spikes)
checkpoint_completion_target = 0.9
wal_buffers = 16MB
max_wal_size = 4GB
min_wal_size = 1GB

# Query Planner
random_page_cost = 1.1           # For SSD (default is 4.0 for HDD)
effective_io_concurrency = 200   # For SSD

# Connection Settings
max_connections = 100            # Default is fine for single server

# Logging (useful for debugging)
log_min_duration_statement = 1000  # Log queries taking > 1s
log_line_prefix = '%t [%p]: '
log_timezone = 'UTC'
```

**For 16GB RAM system, adjust:**
- `shared_buffers = 4GB`
- `effective_cache_size = 12GB`
- `maintenance_work_mem = 1GB`

### 3. Allow Local Connections

Edit `/etc/postgresql/16/main/pg_hba.conf`:

```bash
sudo nano /etc/postgresql/16/main/pg_hba.conf
```

Add/modify:

```conf
# TYPE  DATABASE        USER            ADDRESS                 METHOD
local   archivum        archivum                                md5
host    archivum        archivum        127.0.0.1/32            md5
host    archivum        archivum        ::1/128                 md5
```

### 4. Restart PostgreSQL

```bash
sudo systemctl restart postgresql

# Verify connection
psql -U archivum -d archivum -h localhost
# Enter password when prompted
# \q to exit
```

---

## Backend Deployment

### 1. Build the Backend

```bash
cd /opt/archivum

# Build the server (includes running tests)
./gradlew :archivum-server:build

# Or skip tests for faster build
./gradlew :archivum-server:build -x test
```

### 2. Configure Application

Create `/opt/archivum/archivum-server/src/main/resources/application-prod.properties`:

```properties
# Database Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/archivum
spring.datasource.username=archivum
spring.datasource.password=your_secure_password_here

# Flyway migrations
spring.flyway.enabled=true

# JPA/Hibernate
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.format_sql=false

# Server Configuration
server.port=8080
server.address=0.0.0.0

# Logging
logging.level.tech.zaisys.archivum=INFO
logging.level.org.springframework.web=WARN
logging.file.name=/var/log/archivum/archivum-server.log
logging.file.max-size=100MB
logging.file.max-history=10

# File Upload (if needed)
spring.servlet.multipart.max-file-size=10GB
spring.servlet.multipart.max-request-size=10GB
```

### 3. Create Log Directory

```bash
sudo mkdir -p /var/log/archivum
sudo chown $USER:$USER /var/log/archivum
```

### 4. Run Backend (Manual Test)

```bash
cd /opt/archivum

# Run with production profile
./gradlew :archivum-server:bootRun --args='--spring.profiles.active=prod'

# Or run the JAR directly
java -jar archivum-server/build/libs/archivum-server-*.jar --spring.profiles.active=prod
```

Test it works:

```bash
curl http://localhost:8080/actuator/health
# Should return: {"status":"UP"}
```

---

## Frontend Deployment

### 1. Build the Frontend

```bash
cd /opt/archivum/archivum-ui

# Install dependencies
npm install

# Build for production
npm run build
```

This creates an optimized build in `archivum-ui/dist/`.

### 2. Option A: Serve with Nginx (Recommended)

Create `/etc/nginx/sites-available/archivum`:

```nginx
server {
    listen 80;
    server_name archivum.local;  # Change to your hostname/IP

    # Serve React app
    root /opt/archivum/archivum-ui/dist;
    index index.html;

    # React Router: serve index.html for all routes
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Proxy API requests to backend
    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_cache_bypass $http_upgrade;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # Increase timeout for long operations
        proxy_read_timeout 300s;
        proxy_connect_timeout 300s;
    }

    # WebSocket support (if needed)
    location /ws/ {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }

    # Gzip compression
    gzip on;
    gzip_types text/css application/javascript application/json;
    gzip_min_length 1000;
}
```

Enable the site:

```bash
sudo ln -s /etc/nginx/sites-available/archivum /etc/nginx/sites-enabled/
sudo nginx -t  # Test configuration
sudo systemctl reload nginx
```

### 2. Option B: Serve with Node.js (Development)

```bash
cd /opt/archivum/archivum-ui
npm run dev -- --host 0.0.0.0 --port 3000
```

Access at: `http://server-ip:3000`

---

## Scanner Setup

### 1. Build Scanner

```bash
cd /opt/archivum

# Build scanner distribution
./gradlew :archivum-scanner:installDist

# Scanner binary will be at:
# archivum-scanner/build/install/archivum-scanner/bin/archivum-scanner
```

### 2. Create Scanner Alias

Add to `~/.bashrc` or `~/.zshrc`:

```bash
alias archivum-scan='/opt/archivum/archivum-scanner/build/install/archivum-scanner/bin/archivum-scanner'
```

Apply changes:

```bash
source ~/.bashrc
```

### 3. Scanner Usage

**Scan a disk:**

```bash
# Mount external disk
sudo mount /dev/sdb1 /mnt/disk1

# Scan and upload to server
archivum-scan scan /mnt/disk1 \
  --name "My 4TB Disk 1" \
  --server-url http://localhost:8080

# Or scan first, upload later (for very large disks)
archivum-scan scan /mnt/disk1 --name "My 4TB Disk 1"
# ... later ...
archivum-scan upload /opt/archivum/archivum-scanner/output/[scan-id] \
  --server-url http://localhost:8080
```

**Scanner output location:**
- Default: `archivum-scanner/output/[scan-id]/`
- Contains: `source.json`, `files/batch-*.json`, `code-projects.json`

---

## Systemd Services

Create systemd services for automatic startup.

### 1. Backend Service

Create `/etc/systemd/system/archivum-server.service`:

```ini
[Unit]
Description=Archivum Server
After=network.target postgresql.service
Requires=postgresql.service

[Service]
Type=simple
User=your_username
WorkingDirectory=/opt/archivum
ExecStart=/usr/bin/java -jar /opt/archivum/archivum-server/build/libs/archivum-server-0.1.0.jar --spring.profiles.active=prod
Restart=on-failure
RestartSec=10
StandardOutput=append:/var/log/archivum/archivum-server.log
StandardError=append:/var/log/archivum/archivum-server-error.log

# Resource limits (adjust based on your RAM)
MemoryMax=4G
MemoryHigh=3G

# Environment
Environment="JAVA_OPTS=-Xmx3g -Xms1g"

[Install]
WantedBy=multi-user.target
```

Replace `your_username` with your actual user.

### 2. Enable and Start Backend Service

```bash
# Reload systemd
sudo systemctl daemon-reload

# Enable service (start on boot)
sudo systemctl enable archivum-server

# Start service now
sudo systemctl start archivum-server

# Check status
sudo systemctl status archivum-server

# View logs
sudo journalctl -u archivum-server -f
```

### 3. Frontend Service (if using npm serve)

Create `/etc/systemd/system/archivum-ui.service`:

```ini
[Unit]
Description=Archivum UI
After=network.target

[Service]
Type=simple
User=your_username
WorkingDirectory=/opt/archivum/archivum-ui
ExecStart=/home/your_username/.nvm/versions/node/v20.x.x/bin/npm run dev -- --host 0.0.0.0 --port 3000
Restart=on-failure
RestartSec=10
StandardOutput=append:/var/log/archivum/archivum-ui.log
StandardError=append:/var/log/archivum/archivum-ui-error.log

[Install]
WantedBy=multi-user.target
```

**Note**: If using Nginx, you don't need this service (Nginx serves static files).

---

## Performance Tuning

### 1. PostgreSQL Tuning

Run `pg_tune` for automatic configuration:

```bash
# Install pgtune
sudo apt install -y pgtune

# Generate optimized config
sudo pgtune -i /etc/postgresql/16/main/postgresql.conf \
            -o /etc/postgresql/16/main/postgresql.conf.tuned \
            --type=Web \
            --connections=100 \
            --max-connections=100

# Review and apply
sudo mv /etc/postgresql/16/main/postgresql.conf.tuned /etc/postgresql/16/main/postgresql.conf
sudo systemctl restart postgresql
```

### 2. Increase File Descriptors

For handling many open files during scanning:

Edit `/etc/security/limits.conf`:

```conf
*    soft    nofile    65536
*    hard    nofile    65536
```

Apply immediately:

```bash
sudo sysctl -w fs.file-max=65536
```

Make permanent in `/etc/sysctl.conf`:

```conf
fs.file-max=65536
```

### 3. Optimize SSD Performance

For NVMe/SSD database storage:

```bash
# Check I/O scheduler
cat /sys/block/nvme0n1/queue/scheduler
# Should show: [none] or [mq-deadline]

# If not, set it:
echo "none" | sudo tee /sys/block/nvme0n1/queue/scheduler
```

### 4. Java Heap Tuning

For the backend service, adjust heap size based on available RAM:

**32GB RAM system:**
- `-Xmx6g -Xms2g` (6GB max, 2GB initial)

**16GB RAM system:**
- `-Xmx3g -Xms1g` (3GB max, 1GB initial)

Edit the systemd service file and restart.

---

## Monitoring

### 1. System Resources

```bash
# Monitor CPU, RAM, disk
htop

# Watch PostgreSQL connections
watch -n 1 'psql -U archivum -d archivum -c "SELECT count(*) FROM pg_stat_activity;"'

# Check disk usage
df -h
du -sh /var/lib/postgresql/16/main  # Database size
```

### 2. Application Logs

```bash
# Backend logs
tail -f /var/log/archivum/archivum-server.log

# Nginx access logs
sudo tail -f /var/log/nginx/access.log

# PostgreSQL logs
sudo tail -f /var/log/postgresql/postgresql-16-main.log
```

### 3. Database Performance

```sql
-- Connect to database
psql -U archivum -d archivum

-- Check table sizes
SELECT
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size
FROM pg_tables
WHERE schemaname = 'public'
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;

-- Check slow queries
SELECT
    query,
    calls,
    total_time,
    mean_time,
    max_time
FROM pg_stat_statements
ORDER BY mean_time DESC
LIMIT 10;

-- Check index usage
SELECT
    schemaname,
    tablename,
    indexname,
    idx_scan,
    idx_tup_read,
    idx_tup_fetch
FROM pg_stat_user_indexes
ORDER BY idx_scan ASC;
```

---

## Backup Strategy

### 1. Database Backup

Create automated PostgreSQL backups:

```bash
# Create backup script
sudo nano /opt/archivum/scripts/backup-db.sh
```

```bash
#!/bin/bash
BACKUP_DIR="/backup/archivum/db"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
mkdir -p $BACKUP_DIR

# Backup database
pg_dump -U archivum -h localhost archivum | gzip > $BACKUP_DIR/archivum_$TIMESTAMP.sql.gz

# Keep only last 7 days
find $BACKUP_DIR -name "archivum_*.sql.gz" -mtime +7 -delete

echo "Backup completed: archivum_$TIMESTAMP.sql.gz"
```

Make executable and schedule:

```bash
chmod +x /opt/archivum/scripts/backup-db.sh

# Add to crontab (daily at 2 AM)
crontab -e
# Add line:
0 2 * * * /opt/archivum/scripts/backup-db.sh >> /var/log/archivum/backup.log 2>&1
```

### 2. Restore from Backup

```bash
# Stop backend
sudo systemctl stop archivum-server

# Restore database
gunzip -c /backup/archivum/db/archivum_20251228_020000.sql.gz | psql -U archivum -h localhost archivum

# Start backend
sudo systemctl start archivum-server
```

---

## Security Considerations

### 1. Firewall

```bash
# Allow only necessary ports
sudo ufw enable
sudo ufw allow 22    # SSH
sudo ufw allow 80    # HTTP (Nginx)
sudo ufw allow 443   # HTTPS (if using SSL)

# Backend port should NOT be exposed (proxied through Nginx)
# PostgreSQL should only listen on localhost (default)
```

### 2. SSL/TLS (Optional)

For production use, enable HTTPS with Let's Encrypt:

```bash
sudo apt install -y certbot python3-certbot-nginx

# Obtain certificate
sudo certbot --nginx -d your-domain.com

# Auto-renewal is configured automatically
```

### 3. PostgreSQL Password Security

```bash
# Generate strong password
openssl rand -base64 32

# Update in application-prod.properties
# Update PostgreSQL user password:
sudo -u postgres psql
ALTER USER archivum WITH PASSWORD 'new_strong_password';
\q
```

---

## Troubleshooting

### Backend won't start

```bash
# Check logs
sudo journalctl -u archivum-server -n 50

# Common issues:
# 1. PostgreSQL not running
sudo systemctl status postgresql

# 2. Database connection failed
psql -U archivum -d archivum -h localhost

# 3. Port already in use
sudo lsof -i :8080
```

### Database performance issues

```bash
# Vacuum and analyze
sudo -u postgres vacuumdb -U archivum -d archivum --analyze --verbose

# Reindex if needed
sudo -u postgres reindexdb -U archivum -d archivum
```

### Scanner fails

```bash
# Check disk mount
mount | grep /mnt

# Check permissions
ls -la /mnt/disk1

# Check disk space for output
df -h /opt/archivum/archivum-scanner/output
```

---

## Quick Start Checklist

- [ ] Install Java 21
- [ ] Install PostgreSQL 16
- [ ] Install Node.js 20
- [ ] Create database and user
- [ ] Configure PostgreSQL performance settings
- [ ] Clone/copy Archivum repository to `/opt/archivum`
- [ ] Build backend: `./gradlew :archivum-server:build`
- [ ] Build frontend: `cd archivum-ui && npm install && npm run build`
- [ ] Build scanner: `./gradlew :archivum-scanner:installDist`
- [ ] Configure Nginx (or run frontend with npm)
- [ ] Create systemd services
- [ ] Start services
- [ ] Test: Access http://server-ip (or http://server-ip:3000)
- [ ] Run first scan: `archivum-scan scan /mnt/test-disk --server-url http://localhost:8080`

---

## Next Steps

1. **Test with small disk** (100GB-1TB) to verify setup
2. **Monitor resource usage** during first scan
3. **Tune PostgreSQL** if queries are slow
4. **Set up backups** (database + scanner output)
5. **Document disk inventory** (which disk is which)
6. **Start systematic scanning** of all 20 disks

Good luck! 🚀
