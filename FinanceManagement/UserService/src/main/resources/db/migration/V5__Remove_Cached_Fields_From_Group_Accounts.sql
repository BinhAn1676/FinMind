-- Remove cached account data fields from group_accounts table
-- These fields are no longer needed as account data is now fetched real-time from FinanceService

ALTER TABLE group_accounts 
    DROP COLUMN label,
    DROP COLUMN bank_brand_name,
    DROP COLUMN account_number,
    DROP COLUMN currency,
    DROP COLUMN bank_code,
    DROP COLUMN accumulated;

