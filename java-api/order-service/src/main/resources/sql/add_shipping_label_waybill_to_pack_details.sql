-- Add shipping_label and way_bill columns to sales_shipment_pack_details table
-- Migration script for adding new fields to track shipping labels and waybills per pack

ALTER TABLE sales_shipment_pack_details 
ADD COLUMN shipping_label TEXT COMMENT 'URL or data for shipping label for this pack',
ADD COLUMN way_bill VARCHAR(255) COMMENT 'Waybill number for this specific pack';

-- Add indexes for better query performance
CREATE INDEX idx_sales_shipment_pack_details_way_bill ON sales_shipment_pack_details(way_bill);
CREATE INDEX idx_sales_shipment_pack_details_shipment_id_way_bill ON sales_shipment_pack_details(shipment_id, way_bill);
