#!/bin/bash

set -e

echo "Setting up environment for Docker Compose..."

# Create mongodb directory if it doesn't exist
mkdir -p mongodb

# Generate MongoDB keyfile if it doesn't exist
if [ ! -f mongodb/keyfile ]; then
    echo "Generating MongoDB keyfile..."
    openssl rand -base64 756 > mongodb/keyfile
    chmod 400 mongodb/keyfile
    echo "✓ MongoDB keyfile created successfully"
else
    echo "✓ MongoDB keyfile already exists"
    # Ensure proper permissions
    chmod 400 mongodb/keyfile
    echo "✓ Verified keyfile permissions"
fi

# Create necessary directories for volumes
mkdir -p .data/minio
mkdir -p data/bitnami/kafka1

echo ""
echo "✓ Environment setup complete!"
echo ""
echo "You can now run: docker compose up -d"
