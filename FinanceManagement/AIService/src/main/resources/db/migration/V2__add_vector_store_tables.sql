-- ============================================================
-- AIService RAG (Retrieval-Augmented Generation) Setup
-- ============================================================
-- Description: Adds pgvector extension and creates tables for
-- storing embeddings to enable context-aware AI responses.
--
-- Purpose:
-- 1. Enable pgvector extension for vector similarity search
-- 2. Create transaction_embeddings table for semantic search
-- 3. Create financial_knowledge table for advice retrieval
-- 4. Create indexes for fast vector similarity queries
--
-- Vector Dimensions: 768 (nomic-embed-text model)
-- ============================================================

-- ============================================================
-- 1. Enable pgvector Extension
-- ============================================================
CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================================
-- 2. Transaction Embeddings Table
-- ============================================================
-- Stores vector embeddings of user transactions for semantic search
-- Allows AI to find similar past transactions based on meaning

CREATE TABLE IF NOT EXISTS transaction_embeddings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Transaction reference
    user_id VARCHAR(255) NOT NULL,
    transaction_id BIGINT NOT NULL,

    -- Transaction details (denormalized for fast access)
    description TEXT,
    category VARCHAR(100),
    amount DECIMAL(15, 2),
    transaction_date DATE NOT NULL,
    transaction_type VARCHAR(20) CHECK (transaction_type IN ('INCOME', 'EXPENSE')),

    -- Vector embedding (768 dimensions for nomic-embed-text)
    embedding vector(768) NOT NULL,

    -- Metadata
    embedding_model VARCHAR(50) NOT NULL DEFAULT 'nomic-embed-text',
    embedding_version VARCHAR(20) DEFAULT '1.5',

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
    CONSTRAINT unique_transaction_embedding UNIQUE (user_id, transaction_id)
);

-- Indexes for transaction embeddings
CREATE INDEX idx_txn_emb_user_id ON transaction_embeddings(user_id);
CREATE INDEX idx_txn_emb_date ON transaction_embeddings(transaction_date DESC);
CREATE INDEX idx_txn_emb_category ON transaction_embeddings(category);

-- HNSW index for fast vector similarity search (cosine distance)
-- Using m=16, ef_construction=64 for good balance between speed and accuracy
CREATE INDEX idx_txn_emb_vector_cosine ON transaction_embeddings
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- Alternative: IVFFlat index (faster build, slightly slower search)
-- Uncomment if you prefer IVFFlat over HNSW
-- CREATE INDEX idx_txn_emb_vector_ivfflat ON transaction_embeddings
--     USING ivfflat (embedding vector_cosine_ops)
--     WITH (lists = 100);

-- ============================================================
-- 3. Financial Knowledge Base Table
-- ============================================================
-- Stores financial advice, tips, and domain knowledge
-- Enables AI to retrieve relevant information for user queries

