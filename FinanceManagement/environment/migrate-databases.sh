#!/bin/bash

# Script để migrate data từ các database instances cũ sang instances mới đã gom nhóm
# Chạy: chmod +x migrate-databases.sh && ./migrate-databases.sh

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${YELLOW}=========================================="
echo "  Database Migration Script"
echo "==========================================${NC}"
echo ""
echo "Script này sẽ migrate data từ:"
echo "  - 3 MySQL instances (ports 3316, 3317, 3318) → 1 MySQL instance (port 3306)"
echo "  - 4 MongoDB instances (ports 27017, 27018, 27019, 27020) → 1 MongoDB instance (port 27017)"
echo ""

# Kiểm tra các containers cũ có đang chạy không
echo -e "${YELLOW}[1/5]${NC} Kiểm tra containers cũ..."
OLD_CONTAINERS=("mysql_user" "mysql_finance" "mysql_key" "mongodb" "mongodb-files" "mongodb-notifications" "mongodb-ai")
RUNNING_OLD=0

for container in "${OLD_CONTAINERS[@]}"; do
    if docker ps --format '{{.Names}}' | grep -q "^${container}$"; then
        echo -e "  ${YELLOW}⚠${NC}  Container '${container}' đang chạy"
        RUNNING_OLD=1
    fi
done

if [ $RUNNING_OLD -eq 1 ]; then
    echo ""
    read -p "Các containers cũ đang chạy. Bạn có muốn dừng chúng trước khi migrate? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo "Đang dừng containers cũ..."
        docker-compose down 2>/dev/null || true
    else
        echo -e "${RED}⚠${NC}  Vui lòng dừng containers cũ trước khi migrate!"
        exit 1
    fi
fi

# Kiểm tra containers mới
echo ""
echo -e "${YELLOW}[2/5]${NC} Kiểm tra containers mới..."
if ! docker ps --format '{{.Names}}' | grep -q "^mysql_main$"; then
    echo -e "${YELLOW}⚠${NC}  Container 'mysql_main' chưa chạy. Đang khởi động..."
    docker-compose up -d mysql-main
    echo "Đợi MySQL khởi động..."
    sleep 10
fi

if ! docker ps --format '{{.Names}}' | grep -q "^mongodb_main$"; then
    echo -e "${YELLOW}⚠${NC}  Container 'mongodb_main' chưa chạy. Đang khởi động..."
    docker-compose up -d mongodb-main
    echo "Đợi MongoDB khởi động..."
    sleep 10
fi

# Backup data từ containers cũ (nếu còn chạy)
echo ""
echo -e "${YELLOW}[3/5]${NC} Backup data từ containers cũ..."
BACKUP_DIR="./database-backups/$(date +%Y%m%d_%H%M%S)"
mkdir -p "$BACKUP_DIR"

# Backup MySQL
echo "Backing up MySQL databases..."
for port in 3316 3317 3318; do
    container_name=""
    db_name=""
    case $port in
        3316) container_name="mysql_user"; db_name="user" ;;
        3317) container_name="mysql_finance"; db_name="finance" ;;
        3318) container_name="mysql_key"; db_name="key" ;;
    esac
    
    if docker ps --format '{{.Names}}' | grep -q "^${container_name}$"; then
        echo "  Backing up ${db_name} from port ${port}..."
        docker exec "${container_name}" mysqldump -uroot -proot "${db_name}" > "${BACKUP_DIR}/mysql_${db_name}.sql" 2>/dev/null || echo "    Không thể backup ${db_name}"
    fi
done

# Backup MongoDB
echo "Backing up MongoDB databases..."
for port in 27017 27018 27019 27020; do
    container_name=""
    db_name=""
    case $port in
        27017) container_name="mongodb"; db_name="finance" ;;
        27018) container_name="mongodb-files"; db_name="files" ;;
        27019) container_name="mongodb-notifications"; db_name="notifications" ;;
        27020) container_name="mongodb-ai"; db_name="finance_ai_chat" ;;
    esac
    
    if docker ps --format '{{.Names}}' | grep -q "^${container_name}$"; then
        echo "  Backing up ${db_name} from port ${port}..."
        docker exec "${container_name}" mongodump --archive --gzip --username=root --password=root --authenticationDatabase=admin --db="${db_name}" > "${BACKUP_DIR}/mongodb_${db_name}.archive.gz" 2>/dev/null || echo "    Không thể backup ${db_name}"
    fi
done

echo -e "${GREEN}✓${NC} Backup hoàn thành tại: ${BACKUP_DIR}"

# Restore vào containers mới
echo ""
echo -e "${YELLOW}[4/5]${NC} Restore data vào containers mới..."
read -p "Bạn có muốn restore data từ backup? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    # Restore MySQL
    echo "Restoring MySQL databases..."
    for db in user finance key; do
        if [ -f "${BACKUP_DIR}/mysql_${db}.sql" ]; then
            echo "  Restoring ${db}..."
            docker exec -i mysql_main mysql -uroot -proot < "${BACKUP_DIR}/mysql_${db}.sql" || echo "    Không thể restore ${db}"
        fi
    done
    
    # Restore MongoDB
    echo "Restoring MongoDB databases..."
    for db in finance files notifications finance_ai_chat; do
        if [ -f "${BACKUP_DIR}/mongodb_${db}.archive.gz" ]; then
            echo "  Restoring ${db}..."
            docker exec -i mongodb_main mongorestore --archive --gzip --username=root --password=root --authenticationDatabase=admin < "${BACKUP_DIR}/mongodb_${db}.archive.gz" || echo "    Không thể restore ${db}"
        fi
    done
    echo -e "${GREEN}✓${NC} Restore hoàn thành"
else
    echo "Bỏ qua restore. Containers mới sẽ tạo databases trống."
fi

# Verify
echo ""
echo -e "${YELLOW}[5/5]${NC} Verify connections..."
echo "Kiểm tra MySQL..."
docker exec mysql_main mysql -uroot -proot -e "SHOW DATABASES;" | grep -E "(user|finance|key)" && echo -e "${GREEN}✓${NC} MySQL databases OK" || echo -e "${RED}✗${NC} MySQL databases có vấn đề"

echo "Kiểm tra MongoDB..."
docker exec mongodb_main mongosh -u root -p root --authenticationDatabase admin --eval "db.adminCommand('listDatabases')" | grep -E "(finance|files|notifications|chat)" && echo -e "${GREEN}✓${NC} MongoDB databases OK" || echo -e "${YELLOW}⚠${NC}  MongoDB databases sẽ được tạo khi có data"

echo ""
echo -e "${GREEN}=========================================="
echo "  Migration hoàn thành!"
echo "==========================================${NC}"
echo ""
echo "Các bước tiếp theo:"
echo "1. Kiểm tra các services có kết nối được với databases mới không"
echo "2. Nếu mọi thứ OK, có thể xóa containers và volumes cũ:"
echo "   docker-compose down"
echo "   docker volume rm <old_volume_names>"
echo "3. Backup được lưu tại: ${BACKUP_DIR}"
