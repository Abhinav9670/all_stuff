-- Migration script to add is_mps column to sales_shipment table
-- This script adds a boolean column 'is_mps' with default value FALSE

ALTER TABLE sales_shipment 
ADD COLUMN is_mps BOOLEAN DEFAULT FALSE NOT NULL;

-- Update existing records to ensure they have the default value
UPDATE sales_shipment 
SET is_mps = FALSE 
WHERE is_mps IS NULL;
