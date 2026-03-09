#!/bin/bash
# ============================================================
# Setup pgvector Extension for AIService PostgreSQL
# ============================================================
# This script installs pgvector extension in PostgreSQL
# and verifies the database is ready for RAG

set -e

echo "=========================================="
echo "🚀 Setting up pgvector for AIService"
echo "=========================================="

# Database connection details
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-finance_ai}"
DB_USER="${DB_USER:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-postgres}"

echo ""
echo "📊 Database Configuration:"
echo "   Host: $DB_HOST"
echo "   Port: $DB_PORT"
echo "   Database: $DB_NAME"
echo "   User: $DB_USER"
echo ""

# Check if PostgreSQL is running
echo "🔍 Checking PostgreSQL connection..."
if ! PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d postgres -c "SELECT version();" > /dev/null 2>&1; then
    echo "❌ ERROR: Cannot connect to PostgreSQL"
    echo "   Please ensure PostgreSQL is running on $DB_HOST:$DB_PORT"
    exit 1
fi
echo "✅ PostgreSQL is running"

# Create database if it doesn't exist
echo ""
echo "📦 Checking if database '$DB_NAME' exists..."
if ! PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -lqt | cut -d \| -f 1 | grep -qw $DB_NAME; then
    echo "   Creating database '$DB_NAME'..."
    PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -c "CREATE DATABASE $DB_NAME;"
    echo "✅ Database created"
else
    echo "✅ Database already exists"
fi

# Check if pgvector extension is installed in PostgreSQL
echo ""
echo "🔍 Checking pgvector extension..."
if ! PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "SELECT * FROM pg_extension WHERE extname = 'vector';" | grep -q "vector"; then
    echo "   Installing pgvector extension..."

    # Try to create extension
    if PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "CREATE EXTENSION IF NOT EXISTS vector;" 2>&1 | grep -q "could not open extension control file"; then
        echo ""
        echo "⚠️  WARNING: pgvector is not installed in PostgreSQL"
        echo ""
        echo "To install pgvector, you have two options:"
        echo ""
        echo "Option 1: Using Docker (Recommended for development)"
        echo "   docker pull pgvector/pgvector:pg16"
        echo "   docker run -d --name postgres-pgvector \\"
        echo "       -e POSTGRES_PASSWORD=postgres \\"
        echo "       -p 5432:5432 \\"
        echo "       pgvector/pgvector:pg16"
        echo ""
        echo "Option 2: Install on host system"
        echo "   Ubuntu/Debian:"
        echo "      sudo apt install postgresql-16-pgvector"
        echo ""
        echo "   macOS (Homebrew):"
        echo "      brew install pgvector"
        echo ""
        echo "   From source:"
        echo "      git clone --branch v0.5.1 https://github.com/pgvector/pgvector.git"
        echo "      cd pgvector"
        echo "      make"
        echo "      sudo make install"
        echo ""
        exit 1
    else
        echo "✅ pgvector extension installed"
    fi
else
    echo "✅ pgvector extension already installed"
fi

# Verify pgvector version
echo ""
echo "📋 pgvector Information:"
PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';"

# Test vector operations
echo ""
echo "🧪 Testing vector operations..."
PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME <<EOF
-- Create test table
CREATE TABLE IF NOT EXISTS vector_test (id int, embedding vector(3));

-- Insert test data
INSERT INTO vector_test VALUES (1, '[1,2,3]'), (2, '[4,5,6]') ON CONFLICT DO NOTHING;

-- Test cosine distance
SELECT
    id,
    embedding <=> '[3,1,2]' AS cosine_distance
FROM vector_test
ORDER BY embedding <=> '[3,1,2]'
LIMIT 2;

-- Cleanup
DROP TABLE vector_test;
EOF

echo ""
echo "✅ Vector operations working correctly"

# Check Flyway migrations
echo ""
echo "📝 Flyway Migration Status:"
if PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "\dt flyway_schema_history" 2>&1 | grep -q "Did not find any relation"; then
    echo "   No migrations run yet. Flyway will run migrations on AIService startup."
else
    echo "   Existing migrations:"
    PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "SELECT version, description, installed_on, success FROM flyway_schema_history ORDER BY installed_rank;"
fi

echo ""
echo "=========================================="
echo "✅ pgvector setup complete!"
echo "=========================================="
echo ""
echo "Next steps:"
echo "1. Start AIService: ./gradlew bootRun"
echo "2. Flyway will automatically run V2__add_vector_store_tables.sql"
echo "3. Download embedding model: ollama pull nomic-embed-text"
echo "4. Test RAG functionality with chat queries"
echo ""
