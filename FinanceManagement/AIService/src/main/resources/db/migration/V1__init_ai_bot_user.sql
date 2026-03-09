-- ============================================================
-- AIService Database Initialization
-- ============================================================
-- Description: Creates the AI Bot system user and initial schema
-- for the AIService microservice.
--
-- Purpose:
-- 1. Insert AI Bot user into users table (appears in Frontend contact list)
-- 2. Create tables for storing AI-generated insights
-- 3. Create indexes for performance
--
-- Requirements:
-- - This migration assumes the 'users' table exists in UserService DB
-- - If using separate databases per service, adjust accordingly
-- ============================================================

-- ============================================================
-- 1. AI Bot System User
-- ============================================================
-- NOTE: The AI Bot user should be created in UserService database,
-- not here. AIService uses PostgreSQL, UserService uses MySQL.
-- They are separate microservices with separate databases.
--
-- To create the AI Bot user, run this in UserService (MySQL):
-- INSERT INTO users (id, username, email, full_name, avatar_url,
--                    user_type, is_active, is_system_user)
-- VALUES (999999, 'finbot', 'finbot@finance.system',
--         'FinBot - Trợ lý Tài chính AI', '/assets/avatars/finbot.png',
--         'SYSTEM', 1, 1);

-- ============================================================
-- 2. Create Insight Cards Table
-- ============================================================
-- Stores generated insight cards for caching and history

CREATE TABLE IF NOT EXISTS insight_cards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    card_type VARCHAR(20) NOT NULL CHECK (card_type IN ('WARNING', 'SAVING', 'INFO')),
    title VARCHAR(100) NOT NULL,
    amount BIGINT,
    message TEXT NOT NULL,
    action_text VARCHAR(50) NOT NULL,
    category VARCHAR(50),
    severity INTEGER CHECK (severity BETWEEN 1 AND 5),

    -- Metadata
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    is_dismissed BOOLEAN DEFAULT FALSE,
    dismissed_at TIMESTAMP,

    -- Audit
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for insight cards
CREATE INDEX idx_insight_cards_user_id ON insight_cards(user_id);
CREATE INDEX idx_insight_cards_generated_at ON insight_cards(generated_at DESC);
CREATE INDEX idx_insight_cards_active ON insight_cards(user_id, is_dismissed, expires_at)
    WHERE is_dismissed = FALSE;

-- ============================================================
-- 3. Create Anomaly Alerts Table
-- ============================================================
-- Stores detected anomalies from batch processing

CREATE TABLE IF NOT EXISTS anomaly_alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    sync_job_id VARCHAR(255),

    -- Anomaly details
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    description TEXT NOT NULL,
    affected_transaction_ids JSONB NOT NULL,

    -- AI metadata
    ai_confidence_score DECIMAL(3,2) CHECK (ai_confidence_score BETWEEN 0 AND 1),
    ai_reasoning TEXT,
    ai_model_version VARCHAR(50),

    -- Status tracking
    status VARCHAR(20) DEFAULT 'NEW' CHECK (status IN ('NEW', 'REVIEWED', 'FALSE_POSITIVE', 'CONFIRMED')),
    reviewed_at TIMESTAMP,
    reviewed_by VARCHAR(255),

    -- Audit
    detected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for anomaly alerts
CREATE INDEX idx_anomaly_alerts_user_id ON anomaly_alerts(user_id);
CREATE INDEX idx_anomaly_alerts_status ON anomaly_alerts(status);
CREATE INDEX idx_anomaly_alerts_severity ON anomaly_alerts(severity);
CREATE INDEX idx_anomaly_alerts_detected_at ON anomaly_alerts(detected_at DESC);

-- ============================================================
-- 4. Create Chat History Table
-- ============================================================
-- Stores AI chat conversations for context and history

CREATE TABLE IF NOT EXISTS ai_chat_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    conversation_id VARCHAR(255),

    -- Message details
    message_type VARCHAR(20) NOT NULL CHECK (message_type IN ('USER', 'BOT')),
    message_content TEXT NOT NULL,

    -- AI metadata (for bot messages)
    ai_model_version VARCHAR(50),
    ai_confidence_score DECIMAL(3,2),
    functions_called JSONB,  -- List of functions AI called to generate response

    -- Context
    language VARCHAR(10) DEFAULT 'vi',

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for chat history
CREATE INDEX idx_chat_history_user_id ON ai_chat_history(user_id, created_at DESC);
CREATE INDEX idx_chat_history_conversation_id ON ai_chat_history(conversation_id, created_at ASC);

-- ============================================================
-- 5. Create Forecasting Data Table
-- ============================================================
-- Stores spending forecasts and trend analysis

CREATE TABLE IF NOT EXISTS spending_forecasts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    category VARCHAR(50),

    -- Forecast details
    forecast_type VARCHAR(20) NOT NULL CHECK (forecast_type IN ('DAILY', 'WEEKLY', 'MONTHLY')),
    forecast_date DATE NOT NULL,
    predicted_amount DECIMAL(15,2) NOT NULL,
    confidence_interval_lower DECIMAL(15,2),
    confidence_interval_upper DECIMAL(15,2),

    -- Model details
    model_type VARCHAR(50) NOT NULL,  -- 'LINEAR_REGRESSION', 'ARIMA', etc.
    model_accuracy DECIMAL(5,4),

    -- AI explanation
    trend_explanation TEXT,
    ai_recommendation TEXT,

    -- Audit
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP
);

-- Indexes for forecasts
CREATE INDEX idx_forecasts_user_id ON spending_forecasts(user_id);
CREATE INDEX idx_forecasts_date ON spending_forecasts(forecast_date DESC);
CREATE INDEX idx_forecasts_active ON spending_forecasts(user_id, expires_at)
    WHERE expires_at IS NOT NULL;

-- ============================================================
-- 6. Create Processing Metrics Table
-- ============================================================
-- Tracks AI processing metrics for monitoring

CREATE TABLE IF NOT EXISTS ai_processing_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Processing details
    operation_type VARCHAR(50) NOT NULL,  -- 'INSIGHT_GENERATION', 'ANOMALY_DETECTION', 'CHAT', etc.
    user_id VARCHAR(255),

    -- Performance metrics
    processing_time_ms INTEGER NOT NULL,
    ai_inference_time_ms INTEGER,
    data_fetch_time_ms INTEGER,

    -- Status
    status VARCHAR(20) NOT NULL CHECK (status IN ('SUCCESS', 'FAILED', 'PARTIAL')),
    error_message TEXT,

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for metrics
CREATE INDEX idx_metrics_operation_type ON ai_processing_metrics(operation_type, created_at DESC);
CREATE INDEX idx_metrics_status ON ai_processing_metrics(status);

-- ============================================================
-- 7. Initial Data - Sample Insight Cards (Optional)
-- ============================================================
-- Uncomment to insert sample cards for testing

-- INSERT INTO insight_cards (user_id, card_type, title, amount, message, action_text, severity)
-- VALUES
--     ('user-sample', 'INFO', 'Chào mừng đến AIService', 0, 'Hệ thống AI đã sẵn sàng phân tích tài chính của bạn!', 'Bắt đầu', NULL);

-- ============================================================
-- 8. Grant Permissions (Adjust for your setup)
-- ============================================================
-- If using role-based access control, grant necessary permissions

-- GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO aiservice_role;
-- GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO aiservice_role;

-- ============================================================
-- Migration Complete
-- ============================================================
