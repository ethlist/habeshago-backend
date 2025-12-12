-- Add sender_contact_method column to item_requests table
ALTER TABLE item_requests ADD COLUMN sender_contact_method VARCHAR(20);
