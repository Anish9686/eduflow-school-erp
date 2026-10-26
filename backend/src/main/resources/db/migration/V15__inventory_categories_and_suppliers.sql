-- V15: Inventory Categories and Suppliers for Operations Module
CREATE TABLE IF NOT EXISTS inventory_categories (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    name            VARCHAR(100)    NOT NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_inventory_categories PRIMARY KEY (id),
    CONSTRAINT uq_inv_cat_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS inventory_suppliers (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    name            VARCHAR(200)    NOT NULL,
    contact         VARCHAR(100),
    code            VARCHAR(50),
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_inventory_suppliers PRIMARY KEY (id),
    CONSTRAINT uq_inv_sup_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO inventory_categories (name) VALUES ('Stationery'), ('Electronics'), ('Laboratory'), ('Sports')
ON DUPLICATE KEY UPDATE name=VALUES(name);

INSERT INTO inventory_suppliers (name, contact, code) VALUES ('Apex Supplies', '+91 98765 43210', 'SUP-APEX')
ON DUPLICATE KEY UPDATE name=VALUES(name);
