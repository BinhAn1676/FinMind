#!/bin/bash

# Quick Test Script - FinAI Keycloak Theme
# This script helps you quickly test the theme

echo "========================================="
echo "   FinAI Theme Quick Test"
echo "========================================="
echo ""

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Check Keycloak
echo "Checking Keycloak status..."
if docker ps | grep -q keycloak; then
    echo -e "${GREEN}✓ Keycloak is running${NC}"
else
    echo -e "${YELLOW}! Keycloak is not running. Starting...${NC}"
    docker compose up -d keycloak
    echo "Waiting 30 seconds for Keycloak to start..."
    sleep 30
fi

echo ""
echo "Checking theme installation..."
if docker exec keycloak ls /opt/keycloak/themes/finai/login/template.ftl > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Theme files are accessible${NC}"
else
    echo -e "${YELLOW}! Theme files not found. Please restart Keycloak.${NC}"
fi

echo ""
echo "========================================="
echo "   Open These URLs to Test"
echo "========================================="
echo ""
echo -e "1. Admin Console (to activate theme):"
echo -e "   ${BLUE}http://localhost:7080${NC}"
echo ""
echo -e "2. Login Page (to see the theme):"
echo -e "   ${BLUE}http://localhost:7080/realms/finance/account${NC}"
echo ""
echo -e "3. Your Angular App:"
echo -e "   ${BLUE}http://localhost:4200${NC}"
echo ""
echo "========================================="
echo "   Quick Activation Steps"
echo "========================================="
echo ""
echo "1. Open Admin Console (URL above)"
echo "2. Login: admin / admin"
echo "3. Select realm: finance"
echo "4. Go to: Realm Settings > Themes"
echo "5. Set Login Theme = finai"
echo "6. Save"
echo "7. Test with URL #2 above"
echo ""
echo -e "${GREEN}Done! Your theme is ready to activate.${NC}"
echo ""
