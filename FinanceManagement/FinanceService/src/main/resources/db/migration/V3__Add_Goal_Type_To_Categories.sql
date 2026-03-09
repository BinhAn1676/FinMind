-- Add goal_type column to categories table
ALTER TABLE categories ADD COLUMN goal_type VARCHAR(20) NOT NULL DEFAULT 'MONTHLY';

-- Add check constraint for goal_type values
ALTER TABLE categories ADD CONSTRAINT chk_goal_type CHECK (goal_type IN ('MONTHLY', 'LONG_TERM'));

