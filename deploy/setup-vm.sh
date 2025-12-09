#!/bin/bash
set -e

echo "========================================="
echo "HabeshaGo Backend - GCP VM Setup Script"
echo "========================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if running as root
if [ "$EUID" -ne 0 ]; then
    echo -e "${RED}Please run as root (sudo)${NC}"
    exit 1
fi

echo -e "${YELLOW}Step 1: Updating system packages...${NC}"
apt-get update && apt-get upgrade -y

echo -e "${YELLOW}Step 2: Installing Docker...${NC}"
if ! command -v docker &> /dev/null; then
    curl -fsSL https://get.docker.com -o get-docker.sh
    sh get-docker.sh
    rm get-docker.sh
    usermod -aG docker $SUDO_USER
    echo -e "${GREEN}Docker installed successfully${NC}"
else
    echo -e "${GREEN}Docker already installed${NC}"
fi

echo -e "${YELLOW}Step 3: Installing Docker Compose...${NC}"
if ! command -v docker-compose &> /dev/null; then
    curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
    chmod +x /usr/local/bin/docker-compose
    echo -e "${GREEN}Docker Compose installed successfully${NC}"
else
    echo -e "${GREEN}Docker Compose already installed${NC}"
fi

echo -e "${YELLOW}Step 4: Installing Git...${NC}"
apt-get install -y git

echo -e "${YELLOW}Step 5: Creating app directory...${NC}"
APP_DIR=/opt/habeshago
mkdir -p $APP_DIR
cd $APP_DIR

echo -e "${YELLOW}Step 6: Setting up firewall...${NC}"
apt-get install -y ufw
ufw allow ssh
ufw allow http
ufw allow https
ufw --force enable

echo -e "${YELLOW}Step 7: Installing fail2ban for security...${NC}"
apt-get install -y fail2ban
systemctl enable fail2ban
systemctl start fail2ban

echo ""
echo -e "${GREEN}=========================================${NC}"
echo -e "${GREEN}VM Setup Complete!${NC}"
echo -e "${GREEN}=========================================${NC}"
echo ""
echo "Next steps:"
echo "1. Clone your repository to $APP_DIR"
echo "   git clone https://github.com/YOUR_USERNAME/habeshago-backend.git ."
echo ""
echo "2. Copy and configure environment file:"
echo "   cp deploy/.env.example deploy/.env"
echo "   nano deploy/.env"
echo ""
echo "3. Start the application:"
echo "   cd deploy && docker-compose up -d"
echo ""
echo "4. Get SSL certificate (replace YOUR_DOMAIN):"
echo "   docker-compose run --rm certbot certonly --webroot -w /var/www/certbot -d YOUR_DOMAIN"
echo ""
echo "5. Update nginx.conf with your domain and uncomment HTTPS server block"
echo ""
echo "6. Restart nginx:"
echo "   docker-compose restart nginx"
echo ""
