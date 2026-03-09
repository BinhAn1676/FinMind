#!/bin/bash
# =============================================================
# measure-ram.sh - Đo RAM usage của toàn bộ Docker stack
# =============================================================
# Cách dùng:
#   ./measure-ram.sh              → snapshot 1 lần
#   ./measure-ram.sh --watch      → cập nhật mỗi 5 giây
#   ./measure-ram.sh --watch 10   → cập nhật mỗi 10 giây
# =============================================================

WATCH=false
INTERVAL=5

for arg in "$@"; do
  case $arg in
    --watch) WATCH=true ;;
    [0-9]*) INTERVAL=$arg ;;
  esac
done

YELLOW='\033[1;33m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
RED='\033[0;31m'
BOLD='\033[1m'
NC='\033[0m'

snapshot() {
  clear
  echo -e "${BOLD}============================================================${NC}"
  echo -e "${BOLD}  RAM Monitor - Finance Management Stack${NC}"
  echo -e "${BOLD}  $(date '+%Y-%m-%d %H:%M:%S')${NC}"
  echo -e "${BOLD}============================================================${NC}"

  # Lấy stats từ tất cả containers đang chạy
  STATS=$(docker stats --no-stream --format "{{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.CPUPerc}}" 2>/dev/null)

  if [ -z "$STATS" ]; then
    echo -e "${RED}  Không có container nào đang chạy!${NC}"
    echo -e "  Hãy chạy: docker compose up -d"
    return
  fi

  # Header bảng
  printf "\n${CYAN}%-22s %18s %8s %8s${NC}\n" "CONTAINER" "RAM USED / LIMIT" "RAM %" "CPU %"
  echo "  ──────────────────────────────────────────────────────"

  TOTAL_MIB=0

  # Nhóm theo loại
  declare -A GROUPS
  GROUPS[infra]="redis mysql_main mongodb_main postgres_ai kafka1 minio keycloak"
  GROUPS[core]="configserver eurekaserver gatewayserver"
  GROUPS[services]="users finance notification fileservice keymanagement chat aiservice"
  GROUPS[frontend]="frontend"

  declare -A GROUP_LABELS
  GROUP_LABELS[infra]="Infrastructure"
  GROUP_LABELS[core]="Core (Config/Eureka/Gateway)"
  GROUP_LABELS[services]="Microservices"
  GROUP_LABELS[frontend]="Frontend"

  for group in infra core services frontend; do
    echo ""
    echo -e "  ${YELLOW}▸ ${GROUP_LABELS[$group]}${NC}"
    GROUP_TOTAL=0

    for container in ${GROUPS[$group]}; do
      LINE=$(echo "$STATS" | grep "^${container}" | head -1)
      if [ -n "$LINE" ]; then
        NAME=$(echo "$LINE" | awk '{print $1}')
        MEM_USAGE=$(echo "$LINE" | awk '{print $2}')   # e.g. "512MiB"
        MEM_LIMIT=$(echo "$LINE" | awk '{print $4}')   # e.g. "1GiB"
        MEM_PERC=$(echo "$LINE" | awk '{print $5}')
        CPU_PERC=$(echo "$LINE" | awk '{print $6}')

        # Convert usage sang MiB để tính tổng
        MIB_VAL=$(echo "$MEM_USAGE" | sed 's/MiB//' | sed 's/GiB/*1024/' | bc 2>/dev/null | awk '{printf "%.0f", $1}')
        if [ -n "$MIB_VAL" ] && [ "$MIB_VAL" -gt 0 ] 2>/dev/null; then
          GROUP_TOTAL=$((GROUP_TOTAL + MIB_VAL))
          TOTAL_MIB=$((TOTAL_MIB + MIB_VAL))
        fi

        # Màu theo % RAM
        PERC_NUM=$(echo "$MEM_PERC" | tr -d '%')
        if (( $(echo "$PERC_NUM > 80" | bc -l 2>/dev/null || echo 0) )); then
          COLOR=$RED
        elif (( $(echo "$PERC_NUM > 50" | bc -l 2>/dev/null || echo 0) )); then
          COLOR=$YELLOW
        else
          COLOR=$GREEN
        fi

        printf "  ${COLOR}%-22s %18s %8s %8s${NC}\n" \
          "$NAME" \
          "$MEM_USAGE / $MEM_LIMIT" \
          "$MEM_PERC" \
          "$CPU_PERC"
      fi
    done

    # Các container không thuộc nhóm nào (kafka-ui, etc.)
    for container in $(echo "$STATS" | awk '{print $1}'); do
      FOUND=false
      for g in infra core services frontend; do
        if echo "${GROUPS[$g]}" | grep -qw "$container"; then
          FOUND=true
          break
        fi
      done
      if [ "$FOUND" = false ] && [ "$group" = "infra" ]; then
        LINE=$(echo "$STATS" | grep "^${container}" | head -1)
        if [ -n "$LINE" ]; then
          NAME=$(echo "$LINE" | awk '{print $1}')
          MEM_USAGE=$(echo "$LINE" | awk '{print $2}')
          MEM_LIMIT=$(echo "$LINE" | awk '{print $4}')
          MEM_PERC=$(echo "$LINE" | awk '{print $5}')
          CPU_PERC=$(echo "$LINE" | awk '{print $6}')
          printf "  ${GREEN}%-22s %18s %8s %8s${NC}\n" \
            "$NAME (other)" \
            "$MEM_USAGE / $MEM_LIMIT" \
            "$MEM_PERC" \
            "$CPU_PERC"
        fi
      fi
    done

    echo -e "  ${YELLOW}  Subtotal: ~${GROUP_TOTAL} MiB${NC}"
  done

  echo ""
  echo "  ══════════════════════════════════════════════════════"

  # Tổng Docker containers
  TOTAL_GIB=$(echo "scale=2; $TOTAL_MIB / 1024" | bc)
  echo -e "  ${BOLD}TỔNG CONTAINERS :  ${TOTAL_MIB} MiB  (~${TOTAL_GIB} GiB)${NC}"

  # RAM hệ thống thực tế
  SYS_TOTAL=$(free -m | awk '/^Mem:/{print $2}')
  SYS_USED=$(free -m | awk '/^Mem:/{print $3}')
  SYS_FREE=$(free -m | awk '/^Mem:/{print $4}')
  SYS_AVAIL=$(free -m | awk '/^Mem:/{print $7}')
  SYS_TOTAL_GIB=$(echo "scale=1; $SYS_TOTAL / 1024" | bc)
  SYS_USED_GIB=$(echo "scale=1; $SYS_USED / 1024" | bc)
  SYS_AVAIL_GIB=$(echo "scale=1; $SYS_AVAIL / 1024" | bc)

  echo ""
  echo -e "  ${CYAN}RAM HỆ THỐNG (bao gồm OS + Docker):${NC}"
  printf "  %-20s %s\n" "Tổng RAM máy:" "${SYS_TOTAL} MiB (${SYS_TOTAL_GIB} GiB)"
  printf "  %-20s %s\n" "Đang dùng:" "${SYS_USED} MiB (${SYS_USED_GIB} GiB)"
  printf "  %-20s %s\n" "Còn available:" "${SYS_AVAIL} MiB (${SYS_AVAIL_GIB} GiB)"

  # Khuyến nghị VPS
  echo ""
  echo "  ══════════════════════════════════════════════════════"
  echo -e "  ${BOLD}GỢI Ý CHỌN VPS RAM:${NC}"
  RECOMMEND=$((TOTAL_MIB / 1024 + 4))  # +4GB buffer cho OS + spike
  if [ "$RECOMMEND" -le 8 ]; then
    echo -e "  ${GREEN}→ 8 GB RAM đủ dùng (có ~$((8192 - TOTAL_MIB)) MiB buffer)${NC}"
  elif [ "$RECOMMEND" -le 16 ]; then
    echo -e "  ${YELLOW}→ 16 GB RAM phù hợp (có ~$((16384 - TOTAL_MIB)) MiB buffer)${NC}"
  else
    echo -e "  ${RED}→ Cần ít nhất ${RECOMMEND} GB RAM${NC}"
  fi
  echo "  ══════════════════════════════════════════════════════"

  RUNNING=$(echo "$STATS" | grep -c . 2>/dev/null || echo 0)
  TOTAL_CONTAINERS=$(docker ps -q 2>/dev/null | wc -l)
  echo -e "\n  Containers đang chạy: ${RUNNING}/${TOTAL_CONTAINERS}"

  if [ "$WATCH" = true ]; then
    echo -e "\n  ${CYAN}[Đang watch - cập nhật mỗi ${INTERVAL}s | Ctrl+C để thoát]${NC}"
  fi
}

if [ "$WATCH" = true ]; then
  while true; do
    snapshot
    sleep "$INTERVAL"
  done
else
  snapshot
fi