CREATE TABLE IF NOT EXISTS financial_knowledge (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Content
    topic VARCHAR(255) NOT NULL,
    category VARCHAR(100),  -- 'SAVING', 'SPENDING', 'BUDGETING', 'INVESTING', etc.
    content TEXT NOT NULL,

    -- Vector embedding
    embedding vector(768) NOT NULL,

    -- Metadata
    language VARCHAR(10) DEFAULT 'vi',
    tags TEXT[],  -- Array of tags for filtering
    source VARCHAR(255),  -- Where this knowledge came from
    confidence_score DECIMAL(3,2) CHECK (confidence_score BETWEEN 0 AND 1),

    -- Status
    is_active BOOLEAN DEFAULT TRUE,
    priority INTEGER DEFAULT 0,  -- Higher priority = more important

    -- Additional metadata (JSON for flexibility)
    metadata JSONB,

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for financial knowledge
CREATE INDEX idx_fin_knowledge_category ON financial_knowledge(category);
CREATE INDEX idx_fin_knowledge_language ON financial_knowledge(language);
CREATE INDEX idx_fin_knowledge_active ON financial_knowledge(is_active) WHERE is_active = TRUE;
CREATE INDEX idx_fin_knowledge_tags ON financial_knowledge USING GIN (tags);
CREATE INDEX idx_fin_knowledge_metadata ON financial_knowledge USING GIN (metadata);

-- Vector similarity index for knowledge retrieval
CREATE INDEX idx_fin_knowledge_vector_cosine ON financial_knowledge
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- ============================================================
-- 4. Conversation Context Table (for RAG context injection)
-- ============================================================
-- Stores important context snippets from conversations
-- Enables AI to remember and reference previous discussions

CREATE TABLE IF NOT EXISTS conversation_context (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- User and conversation tracking
    user_id VARCHAR(255) NOT NULL,
    conversation_id VARCHAR(255) NOT NULL,

    -- Context snippet
    context_type VARCHAR(50) NOT NULL,  -- 'PREFERENCE', 'FACT', 'GOAL', 'QUESTION', etc.
    context_text TEXT NOT NULL,

    -- Vector embedding
    embedding vector(768) NOT NULL,

    -- Metadata
    importance_score DECIMAL(3,2) CHECK (importance_score BETWEEN 0 AND 1),
    referenced_count INTEGER DEFAULT 0,  -- How many times this context was used
    last_referenced_at TIMESTAMP,

    -- Expiration
    expires_at TIMESTAMP,

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for conversation context
CREATE INDEX idx_conv_context_user_id ON conversation_context(user_id, created_at DESC);
CREATE INDEX idx_conv_context_conversation_id ON conversation_context(conversation_id);
CREATE INDEX idx_conv_context_type ON conversation_context(context_type);
-- Note: Can't use CURRENT_TIMESTAMP in index predicate (not immutable)
-- Query filters will handle expiration check at runtime
CREATE INDEX idx_conv_context_expires ON conversation_context(user_id, expires_at);

-- Vector similarity index
CREATE INDEX idx_conv_context_vector_cosine ON conversation_context
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- ============================================================
-- 5. RAG Retrieval Metrics Table
-- ============================================================
-- Tracks RAG retrieval performance and relevance

CREATE TABLE IF NOT EXISTS rag_retrieval_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Query details
    user_id VARCHAR(255) NOT NULL,
    query_text TEXT NOT NULL,
    query_type VARCHAR(50),  -- 'TRANSACTION_SEARCH', 'KNOWLEDGE_LOOKUP', 'CONTEXT_RETRIEVAL'

    -- Retrieval results
    results_count INTEGER NOT NULL,
    top_similarity_score DECIMAL(5,4),  -- Highest similarity score from results
    avg_similarity_score DECIMAL(5,4),  -- Average similarity score

    -- Performance
    retrieval_time_ms INTEGER NOT NULL,
    embedding_generation_time_ms INTEGER,

    -- User feedback (optional - for improving RAG)
    was_helpful BOOLEAN,
    user_feedback TEXT,

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for metrics
CREATE INDEX idx_rag_metrics_user_id ON rag_retrieval_metrics(user_id, created_at DESC);
CREATE INDEX idx_rag_metrics_query_type ON rag_retrieval_metrics(query_type);
CREATE INDEX idx_rag_metrics_performance ON rag_retrieval_metrics(retrieval_time_ms);

-- ============================================================
-- 6. Helper Functions
-- ============================================================

-- Function to calculate cosine similarity (useful for debugging)
CREATE OR REPLACE FUNCTION cosine_similarity(a vector, b vector)
RETURNS FLOAT AS $$
BEGIN
    RETURN 1 - (a <=> b);
END;
$$ LANGUAGE plpgsql IMMUTABLE STRICT PARALLEL SAFE;

-- Function to find similar transactions
CREATE OR REPLACE FUNCTION find_similar_transactions(
    query_embedding vector(768),
    p_user_id VARCHAR(255),
    p_limit INTEGER DEFAULT 5,
    p_min_similarity FLOAT DEFAULT 0.7
)
RETURNS TABLE (
    transaction_id BIGINT,
    description TEXT,
    category VARCHAR(100),
    amount DECIMAL(15, 2),
    transaction_date DATE,
    similarity_score FLOAT
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        te.transaction_id,
        te.description,
        te.category,
        te.amount,
        te.transaction_date,
        cosine_similarity(te.embedding, query_embedding) AS similarity_score
    FROM transaction_embeddings te
    WHERE te.user_id = p_user_id
        AND cosine_similarity(te.embedding, query_embedding) >= p_min_similarity
    ORDER BY te.embedding <=> query_embedding
    LIMIT p_limit;
END;
$$ LANGUAGE plpgsql STABLE;

-- ============================================================
-- 7. Sample Financial Knowledge (Optional - Vietnamese)
-- ============================================================
-- Insert some initial financial advice for testing

INSERT INTO financial_knowledge (topic, category, content, embedding, language, tags, priority)
VALUES
    (
        'Tiết kiệm hàng tháng',
        'SAVING',
        'Quy tắc 50/30/20: Dành 50% thu nhập cho nhu cầu thiết yếu, 30% cho chi tiêu cá nhân, và 20% cho tiết kiệm. Điều này giúp bạn duy trì cân bằng tài chính và xây dựng quỹ khẩn cấp.',
        (SELECT ARRAY_FILL(0::float, ARRAY[768])::vector),  -- Placeholder - will be updated by embedding service
        'vi',
        ARRAY['tiết kiệm', 'ngân sách', 'quy tắc 50-30-20'],
        10
    ),
    (
        'Quỹ khẩn cấp',
        'SAVING',
        'Nên có quỹ khẩn cấp tương đương 3-6 tháng chi phí sinh hoạt. Đây là khoản tiền dự phòng cho những tình huống không lường trước như mất việc, ốm đau, hoặc chi phí bất ngờ.',
        (SELECT ARRAY_FILL(0::float, ARRAY[768])::vector),
        'vi',
        ARRAY['quỹ khẩn cấp', 'tiết kiệm', 'dự phòng'],
        9
    ),
    (
        'Theo dõi chi tiêu',
        'BUDGETING',
        'Ghi chép chi tiêu hàng ngày giúp bạn nhận biết thói quen tiêu dùng và tìm ra những khoản chi không cần thiết. Sử dụng app quản lý tài chính để theo dõi tự động và phân tích chi tiêu.',
        (SELECT ARRAY_FILL(0::float, ARRAY[768])::vector),
        'vi',
        ARRAY['theo dõi chi tiêu', 'ngân sách', 'quản lý tiền'],
        8
    ),
    (
        'Giảm chi tiêu không cần thiết',
        'SPENDING',
        'Phân biệt "muốn" và "cần": Trước khi mua hàng, hãy tự hỏi liệu đây có phải là nhu cầu thiết yếu. Áp dụng quy tắc 24 giờ: Chờ 24 giờ trước khi quyết định mua những món hàng không thiết yếu.',
        (SELECT ARRAY_FILL(0::float, ARRAY[768])::vector),
        'vi',
        ARRAY['chi tiêu thông minh', 'tiết kiệm', 'giảm chi phí'],
        7
    );

-- ============================================================
-- 8. Grant Permissions (Adjust for your setup)
-- ============================================================
-- If using role-based access control

-- GRANT SELECT, INSERT, UPDATE, DELETE ON transaction_embeddings TO aiservice_role;
-- GRANT SELECT, INSERT, UPDATE, DELETE ON financial_knowledge TO aiservice_role;
-- GRANT SELECT, INSERT, UPDATE, DELETE ON conversation_context TO aiservice_role;
-- GRANT SELECT, INSERT, UPDATE, DELETE ON rag_retrieval_metrics TO aiservice_role;
-- GRANT EXECUTE ON FUNCTION find_similar_transactions TO aiservice_role;

-- ============================================================
-- Migration Complete - RAG Infrastructure Ready
-- ============================================================
-- Next steps:
-- 1. Run embedding generation service to populate embeddings
-- 2. Update financial knowledge embeddings with actual vectors
-- 3. Start processing transactions to generate embeddings
-- ============================================================
