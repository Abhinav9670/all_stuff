-- Migration script to create sales_shipment_pack_details and sales_shipment_pack_details_item tables
-- These tables store pack box details and SKU quantity data for shipments

-- Create sales_shipment_pack_details table
CREATE TABLE sales_shipment_pack_details (
    entity_id INT AUTO_INCREMENT PRIMARY KEY,
    shipment_id INT NOT NULL,
    length DECIMAL(10,2) NULL,
    breadth DECIMAL(10,2) NULL,
    height DECIMAL(10,2) NULL,
    weight DECIMAL(10,2) NULL,
    box_id BIGINT NOT NULL,
    box_code VARCHAR(255) NOT NULL,
    vol_weight DECIMAL(10,2) NULL,
    box_sku_id BIGINT NULL,
    shipping_label TEXT NULL COMMENT 'URL or data for shipping label for this pack',
    way_bill VARCHAR(255) NULL COMMENT 'Waybill number for this specific pack',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_shipment_id (shipment_id),
    INDEX idx_box_id (box_id),
    INDEX idx_box_code (box_code),
    
    FOREIGN KEY (shipment_id) REFERENCES sales_shipment(entity_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create sales_shipment_pack_details_item table
CREATE TABLE sales_shipment_pack_details_item (
    entity_id INT AUTO_INCREMENT PRIMARY KEY,
    pack_details_id INT NOT NULL,
    global_sku_id BIGINT NOT NULL,
    client_sku_id VARCHAR(255) NOT NULL,
    count INT NOT NULL DEFAULT 1,
    
    INDEX idx_pack_details_id (pack_details_id),
    INDEX idx_global_sku_id (global_sku_id),
    INDEX idx_client_sku_id (client_sku_id),
    
    FOREIGN KEY (pack_details_id) REFERENCES sales_shipment_pack_details(entity_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
