# HabeshaGo Backend - Operations Guide

This guide covers day-to-day operations, debugging, and management of the HabeshaGo backend running on GCP.

## Table of Contents
1. [VM Access](#vm-access)
2. [Project Information](#project-information)
3. [Viewing Logs](#viewing-logs)
4. [Database Access](#database-access)
5. [Environment Variables](#environment-variables)
6. [Container Management](#container-management)
7. [Local Development Setup](#local-development-setup)
8. [Updating the Application](#updating-the-application)
9. [Backup & Restore](#backup--restore)
10. [Monitoring & Health Checks](#monitoring--health-checks)
11. [Troubleshooting](#troubleshooting)
12. [Useful Commands Reference](#useful-commands-reference)

---

## VM Access

### Accessing the VM via SSH

**From local machine (requires gcloud CLI):**
```bash
gcloud compute ssh habeshago-backend --project=habeshago-prod --zone=us-central1-a
```

**From GCP Console:**
1. Go to https://console.cloud.google.com/compute/instances
2. Select project `habeshago-prod`
3. Click "SSH" button next to `habeshago-backend`

### SSH Key Management

SSH keys are automatically managed by gcloud. To manually add keys:

```bash
# Generate new SSH key
ssh-keygen -t rsa -b 4096 -C "your-email@example.com"

# Add to GCP project metadata
gcloud compute project-info add-metadata \
  --metadata-from-file ssh-keys=~/.ssh/id_rsa.pub
```

### Useful VM Commands

```bash
# Check VM status
gcloud compute instances describe habeshago-backend \
  --project=habeshago-prod \
  --zone=us-central1-a

# Start VM (if stopped)
gcloud compute instances start habeshago-backend \
  --project=habeshago-prod \
  --zone=us-central1-a

# Stop VM
gcloud compute instances stop habeshago-backend \
  --project=habeshago-prod \
  --zone=us-central1-a

# Restart VM
gcloud compute instances reset habeshago-backend \
  --project=habeshago-prod \
  --zone=us-central1-a
```

---

## Project Information

### GCP Project Details

| Item | Value |
|------|-------|
| Project ID | `habeshago-prod` |
| VM Name | `habeshago-backend` |
| Zone | `us-central1-a` |
| Machine Type | `e2-small` |
| Static IP | `104.197.21.207` |
| Domain | `api.habeshago.com` |

### GitHub Repository

| Item | Value |
|------|-------|
| Repository | `https://github.com/ethlist/habeshago-backend` |
| Branch | `main` |
| VM Clone Location | `/home/mikem/habeshago-backend` |

### GCP Console URLs

- **Compute Engine:** https://console.cloud.google.com/compute/instances?project=habeshago-prod
- **Logs Explorer:** https://console.cloud.google.com/logs?project=habeshago-prod
- **Billing:** https://console.cloud.google.com/billing?project=habeshago-prod
- **Firewall Rules:** https://console.cloud.google.com/networking/firewalls?project=habeshago-prod

---

## Viewing Logs

### Application Logs (Docker)

```bash
# SSH into VM first
gcloud compute ssh habeshago-backend --project=habeshago-prod --zone=us-central1-a

# Navigate to project directory
cd /home/mikem/habeshago-backend/deploy

# View all container logs (last 100 lines, follow)
docker compose logs -f --tail=100

# View only backend logs
docker compose logs -f backend

# View only database logs
docker compose logs -f postgres

# View only nginx logs
docker compose logs -f nginx

# View logs without following
docker compose logs --tail=200 backend

# View logs from specific time
docker compose logs --since="2024-01-15T10:00:00" backend

# View logs for last 30 minutes
docker compose logs --since="30m" backend
```

### Log Files Inside Container

The production configuration writes logs to files. Access them:

```bash
# Enter backend container
docker compose exec backend sh

# View application log
cat /app/logs/app.log

# View security audit log
cat /app/logs/security-audit.log

# Exit container
exit
```

### Log Format (Production)

Logs are in JSON format for easy parsing:
```json
{
  "timestamp": "2024-01-15T10:30:45.123Z",
  "level": "INFO",
  "logger": "com.habeshago.trip.TripController",
  "message": "Trip created",
  "requestId": "abc12345",
  "ip": "192.168.1.1"
}
```

### Security Audit Logs

Security events are logged separately:
```bash
# View security logs
docker compose logs backend | grep "SECURITY_EVENT"

# Or inside container
docker compose exec backend sh -c "cat /app/logs/security-audit.log | tail -50"
```

### GCP Cloud Logging

Container logs are also available in GCP:
1. Go to https://console.cloud.google.com/logs
2. Select project `habeshago-prod`
3. Filter: `resource.type="gce_instance" resource.labels.instance_id="habeshago-backend"`

---

## Database Access

### Accessing PostgreSQL

**Method 1: Via Docker Compose**
```bash
# SSH into VM
gcloud compute ssh habeshago-backend --project=habeshago-prod --zone=us-central1-a

# Navigate to deploy directory
cd /home/mikem/habeshago-backend/deploy

# Connect to PostgreSQL
docker compose exec postgres psql -U habeshago -d habeshago
```

**Method 2: Direct psql in container**
```bash
docker compose exec postgres psql -U $POSTGRES_USER -d $POSTGRES_DB
```

### Common Database Commands

```sql
-- List all tables
\dt

-- Describe a table
\d users
\d trips
\d item_requests

-- Count records
SELECT COUNT(*) FROM users;
SELECT COUNT(*) FROM trips;
SELECT COUNT(*) FROM item_requests;

-- View recent users
SELECT id, email, first_name, created_at FROM users ORDER BY created_at DESC LIMIT 10;

-- View recent trips
SELECT id, from_city, to_city, departure_date, status FROM trips ORDER BY created_at DESC LIMIT 10;

-- View pending requests
SELECT * FROM item_requests WHERE status = 'PENDING';

-- Exit psql
\q
```

### Database Credentials

The database password is stored in the `.env` file on the VM:

```bash
# View current .env file (contains passwords)
cat /home/mikem/habeshago-backend/deploy/.env

# The file contains:
# POSTGRES_PASSWORD=<your-password>
```

**Important:** Never commit the `.env` file to git. It's in `.gitignore`.

### Database Tools (Remote Access)

To connect from your local machine (e.g., DBeaver, pgAdmin):

**Option 1: SSH Tunnel**
```bash
# Create SSH tunnel (run locally)
gcloud compute ssh habeshago-backend \
  --project=habeshago-prod \
  --zone=us-central1-a \
  -- -L 5432:localhost:5432

# Now connect your DB tool to:
# Host: localhost
# Port: 5432
# Database: habeshago
# User: habeshago
# Password: (from .env file)
```

**Option 2: Expose port temporarily (NOT RECOMMENDED for production)**
```bash
# On VM - temporarily forward port
docker compose exec postgres psql -U habeshago -d habeshago
```

### Database Backup

```bash
# Create backup
cd /home/mikem/habeshago-backend/deploy
docker compose exec postgres pg_dump -U habeshago habeshago > backup_$(date +%Y%m%d_%H%M%S).sql

# Download backup to local machine (run locally)
gcloud compute scp habeshago-backend:/home/mikem/habeshago-backend/deploy/backup_*.sql . \
  --project=habeshago-prod \
  --zone=us-central1-a
```

### Database Restore

```bash
# Upload backup to VM (run locally)
gcloud compute scp ./backup_20240115.sql habeshago-backend:/home/mikem/habeshago-backend/deploy/ \
  --project=habeshago-prod \
  --zone=us-central1-a

# Restore on VM
cd /home/mikem/habeshago-backend/deploy
docker compose exec -T postgres psql -U habeshago habeshago < backup_20240115.sql
```

---

## Environment Variables

### Viewing Current Environment

```bash
# SSH into VM
gcloud compute ssh habeshago-backend --project=habeshago-prod --zone=us-central1-a

# View .env file
cat /home/mikem/habeshago-backend/deploy/.env
```

### Modifying Environment Variables

```bash
# SSH into VM
cd /home/mikem/habeshago-backend/deploy

# Edit .env file
nano .env
# or
vim .env

# After editing, restart containers for changes to take effect
docker compose down
docker compose up -d
```

### Required Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `POSTGRES_DB` | Database name | `habeshago` |
| `POSTGRES_USER` | Database user | `habeshago` |
| `POSTGRES_PASSWORD` | Database password | (strong password) |
| `TELEGRAM_BOT_TOKEN` | Telegram Bot API token | `123456:ABC...` |
| `JWT_SECRET` | JWT signing secret (64+ chars) | (random string) |
| `STRIPE_SECRET_KEY` | Stripe API key | `sk_live_...` |
| `CORS_ALLOWED_ORIGINS` | Allowed frontend domains | `https://habeshago.com` |

### Generating Secure Secrets

```bash
# Generate random JWT secret (64 characters)
openssl rand -base64 48

# Generate random database password
openssl rand -base64 24
```

---

## Container Management

### Basic Commands

```bash
# Navigate to deploy directory first
cd /home/mikem/habeshago-backend/deploy

# View running containers
docker compose ps

# Start all containers
docker compose up -d

# Stop all containers
docker compose down

# Restart specific container
docker compose restart backend
docker compose restart postgres
docker compose restart nginx

# View container resource usage
docker stats
```

### Container Logs

```bash
# Follow logs for all containers
docker compose logs -f

# Follow logs for specific container
docker compose logs -f backend
docker compose logs -f postgres

# Show last N lines
docker compose logs --tail=100 backend
```

### Entering Containers

```bash
# Enter backend container (bash/sh)
docker compose exec backend sh

# Enter postgres container
docker compose exec postgres bash

# Enter nginx container
docker compose exec nginx sh
```

### Rebuilding Containers

```bash
# Rebuild backend after code changes
docker compose build --no-cache backend
docker compose up -d backend

# Rebuild all containers
docker compose build --no-cache
docker compose up -d
```

---

## Local Development Setup

### Prerequisites

1. **Java 21** - Download from https://adoptium.net/
2. **Maven 3.9+** - Download from https://maven.apache.org/download.cgi
3. **Git** - https://git-scm.com/downloads
4. **IDE (Recommended)**
   - IntelliJ IDEA (Community or Ultimate)
   - VS Code with Java extensions

### Clone and Run Locally

```bash
# Clone the repository
git clone https://github.com/ethlist/habeshago-backend.git
cd habeshago-backend

# Run with H2 database (development mode)
mvn spring-boot:run

# Or specify profile explicitly
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The application starts at `http://localhost:8080`

### Local Environment Variables

Create `application-local.properties` (gitignored):
```properties
# Local development overrides
telegram.bot.token=YOUR_TEST_BOT_TOKEN
jwt.secret=your-local-development-jwt-secret-must-be-at-least-64-characters-long
stripe.secret.key=sk_test_your_test_key
```

Run with local profile:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### IDE Setup (IntelliJ IDEA)

1. Open project: File → Open → Select `pom.xml`
2. Import as Maven project
3. Set Project SDK to Java 21
4. Run `HabeshaGoApplication.java`

### Testing API Locally

```bash
# Health check
curl http://localhost:8080/actuator/health

# Create test request (requires auth token)
# First get token via Telegram auth or web register
```

### Recommended Local Tools

| Tool | Purpose | Download |
|------|---------|----------|
| Postman | API testing | https://www.postman.com/downloads/ |
| DBeaver | Database GUI | https://dbeaver.io/download/ |
| Docker Desktop | Local containers | https://www.docker.com/products/docker-desktop |

---

## Updating the Application

### Deploying Code Updates

```bash
# SSH into VM
gcloud compute ssh habeshago-backend --project=habeshago-prod --zone=us-central1-a

# Navigate to project
cd /home/mikem/habeshago-backend

# Pull latest code
git pull origin main

# Rebuild and restart
cd deploy
docker compose down
docker compose build --no-cache backend
docker compose up -d

# Verify containers are healthy
docker compose ps
```

### Quick Deploy Script

Create this as `deploy.sh` on the VM:

```bash
#!/bin/bash
set -e

echo "=== Deploying HabeshaGo Backend ==="

cd /home/mikem/habeshago-backend

echo "Pulling latest code..."
git pull origin main

echo "Building and restarting containers..."
cd deploy
docker compose down
docker compose build --no-cache backend
docker compose up -d

echo "Waiting for health check..."
sleep 30
docker compose ps

echo "=== Deployment complete ==="
```

Make it executable:
```bash
chmod +x deploy.sh
./deploy.sh
```

### Rolling Back

```bash
# View recent commits
git log --oneline -10

# Rollback to specific commit
git checkout <commit-hash>

# Rebuild and restart
cd deploy
docker compose down
docker compose build --no-cache backend
docker compose up -d
```

---

## Backup & Restore

### Automated Daily Backups

Create cron job for daily backups:

```bash
# Edit crontab
crontab -e

# Add daily backup at 3 AM
0 3 * * * cd /home/mikem/habeshago-backend/deploy && docker compose exec -T postgres pg_dump -U habeshago habeshago > /home/mikem/backups/db_$(date +\%Y\%m\%d).sql 2>/dev/null

# Keep only last 7 days of backups
0 4 * * * find /home/mikem/backups -name "db_*.sql" -mtime +7 -delete
```

Create backup directory:
```bash
mkdir -p /home/mikem/backups
```

### Manual Backup

```bash
# Full database backup
cd /home/mikem/habeshago-backend/deploy
docker compose exec -T postgres pg_dump -U habeshago habeshago > ~/backups/manual_backup_$(date +%Y%m%d_%H%M%S).sql

# Backup specific table
docker compose exec -T postgres pg_dump -U habeshago -t users habeshago > ~/backups/users_backup.sql
```

### Restore from Backup

```bash
# Stop backend to prevent writes
cd /home/mikem/habeshago-backend/deploy
docker compose stop backend

# Restore database
docker compose exec -T postgres psql -U habeshago habeshago < ~/backups/db_20240115.sql

# Restart backend
docker compose start backend
```

---

## Monitoring & Health Checks

### Health Endpoints

```bash
# Application health (HTTPS - production)
curl https://api.habeshago.com/actuator/health

# From inside the VM
curl http://localhost:8080/actuator/health
```

Response:
```json
{"status":"UP"}
```

### UptimeRobot Setup (Free Monitoring)

UptimeRobot provides free uptime monitoring with email/SMS alerts.

**Setup Steps:**

1. **Create Account**
   - Go to https://uptimerobot.com
   - Sign up for free account

2. **Add New Monitor**
   - Click "Add New Monitor"
   - Configure:
     - **Monitor Type:** HTTP(s)
     - **Friendly Name:** HabeshaGo API
     - **URL:** `https://api.habeshago.com/actuator/health`
     - **Monitoring Interval:** 5 minutes (free tier)

3. **Configure Alerts**
   - Add your email for notifications
   - Optionally add SMS (limited on free tier)
   - Set alert contacts to notify on down/up events

4. **Optional: Add Keyword Monitor**
   - Monitor Type: Keyword
   - URL: `https://api.habeshago.com/actuator/health`
   - Keyword: `UP`
   - Alert if keyword: exists (or doesn't exist for failure)

**Free Tier Includes:**
- 50 monitors
- 5-minute check intervals
- Email notifications
- 2-month log history
- Public status page

### Alternative Monitoring Options

| Service | Free Tier | Features |
|---------|-----------|----------|
| UptimeRobot | 50 monitors, 5-min | Email alerts, status page |
| Better Uptime | 10 monitors, 3-min | Slack integration |
| Freshping | 50 monitors, 1-min | Multi-location checks |
| GCP Monitoring | Limited free | Native GCP integration |

### Container Health Status

```bash
docker compose ps

# Example output:
# NAME                STATUS
# habeshago-backend   Up (healthy)
# habeshago-db        Up (healthy)
# habeshago-nginx     Up
```

### Resource Monitoring

```bash
# Container resource usage
docker stats

# Disk usage
df -h

# Memory usage
free -h

# CPU usage
top
```

### Uptime Monitoring (External)

Recommended services for monitoring:
- **UptimeRobot** (free) - https://uptimerobot.com
- **Pingdom** - https://www.pingdom.com
- **GCP Monitoring** - Built into GCP Console

Set up monitoring for:
- `https://api.habeshago.com/actuator/health`
- Expected response: `{"status":"UP"}`

---

## Troubleshooting

### Container Won't Start

```bash
# Check logs for errors
docker compose logs backend

# Common issues:
# - Database connection failed: Check POSTGRES_PASSWORD in .env
# - Port already in use: Check if another process is using 8080
# - Out of memory: Check with `free -h`
```

### Database Connection Issues

```bash
# Check if postgres is running
docker compose ps postgres

# Check postgres logs
docker compose logs postgres

# Test connection from backend container
docker compose exec backend sh -c "nc -zv postgres 5432"
```

### Application Not Responding

```bash
# Check if backend is running
docker compose ps backend

# Check health endpoint
curl http://localhost:8080/actuator/health

# Check backend logs
docker compose logs --tail=100 backend

# Restart backend
docker compose restart backend
```

### SSL Certificate Issues

```bash
# Check certificate status
docker compose run --rm certbot certificates

# Force renewal
docker compose run --rm certbot renew --force-renewal

# Reload nginx after renewal
docker compose exec nginx nginx -s reload
```

### Out of Disk Space

```bash
# Check disk usage
df -h

# Clean Docker resources
docker system prune -a

# Remove old logs
sudo journalctl --vacuum-time=3d

# Remove old backups
find ~/backups -name "*.sql" -mtime +7 -delete
```

### High Memory Usage

```bash
# Check memory
free -h

# Check container memory
docker stats --no-stream

# Restart containers to free memory
docker compose restart
```

---

## Useful Commands Reference

### Quick Reference Card

```bash
# === SSH ACCESS ===
gcloud compute ssh habeshago-backend --project=habeshago-prod --zone=us-central1-a

# === NAVIGATION ===
cd /home/mikem/habeshago-backend/deploy

# === CONTAINER STATUS ===
docker compose ps                    # View containers
docker compose logs -f backend       # Follow backend logs
docker compose restart backend       # Restart backend

# === DATABASE ===
docker compose exec postgres psql -U habeshago -d habeshago   # Connect to DB
# Inside psql:
\dt                                  # List tables
SELECT COUNT(*) FROM users;          # Count users
\q                                   # Exit

# === ENVIRONMENT ===
cat .env                             # View env vars
nano .env                            # Edit env vars

# === DEPLOYMENT ===
git pull origin main                 # Get latest code
docker compose build --no-cache backend   # Rebuild
docker compose up -d                 # Start containers

# === BACKUP ===
docker compose exec -T postgres pg_dump -U habeshago habeshago > backup.sql

# === MONITORING ===
curl http://localhost:8080/actuator/health   # Health check
docker stats                         # Resource usage
```

### Bookmark These URLs

- **GCP Console:** https://console.cloud.google.com/home/dashboard?project=habeshago-prod
- **VM Instances:** https://console.cloud.google.com/compute/instances?project=habeshago-prod
- **GitHub Repo:** https://github.com/ethlist/habeshago-backend
- **API Health:** http://104.197.21.207/actuator/health

---

## Emergency Contacts & Procedures

### If the Application is Down

1. Check VM status in GCP Console
2. SSH into VM and check `docker compose ps`
3. Check logs: `docker compose logs --tail=100`
4. Try restarting: `docker compose restart`
5. If all else fails: `docker compose down && docker compose up -d`

### If Database is Corrupted

1. Stop backend: `docker compose stop backend`
2. Restore from latest backup (see Backup & Restore section)
3. Start backend: `docker compose start backend`

### If Hacked/Compromised

1. Immediately stop VM: `gcloud compute instances stop habeshago-backend`
2. Review security audit logs
3. Change all secrets (DB password, JWT secret, API keys)
4. Restore from known-good backup
5. Start VM with new secrets
