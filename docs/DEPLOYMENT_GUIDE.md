# HabeshaGo Backend - GCP Deployment Guide

This guide documents the complete process of deploying the HabeshaGo backend to Google Cloud Platform (GCP) using Docker Compose on a Compute Engine VM.

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [GCP Project Setup](#gcp-project-setup)
3. [VM Instance Creation](#vm-instance-creation)
4. [Firewall Configuration](#firewall-configuration)
5. [Static IP Setup](#static-ip-setup)
6. [GitHub Repository Setup](#github-repository-setup)
7. [VM Configuration](#vm-configuration)
8. [Docker Installation](#docker-installation)
9. [Application Deployment](#application-deployment)
10. [DNS Configuration](#dns-configuration)
11. [SSL Certificate Setup](#ssl-certificate-setup)
12. [Maintenance Commands](#maintenance-commands)

---

## Prerequisites

### Local Machine Requirements
- **Google Cloud SDK (gcloud CLI)** - Command-line tool for GCP
- **Git** - Version control
- **A GCP account** with billing enabled

### Install Google Cloud SDK

**Windows:**
Download from: https://cloud.google.com/sdk/docs/install

**macOS:**
```bash
brew install google-cloud-sdk
```

**Linux:**
```bash
curl https://sdk.cloud.google.com | bash
exec -l $SHELL
```

### Verify Installation
```bash
gcloud --version
```

### Authenticate with GCP
```bash
gcloud auth login
```
This opens a browser window for Google authentication.

---

## GCP Project Setup

### Step 1: Create a New Project

```bash
gcloud projects create habeshago-prod --name="HabeshaGo Production"
```

**Why:** Projects in GCP are isolated containers for resources. Creating a dedicated project keeps production resources separate and makes billing/access management easier.

### Step 2: Set the Project as Default

```bash
gcloud config set project habeshago-prod
```

**Why:** This sets your default project so you don't need to specify `--project` in every command.

### Step 3: Add Additional Owners/Editors (Optional)

```bash
# Add as Editor
gcloud projects add-iam-policy-binding habeshago-prod \
  --member="user:someone@gmail.com" \
  --role="roles/editor"

# Add billing permissions
gcloud projects add-iam-policy-binding habeshago-prod \
  --member="user:someone@gmail.com" \
  --role="roles/billing.projectManager"
```

**Why:** Multiple team members may need access. Editor role allows resource management, billing.projectManager allows managing billing settings.

### Step 4: Link Billing Account

Go to: https://console.cloud.google.com/billing/projects

Select your project and link it to a billing account.

**Why:** GCP requires billing to be enabled before you can create resources like VMs.

---

## VM Instance Creation

### Step 1: Enable Compute Engine API

```bash
gcloud services enable compute.googleapis.com --project=habeshago-prod
```

**Why:** GCP APIs must be explicitly enabled. Compute Engine API is required to create and manage VMs.

### Step 2: Create the VM Instance

```bash
gcloud compute instances create habeshago-backend \
  --project=habeshago-prod \
  --zone=us-central1-a \
  --machine-type=e2-small \
  --image-family=ubuntu-2204-lts \
  --image-project=ubuntu-os-cloud \
  --boot-disk-size=20GB \
  --boot-disk-type=pd-balanced \
  --tags=http-server,https-server
```

**Parameter Explanation:**
| Parameter | Value | Why |
|-----------|-------|-----|
| `--zone` | us-central1-a | Geographic location. Choose based on your users' location |
| `--machine-type` | e2-small | 2 vCPU, 2GB RAM. Cost-effective for small apps (~$13/month) |
| `--image-family` | ubuntu-2204-lts | Ubuntu 22.04 LTS - stable, well-supported |
| `--boot-disk-size` | 20GB | Enough for OS, Docker images, and logs |
| `--boot-disk-type` | pd-balanced | Good balance of performance and cost |
| `--tags` | http-server,https-server | Used by firewall rules to target this VM |

**Machine Type Options:**
- `e2-micro`: 0.25 vCPU, 1GB RAM (~$6/month) - Too small for Java apps
- `e2-small`: 2 vCPU, 2GB RAM (~$13/month) - Good for small production
- `e2-medium`: 2 vCPU, 4GB RAM (~$27/month) - Better for growth

---

## Firewall Configuration

### Step 1: Create HTTPS Firewall Rule

```bash
gcloud compute firewall-rules create allow-https \
  --project=habeshago-prod \
  --direction=INGRESS \
  --priority=1000 \
  --network=default \
  --action=ALLOW \
  --rules=tcp:443 \
  --source-ranges=0.0.0.0/0 \
  --target-tags=https-server
```

**Why:** Allows encrypted HTTPS traffic (port 443) from anywhere to reach your server.

### Step 2: Create HTTP Firewall Rule

```bash
gcloud compute firewall-rules create allow-http \
  --project=habeshago-prod \
  --direction=INGRESS \
  --priority=1000 \
  --network=default \
  --action=ALLOW \
  --rules=tcp:80 \
  --source-ranges=0.0.0.0/0 \
  --target-tags=http-server
```

**Why:** Required for:
1. **Let's Encrypt validation** - Certbot uses HTTP-01 challenge on port 80
2. **HTTP to HTTPS redirect** - Users typing `http://` get redirected to `https://`

**Parameter Explanation:**
| Parameter | Value | Why |
|-----------|-------|-----|
| `--direction` | INGRESS | Incoming traffic (from internet to VM) |
| `--priority` | 1000 | Lower number = higher priority. 1000 is standard |
| `--source-ranges` | 0.0.0.0/0 | Allow from any IP address |
| `--target-tags` | http-server | Only applies to VMs with this tag |

---

## Static IP Setup

### Step 1: Reserve a Static IP Address

```bash
gcloud compute addresses create habeshago-ip \
  --project=habeshago-prod \
  --region=us-central1
```

**Why:** By default, VMs get ephemeral IPs that change on restart. A static IP ensures your DNS records remain valid.

### Step 2: Get the Reserved IP

```bash
gcloud compute addresses describe habeshago-ip \
  --project=habeshago-prod \
  --region=us-central1 \
  --format="get(address)"
```

Output: `104.197.21.207` (your IP will differ)

### Step 3: Attach Static IP to VM

```bash
gcloud compute instances delete-access-config habeshago-backend \
  --project=habeshago-prod \
  --zone=us-central1-a \
  --access-config-name="external-nat"

gcloud compute instances add-access-config habeshago-backend \
  --project=habeshago-prod \
  --zone=us-central1-a \
  --address=104.197.21.207
```

**Why:** Two-step process:
1. Remove the current ephemeral IP
2. Attach the reserved static IP

---

## GitHub Repository Setup

### Step 1: Initialize Git Repository

```bash
cd /path/to/habeshago
git init
```

### Step 2: Create .gitignore

Create `.gitignore` with:
```
# Build outputs
target/
build/
*.class
*.jar
*.war

# IDE files
.idea/
*.iml
.vscode/

# Logs
logs/
*.log

# Environment and secrets
.env
*.env.local
application-local.properties

# OS files
.DS_Store
Thumbs.db

# Maven
.mvn/wrapper/maven-wrapper.jar

# Test outputs
test-output/
surefire-reports/

# Database files
*.db
*.mv.db
```

**Why:** Prevents committing sensitive files, build artifacts, and IDE-specific files.

### Step 3: Commit and Push

```bash
git add .
git commit -m "Initial commit - HabeshaGo backend"
git remote add origin https://github.com/your-org/habeshago-backend.git
git branch -M main
git push -u origin main
```

---

## VM Configuration

### Step 1: SSH into the VM

```bash
gcloud compute ssh habeshago-backend \
  --project=habeshago-prod \
  --zone=us-central1-a
```

**Why:** `gcloud compute ssh` handles SSH key management automatically. No need to manually create/upload keys.

### Step 2: Update System Packages

```bash
sudo apt update && sudo apt upgrade -y
```

**Why:** Ensures security patches are applied and packages are current.

---

## Docker Installation

### Step 1: Install Docker

```bash
# Install prerequisites
sudo apt install -y apt-transport-https ca-certificates curl software-properties-common

# Add Docker's GPG key
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg

# Add Docker repository
echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Install Docker
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# Add current user to docker group (avoids needing sudo)
sudo usermod -aG docker $USER
```

**Why:**
- We install from Docker's official repository (not Ubuntu's) for latest version
- Adding user to docker group allows running docker commands without sudo

### Step 2: Verify Installation

```bash
# Log out and back in for group change to take effect
exit
# SSH back in
gcloud compute ssh habeshago-backend --project=habeshago-prod --zone=us-central1-a

# Verify
docker --version
docker compose version
```

---

## Application Deployment

### Step 1: Clone the Repository

```bash
cd ~
git clone https://github.com/your-org/habeshago-backend.git
cd habeshago-backend
```

### Step 2: Create Environment File

```bash
cd deploy
nano .env
```

Add the following (replace with your actual values):

```env
# Database
POSTGRES_DB=habeshago
POSTGRES_USER=habeshago
POSTGRES_PASSWORD=your_secure_password_here

# Application
SPRING_PROFILES_ACTIVE=prod
JWT_SECRET=your_64_character_random_string_here

# Telegram Bot
TELEGRAM_BOT_TOKEN=your_telegram_bot_token

# Stripe
STRIPE_SECRET_KEY=sk_live_your_stripe_key
STRIPE_WEBHOOK_SECRET=whsec_your_webhook_secret

# Domain
DOMAIN=api.habeshago.com
```

**Why:** Environment variables keep secrets out of source code. The `.env` file is in `.gitignore`.

### Step 3: Start the Services

```bash
docker compose up -d
```

**Parameter Explanation:**
- `-d`: Detached mode (runs in background)

### Step 4: Verify Services are Running

```bash
docker compose ps
```

Expected output:
```
NAME                STATUS              PORTS
habeshago-backend   Up (healthy)        0.0.0.0:8080->8080/tcp
habeshago-db        Up (healthy)        5432/tcp
habeshago-nginx     Up                  0.0.0.0:80->80/tcp, 0.0.0.0:443->443/tcp
habeshago-certbot   Up
```

### Step 5: Test the API

```bash
curl http://localhost:8080/actuator/health
# Should return: {"status":"UP"}
```

---

## DNS Configuration

### Step 1: Add A Record

In your DNS provider (Cloudflare, Google Domains, etc.), add:

| Type | Name | Value | TTL |
|------|------|-------|-----|
| A | api | 104.197.21.207 | 300 |

This creates `api.habeshago.com` pointing to your server.

### Step 2: Verify DNS Propagation

```bash
# From your local machine
nslookup api.habeshago.com
# or
dig api.habeshago.com
```

Wait until it resolves to your static IP. Propagation can take 5 minutes to 48 hours.

---

## SSL Certificate Setup

### Step 1: Obtain Certificate (After DNS Propagates)

SSH into the VM and run:

```bash
cd ~/habeshago-backend/deploy
docker compose run --rm certbot certonly \
  --webroot \
  --webroot-path=/var/www/certbot \
  -d api.habeshago.com \
  --email your-email@example.com \
  --agree-tos \
  --no-eff-email
```

**Parameter Explanation:**
| Parameter | Why |
|-----------|-----|
| `--webroot` | Use webroot authentication (files served via nginx) |
| `--webroot-path` | Directory where certbot places challenge files |
| `-d` | Domain to get certificate for |
| `--email` | For expiration notices |
| `--agree-tos` | Accept Let's Encrypt terms |
| `--no-eff-email` | Don't share email with EFF |

### Step 2: Update Nginx Configuration

After obtaining the certificate, update `deploy/nginx/nginx.conf` to enable HTTPS:

```nginx
server {
    listen 80;
    server_name api.habeshago.com;

    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }

    location / {
        return 301 https://$host$request_uri;
    }
}

server {
    listen 443 ssl http2;
    server_name api.habeshago.com;

    ssl_certificate /etc/letsencrypt/live/api.habeshago.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.habeshago.com/privkey.pem;

    # SSL configuration
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256;
    ssl_prefer_server_ciphers off;

    location / {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### Step 3: Reload Nginx

```bash
docker compose exec nginx nginx -s reload
```

### Step 4: Set Up Auto-Renewal

Certificates expire every 90 days. Add a cron job:

```bash
crontab -e
```

Add:
```
0 3 * * * cd ~/habeshago-backend/deploy && docker compose run --rm certbot renew --quiet && docker compose exec nginx nginx -s reload
```

**Why:** Runs daily at 3 AM. Certbot only renews if certificate is expiring within 30 days.

---

## Maintenance Commands

### View Logs

```bash
# All services
docker compose logs -f

# Specific service
docker compose logs -f backend

# Last 100 lines
docker compose logs --tail=100 backend
```

### Restart Services

```bash
# All services
docker compose restart

# Specific service
docker compose restart backend
```

### Update Application

```bash
cd ~/habeshago-backend

# Pull latest code
git pull origin main

# Rebuild and restart
cd deploy
docker compose down
docker compose build --no-cache backend
docker compose up -d
```

### Check Resource Usage

```bash
# Container stats
docker stats

# Disk usage
df -h

# Memory usage
free -h
```

### Database Backup

```bash
docker compose exec postgres pg_dump -U habeshago habeshago > backup_$(date +%Y%m%d).sql
```

### Database Restore

```bash
docker compose exec -T postgres psql -U habeshago habeshago < backup_20240115.sql
```

### Stop All Services

```bash
docker compose down
```

### Stop and Remove All Data (CAUTION!)

```bash
docker compose down -v  # -v removes volumes including database
```

---

## Troubleshooting

### Container Won't Start

```bash
# Check logs
docker compose logs backend

# Check if port is in use
sudo netstat -tulpn | grep 8080
```

### Can't Connect to Database

```bash
# Verify postgres is running
docker compose ps postgres

# Check postgres logs
docker compose logs postgres

# Test connection from backend container
docker compose exec backend sh -c "nc -zv postgres 5432"
```

### SSL Certificate Issues

```bash
# Check certificate status
docker compose run --rm certbot certificates

# Force renewal
docker compose run --rm certbot renew --force-renewal
```

### Out of Disk Space

```bash
# Clean up Docker
docker system prune -a

# Remove old logs
sudo journalctl --vacuum-time=7d
```

---

## Cost Estimation

| Resource | Monthly Cost |
|----------|--------------|
| e2-small VM | ~$13 |
| 20GB SSD | ~$2 |
| Static IP | ~$3 (free if attached) |
| Egress (10GB) | ~$1 |
| **Total** | **~$16-20/month** |

---

## Security Checklist

- [ ] Strong database password (32+ characters)
- [ ] JWT secret is random (64+ characters)
- [ ] `.env` file is not in git
- [ ] SSH key authentication only (no password)
- [ ] Firewall rules limit access
- [ ] SSL/TLS enabled
- [ ] Regular backups configured
- [ ] Monitoring/alerts set up
