# Key Management Service

Service quản lý mã hóa và giải mã dữ liệu sử dụng Google Cloud KMS và AES encryption.

## Tính năng

- **Generate AES Key**: Tạo AES key cho user và mã hóa bằng master key từ Google Cloud KMS
- **Encrypt Data**: Mã hóa dữ liệu sử dụng AES key của user
- **Decrypt Data**: Giải mã dữ liệu sử dụng AES key của user
- **Redis Caching**: Cache master key trong Redis để tăng hiệu suất
- **Error Handling**: Xử lý lỗi timeout và kết nối

## API Endpoints

### 1. Generate AES Key
```
POST /api/v1/keys/generate
Content-Type: application/json

{
    "userId": "user123",
    "keyVersion": "v1"
}
```

### 2. Encrypt Data
```
POST /api/v1/keys/encrypt
Content-Type: application/json

{
    "userId": "user123",
    "data": "sensitive data to encrypt"
}
```

### 3. Decrypt Data
```
POST /api/v1/keys/decrypt
Content-Type: application/json

{
    "userId": "user123",
    "encryptedData": "encrypted_data_here"
}
```

### 4. Deactivate User Key
```
DELETE /api/v1/keys/{userId}
```

### 5. Health Check
```
GET /api/v1/keys/health
```

## Cấu hình

### Google Cloud KMS Setup

1. **Tạo Service Account Key:**
   ```bash
   # Download service account key từ Google Cloud Console
   # Lưu file JSON vào: /home/annb/Documents/Importants/your-service-account-key.json
   ```

2. **Cấu hình Environment Variables:**
   ```bash
   # Chạy script setup
   ./setup-google-cloud.sh
   
   # Hoặc set manual:
   export GOOGLE_CLOUD_KMS_ENABLED=true
   export GOOGLE_CLOUD_CREDENTIALS_FILE=/home/annb/Documents/Importants/your-service-account-key.json
   export GOOGLE_CLOUD_PROJECT_ID=your-project-id
   export GOOGLE_CLOUD_KMS_LOCATION=global
   export GOOGLE_CLOUD_KMS_KEY_RING=finance-key-ring
   export GOOGLE_CLOUD_KMS_CRYPTO_KEY=master-key
   ```

3. **Tạo Key Ring và Crypto Key trong Google Cloud KMS:**
   ```bash
   # Tạo Key Ring
   gcloud kms keyrings create finance-key-ring --location global
   
   # Tạo Crypto Key
   gcloud kms keys create master-key --keyring finance-key-ring --location global --purpose encryption
   ```

### Environment Variables

```bash
# Google Cloud KMS Configuration
GOOGLE_CLOUD_KMS_ENABLED=true
GOOGLE_CLOUD_CREDENTIALS_FILE=/home/annb/Documents/Importants/your-service-account-key.json
GOOGLE_CLOUD_PROJECT_ID=your-project-id
GOOGLE_CLOUD_KMS_LOCATION=global
GOOGLE_CLOUD_KMS_KEY_RING=finance-key-ring
GOOGLE_CLOUD_KMS_CRYPTO_KEY=master-key

# Database Configuration
SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/finance
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=root

# Redis Configuration
SPRING_DATA_REDIS_HOST=redis
SPRING_DATA_REDIS_PORT=6379
```

## Kiến trúc

### Luồng hoạt động

1. **Generate Key**:
   - Tạo AES key mới cho user
   - Lấy local master key từ Redis cache hoặc database
   - Nếu chưa có local master key: tạo mới và mã hóa bằng Google Cloud KMS
   - Mã hóa user AES key bằng local master key
   - Lưu encrypted user AES key vào database

2. **Encrypt Data**:
   - Lấy encrypted user AES key từ database
   - Lấy local master key từ Redis cache hoặc database
   - Giải mã user AES key bằng local master key
   - Mã hóa dữ liệu bằng user AES key

3. **Decrypt Data**:
   - Lấy encrypted user AES key từ database
   - Lấy local master key từ Redis cache hoặc database
   - Giải mã user AES key bằng local master key
   - Giải mã dữ liệu bằng user AES key

### Kiến trúc mã hóa 2 tầng

1. **Tầng 1**: Google Cloud KMS → mã hóa **Local Master Key** (lưu trong DB)
2. **Tầng 2**: Local Master Key → mã hóa **User AES Key** (lưu trong DB)
3. **Tầng 3**: User AES Key → mã hóa **User Data** (không lưu trong DB)

### Redis Caching

- Local master key được cache trong Redis với TTL 1 giờ
- Nếu local master key không có trong cache, sẽ lấy từ database
- Nếu chưa có local master key trong database, sẽ tạo mới và mã hóa bằng Google Cloud KMS
- Sau khi sử dụng, local master key được lưu lại vào Redis

### Error Handling

- Nếu không có master key: Log error và throw exception
- Nếu timeout hoặc lỗi kết nối: Tiếp tục xử lý với fallback
- Nếu user không có AES key: Trả về error message

## Database Schema

### local_master_keys Table

```sql
CREATE TABLE local_master_keys (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    key_name VARCHAR(255) NOT NULL UNIQUE,
    encrypted_master_key TEXT NOT NULL,
    key_version VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at TIMESTAMP,
    -- BaseEntity fields (inherited)
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255),
    updated_at TIMESTAMP,
    updated_by VARCHAR(255)
);
```

### aes_keys Table

```sql
CREATE TABLE aes_keys (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL UNIQUE,
    encrypted_aes_key TEXT NOT NULL,
    key_version VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at TIMESTAMP,
    -- BaseEntity fields (inherited)
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255),
    updated_at TIMESTAMP,
    updated_by VARCHAR(255)
);
```

## Docker Deployment

Service đã được cấu hình trong `docker-compose.yml`:

```yaml
keymanagementservice:
  image: "keymanagementservice:1.0"
  container_name: keymanagementservice
  ports:
    - "8085:8085"
  depends_on:
    mysql:
      condition: service_healthy
    redis:
      condition: service_started
```

## Build và Deploy

```bash
# Build service
cd KeyManagementService
./gradlew build

# Build Docker image
docker build -t keymanagementservice:1.0 .

# Deploy với docker-compose
cd ../environment
docker-compose up keymanagementservice
```

## Monitoring

Service hỗ trợ các endpoint monitoring:

- Health Check: `/actuator/health`
- Metrics: `/actuator/metrics`
- Info: `/actuator/info`

## Security

- Sử dụng Google Cloud KMS để quản lý master key
- AES key của user được mã hóa bằng master key
- Master key được cache trong Redis với TTL
- Tất cả dữ liệu nhạy cảm đều được mã hóa
