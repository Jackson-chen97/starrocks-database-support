CREATE DATABASE IF NOT EXISTS dws;
CREATE SCHEMA IF NOT EXISTS ods;

ALTER DATABASE dws SET DATA QUOTA 1024G;
ALTER SCHEMA ods SET DATA QUOTA 512G;

CREATE INDEX idx_order_id ON dws.sample_orders (order_id) USING BITMAP;
CREATE BITMAP INDEX idx_store_id ON dws.sample_orders (store_id);

DROP INDEX idx_order_id ON dws.sample_orders;
DROP DATABASE IF EXISTS old_dws;
DROP SCHEMA IF EXISTS old_ods;
