#!/bin/bash

# FinAI Keycloak Theme Setup Script
# This script helps you activate the FinAI theme in Keycloak

set -e

echo "========================================="
echo "   FinAI Keycloak Theme Setup"
echo "========================================="
echo ""

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Check if Keycloak is running
echo "Checking Keycloak status..."
if docker ps | grep -q keycloak; then
    echo -e "${GREEN}✓ Keycloak is running${NC}"
else
    echo -e "${RED}✗ Keycloak is not running${NC}"
    echo ""
    echo "Starting Keycloak..."
    docker compose up -d keycloak
    echo "Waiting for Keycloak to start (30 seconds)..."
    sleep 30
fi

echo ""
echo "Theme files location: $(pwd)/keycloak-export/themes/finai"
echo ""

# Check if theme directory exists
if [ -d "keycloak-export/themes/finai" ]; then
    echo -e "${GREEN}✓ Theme directory exists${NC}"
else
    echo -e "${RED}✗ Theme directory not found${NC}"
    exit 1
fi

# Check if theme is accessible in container
echo ""
echo "Verifying theme is mounted in Keycloak container..."
if docker exec keycloak ls /opt/keycloak/themes/finai > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Theme is properly mounted${NC}"
else
    echo -e "${RED}✗ Theme is not accessible in container${NC}"
    echo "Please restart Keycloak:"
    echo "  docker compose restart keycloak"
    exit 1
fi

echo ""
echo "========================================="
echo "   Next Steps"
echo "========================================="
echo ""
echo "1. Open Keycloak Admin Console:"
echo -e "   ${YELLOW}http://localhost:7080${NC}"
echo ""
echo "2. Login with:"
echo "   Username: admin"
echo "   Password: admin"
echo ""
echo "3. Select your realm: ${YELLOW}finance${NC}"
echo ""
echo "4. Go to: Realm Settings > Themes tab"
echo ""
echo "5. Set:"
echo "   - Login Theme: ${YELLOW}finai${NC}"
echo "   - Account Theme: finai (optional)"
echo "   - Email Theme: finai (optional)"
echo ""
echo "6. Click ${YELLOW}Save${NC}"
echo ""
echo "7. Test by logging out or opening:"
echo -e "   ${YELLOW}http://localhost:7080/realms/finance/account${NC}"
echo ""
echo "========================================="
echo ""
echo -e "${GREEN}Setup script completed!${NC}"
echo ""
echo "For more information, see:"
echo "  keycloak-export/themes/finai/README.md"
echo ""
