#!/bin/bash
set -e

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
FE_DIR="$BASE_DIR/../FinanceManagementFE"

declare -A SERVICES=(
  [ConfigServer]=configserver
  [EurekaServer]=eurekaserver
  [UserService]=users
  [FinanceService]=finance
  [NotificationService]=notification
  [FileService]=fileservice
  [KeyManagementService]=keymanagement
  [ChatService]=chat
  [gatewayserver]=gatewayserver
  [AIService]=aiservice
)

echo "======================================"
echo " Finance Management - Build All Images"
echo "======================================"

# Build Backend Services
for dir in "${!SERVICES[@]}"; do
  image="${SERVICES[$dir]}"
  echo ""
  echo ">>> [$dir] Building image: $image:1.0"
  cd "$BASE_DIR/$dir"
  ./gradlew build -x test --no-daemon
  docker build -t "$image:1.0" .
  echo ">>> [$dir] Done: $image:1.0 ✓"
done

# Build Frontend
echo ""
echo ">>> [FinanceManagementFE] Building image: frontend:1.0"
cd "$FE_DIR"
docker build -t "frontend:1.0" .
echo ">>> [FinanceManagementFE] Done: frontend:1.0 ✓"

echo ""
echo "======================================"
echo " All images built successfully!"
echo "======================================"
docker images | grep -E "configserver|eurekaserver|users|finance|notification|fileservice|keymanagement|chat|gatewayserver|aiservice|frontend"
