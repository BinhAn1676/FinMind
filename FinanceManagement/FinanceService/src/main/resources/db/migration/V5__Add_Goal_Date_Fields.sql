-- Add goal date fields to categories table
ALTER TABLE categories 
ADD COLUMN goal_target_date DATE,
ADD COLUMN monthly_goal_day INTEGER;

-- Add constraints for monthly_goal_day (1-31)
ALTER TABLE categories 
ADD CONSTRAINT chk_monthly_goal_day 
CHECK (monthly_goal_day IS NULL OR (monthly_goal_day >= 1 AND monthly_goal_day <= 31));

-- Add comment for clarity
COMMENT ON COLUMN categories.goal_target_date IS 'Target date for long-term goals';
COMMENT ON COLUMN categories.monthly_goal_day IS 'Day of month for monthly goals (1-31)';
