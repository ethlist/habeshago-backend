-- Add sender_contact_value column to item_requests table
ALTER TABLE item_requests ADD COLUMN IF NOT EXISTS sender_contact_value VARCHAR(50);
