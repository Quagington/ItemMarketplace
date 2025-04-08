CREATE TABLE IF NOT EXISTS marketplace_listings (
  listing_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  seller_uuid CHAR(36) NOT NULL,
  item_data BLOB NOT NULL, -- Serialized item stack
  price DECIMAL(20,2) NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  create_date_utc TIMESTAMP NOT NULL DEFAULT UTC_TIMESTAMP,
  last_update_date_utc TIMESTAMP NOT NULL DEFAULT UTC_TIMESTAMP,
  expiry_date_utc TIMESTAMP NULL,
  INDEX idx_seller (seller_uuid),
  INDEX idx_active_listings (is_active, expiry_date_utc)
);

CREATE TABLE IF NOT EXISTS marketplace_transactions (
  transaction_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  listing_id BIGINT NOT NULL,
  seller_uuid CHAR(36) NOT NULL,
  buyer_uuid CHAR(36) NOT NULL,
  item_data BLOB NOT NULL, -- Snapshot of item at time of purchase
  price DECIMAL(20,2) NOT NULL,
  transaction_date_utc TIMESTAMP NOT NULL DEFAULT UTC_TIMESTAMP,
  FOREIGN KEY (listing_id) REFERENCES marketplace_listings(listing_id),
  INDEX idx_seller_transactions (seller_uuid),
  INDEX idx_buyer_transactions (buyer_uuid)
);