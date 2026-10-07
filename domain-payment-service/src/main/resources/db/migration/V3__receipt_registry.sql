-- One provider payment can settle only one merchant order, even when several partial receipts are combined.
CREATE TABLE provider_receipts(receipt_id VARCHAR(200) PRIMARY KEY,order_id UUID NOT NULL REFERENCES payment_orders(id));
ALTER TABLE payment_orders ALTER COLUMN receipt_id TYPE TEXT;
