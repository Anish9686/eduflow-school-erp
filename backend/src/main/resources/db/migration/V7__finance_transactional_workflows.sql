ALTER TABLE fee_demands
    ADD COLUMN billing_period DATE NULL;

ALTER TABLE fee_demands
    ADD CONSTRAINT uq_fee_demand_period UNIQUE (student_id, fee_structure_id, session_id, billing_period);

ALTER TABLE fee_structures
    ADD COLUMN effective_from DATE NOT NULL DEFAULT '1970-01-01';

ALTER TABLE fee_structures
    ADD COLUMN effective_to DATE NULL;

ALTER TABLE fee_structures
    ADD CONSTRAINT chk_fee_structure_effective_dates CHECK (effective_to IS NULL OR effective_to >= effective_from);

CREATE TABLE finance_adjustments (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    payment_id      BIGINT          NOT NULL,
    adjustment_type ENUM('REFUND','REVERSAL') NOT NULL,
    amount          DECIMAL(12,2)   NOT NULL,
    reason          VARCHAR(500)    NOT NULL,
    requested_by    BIGINT          NOT NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_finance_adjustments PRIMARY KEY (id),
    CONSTRAINT uq_finance_adjustment_payment UNIQUE (payment_id),
    CONSTRAINT chk_finance_adjustment_amount CHECK (amount > 0),
    CONSTRAINT fk_finance_adjustment_payment FOREIGN KEY (payment_id) REFERENCES payments(id),
    CONSTRAINT fk_finance_adjustment_actor FOREIGN KEY (requested_by) REFERENCES users(id),
    KEY idx_finance_adjustments_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO permissions (code, description, module) VALUES
    ('FEE:READ', 'Read fee structures and demands', 'FEE'),
    ('FEE:MANAGE', 'Create fee structures and demands', 'FEE'),
    ('FEE:REFUND', 'Refund or reverse posted payments', 'FEE'),
    ('FEE_DISCOUNT:REQUEST', 'Request fee discounts', 'FEE_DISCOUNT'),
    ('EXPENSE:MANAGE', 'Create and submit expenses', 'EXPENSE'),
    ('EXPENSE:APPROVE', 'Approve or reject expenses', 'EXPENSE'),
    ('FINANCE_REPORT:READ', 'Read finance reports and reconciliation', 'FINANCE_REPORT'),
    ('FINANCE_REPORT:EXPORT', 'Export finance transaction reports', 'FINANCE_REPORT');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'SUPER_ADMIN'
  AND p.code IN ('FEE:READ', 'FEE:MANAGE', 'FEE:REFUND', 'FEE_DISCOUNT:REQUEST',
                 'EXPENSE:MANAGE', 'EXPENSE:APPROVE', 'FINANCE_REPORT:READ', 'FINANCE_REPORT:EXPORT');