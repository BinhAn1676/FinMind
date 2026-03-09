-- Remove icon columns from categories and category_groups tables
ALTER TABLE categories DROP COLUMN icon;
ALTER TABLE category_groups DROP COLUMN icon;

