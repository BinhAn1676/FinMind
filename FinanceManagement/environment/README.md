# Finance Management - Environment Setup

This directory contains the Docker Compose configuration for running all infrastructure services required by the Finance Management microservices application.

## Prerequisites

- Docker Engine 20.10+
- Docker Compose V2+
- OpenSSL (for generating MongoDB keyfile)

## Quick Start

### First Time Setup

1. Run the setup script to initialize the environment:
```bash
./setup.sh
```

This script will:
- Create the MongoDB keyfile with proper permissions
- Create necessary directories for Docker volumes

2. Start all services:
```bash
docker compose up -d
```

3. Check service health:
```bash
docker compose ps
```

### Subsequent Runs

After the initial setup, you can simply run:
```bash
docker compose up -d
```

## Services Overview

### Authentication & Authorization

**Keycloak** (Port: 7080)
- Identity and Access Management
- SSO (Single Sign-On) provider
- User authentication and authorization
- Admin Console: http://localhost:7080
- Default credentials: admin/admin

### Databases

**MySQL Instances**

1. **mysql-user** (Port: 3316)
   - Database for User Service
   - Database name: `user`
   - Root password: `root`

2. **mysql-finance** (Port: 3317)
   - Database for Finance Service
   - Database name: `finance`
   - Root password: `root`

3. **mysql-key** (Port: 3318)
   - Database for Key Management Service
   - Database name: `key`
   - Root password: `root`

**MongoDB Instances** (Replica Sets)

1. **mongodb** (Port: 27017) - Replica Set: rs0
   - Primary database for Finance Service
   - Used for group planning and financial data
   - Root credentials: root/root

2. **mongodb-files** (Port: 27018) - Replica Set: rs1
   - Database for File Service
   - Stores file metadata
   - Root credentials: root/root

3. **mongodb-notifications** (Port: 27019) - Replica Set: rs2
   - Database for Notification Service & Chat Service
   - Stores notifications and chat messages
   - Root credentials: root/root

### Message Broker

**Kafka** (Port: 9194 external, 9092 internal)
- Event streaming platform
- Used for asynchronous communication between microservices
- KRaft mode (no ZooKeeper required)
- Bootstrap server: localhost:9194

**Kafka UI** (Port: 8080)
- Web-based Kafka management interface
- Monitor topics, messages, and consumer groups
- Access: http://localhost:8080

### Cache & Storage

**Redis** (Port: 6379)
- In-memory data store
- Used for caching and session management
- RedisLabs module with additional features

**MinIO** (Port: 9000 API, 9001 Console)
- S3-compatible object storage
- Used for storing files and attachments
- Console: http://localhost:9001
- Credentials: loki/supersecret

## Network Configuration

All services are connected via the `event-network` bridge network, allowing them to communicate with each other using service names as hostnames.

## Volume Management

The following Docker volumes are created for data persistence:
- `mysql_data` - User Service database
- `mysql_finance_data` - Finance Service database
- `mysql_key_data` - Key Management Service database
- `mongodb_data` - Finance MongoDB
- `mongodb_files_data` - File Service MongoDB
- `mongodb_notifications_data` - Notification/Chat MongoDB

## Troubleshooting

### MongoDB Keyfile Issues

If you encounter errors like "error while creating mount source path", the keyfile may be missing or have incorrect permissions:

```bash
# Run the setup script again
./setup.sh
```

Or manually recreate the keyfile:
```bash
openssl rand -base64 756 > mongodb/keyfile
chmod 400 mongodb/keyfile
```

### Port Conflicts

If you get port binding errors, check if the required ports are already in use:
```bash
sudo lsof -i :7080  # Keycloak
sudo lsof -i :9194  # Kafka
sudo lsof -i :6379  # Redis
```

### Reset Everything

To completely reset the environment (WARNING: This will delete all data):
```bash
docker compose down -v
rm -rf .data data
./setup.sh
docker compose up -d
```

### View Logs

To view logs for a specific service:
```bash
docker compose logs -f [service-name]
```

Example:
```bash
docker compose logs -f kafka1
docker compose logs -f mongodb
```

## Service Dependencies

The services have health checks and dependencies configured:
- MongoDB init containers wait for MongoDB instances to be healthy
- Kafka UI waits for Kafka to be healthy
- All services must be healthy before the application services can connect

## Updating Configuration

### Kafka External IP

If you need to change the Kafka external IP (line 255 in docker-compose.yml):
```yaml
KAFKA_CFG_ADVERTISED_LISTENERS: EXTERNAL://YOUR_HOST_IP:9194,PLAINTEXT://kafka1:9092
```

Get your host IP:
```bash
hostname -I
```

## Security Notes

- The MongoDB keyfile is NOT committed to git for security reasons
- Each environment generates its own unique keyfile
- Default passwords are used for development only
- Change all passwords before deploying to production
- Consider using Docker secrets or environment variables for sensitive data

## Health Checks

All critical services have health checks configured:
- MySQL: Ping test every 10s
- MongoDB: Connection status check every 10s
- Kafka: Topic list check every 5s
- MinIO: Health endpoint check every 15s

Check health status:
```bash
docker compose ps
```

Services will show as "healthy" when ready to accept connections.
