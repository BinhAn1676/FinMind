-- Migration script to add new profile fields to users table
-- Run this script on your database

ALTER TABLE users 
ADD COLUMN first_name VARCHAR(255),
ADD COLUMN last_name VARCHAR(255),
ADD COLUMN avatar VARCHAR(500),
ADD COLUMN bio TEXT,
ADD COLUMN date_of_birth VARCHAR(50);

-- Add indexes for better performance
CREATE INDEX idx_users_first_name ON users(first_name);
CREATE INDEX idx_users_last_name ON users(last_name);
CREATE INDEX idx_users_avatar ON users(avatar);

-- Update existing records to populate first_name and last_name from full_name
-- This is optional and depends on your data structure
UPDATE users 
SET first_name = SUBSTRING_INDEX(full_name, ' ', 1),
    last_name = SUBSTRING_INDEX(full_name, ' ', -1)
WHERE full_name IS NOT NULL AND full_name != '';

-- Add comments for documentation
ALTER TABLE users 
MODIFY COLUMN first_name VARCHAR(255) COMMENT 'User first name',
MODIFY COLUMN last_name VARCHAR(255) COMMENT 'User last name',
MODIFY COLUMN avatar VARCHAR(500) COMMENT 'File key for user avatar',
MODIFY COLUMN bio TEXT COMMENT 'User biography/description',
MODIFY COLUMN date_of_birth VARCHAR(50) COMMENT 'User date of birth in string format';


