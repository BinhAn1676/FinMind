-- Add is_default field to wallets table
ALTER TABLE wallets ADD COLUMN is_default BOOLEAN DEFAULT FALSE NOT NULL;

-- Set the first wallet for each user as default (if no default exists)
UPDATE wallets w1 
SET is_default = TRUE 
WHERE w1.id IN (
    SELECT MIN(w2.id) 
    FROM wallets w2 
    WHERE w2.user_id = w1.user_id 
    AND NOT EXISTS (
        SELECT 1 FROM wallets w3 
        WHERE w3.user_id = w2.user_id 
        AND w3.is_default = TRUE
    )
);
