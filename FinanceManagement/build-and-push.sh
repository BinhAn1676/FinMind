#!/bin/bash
set -e

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
FE_DIR="$BASE_DIR/../FinanceManagementFE"
REGISTRY="${REGISTRY:-binhan21/}"
TAG="${TAG:-1.0}"

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
echo " Finance Management - Build & Push"
echo " Registry : ${REGISTRY}"
echo " Tag      : ${TAG}"
echo "======================================"

# Đăng nhập Docker Hub (nếu chưa login)
if ! docker info | grep -q "Username"; then
  echo ">>> Chưa đăng nhập Docker Hub, đang login..."
  docker login
fi

# Build & Push Backend Services
for dir in "${!SERVICES[@]}"; do
  image="${SERVICES[$dir]}"
  full_image="${REGISTRY}${image}:${TAG}"
  echo ""
  echo ">>> [$dir] Building: $full_image"
  cd "$BASE_DIR/$dir"
  ./gradlew build -x test --no-daemon
  docker build -t "$full_image" .
  echo ">>> [$dir] Pushing: $full_image"
  docker push "$full_image"
  echo ">>> [$dir] Done ✓"
done

# Build & Push Frontend
echo ""
full_fe="${REGISTRY}frontend:${TAG}"
echo ">>> [FinanceManagementFE] Building: $full_fe"
cd "$FE_DIR"
docker build -t "$full_fe" .
echo ">>> [FinanceManagementFE] Pushing: $full_fe"
docker push "$full_fe"
echo ">>> [FinanceManagementFE] Done ✓"

echo ""
echo "======================================"
echo " All images pushed to Docker Hub!"
echo " https://hub.docker.com/u/binhan21"
echo "======================================"
