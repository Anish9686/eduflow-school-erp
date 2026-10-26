-- ============================================================
-- V13: TRANSPORT MANAGEMENT MODULE
-- Bus, Route, Route Stops, Student Assignments, History & RBAC
-- ============================================================

-- 1. Transport Routes
CREATE TABLE transport_routes (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    route_name      VARCHAR(150)    NOT NULL,
    route_code      VARCHAR(50)     NOT NULL,
    description     TEXT,
    status          ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    institution_id  VARCHAR(50)     NOT NULL DEFAULT 'MAIN',
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by      BIGINT,
    updated_by      BIGINT,
    CONSTRAINT pk_transport_routes PRIMARY KEY (id),
    CONSTRAINT uq_transport_routes_code UNIQUE (route_code),
    CONSTRAINT fk_tr_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_tr_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Transport Route Stops
CREATE TABLE transport_route_stops (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    route_id        BIGINT          NOT NULL,
    stop_name       VARCHAR(150)    NOT NULL,
    stop_order      INT             NOT NULL,
    pickup_time     TIME,
    drop_time       TIME,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_transport_route_stops PRIMARY KEY (id),
    CONSTRAINT uq_route_stop_order UNIQUE (route_id, stop_order),
    CONSTRAINT fk_trs_route FOREIGN KEY (route_id) REFERENCES transport_routes(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Transport Buses
CREATE TABLE transport_buses (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    bus_name            VARCHAR(100)    NOT NULL,
    registration_no     VARCHAR(50)     NOT NULL,
    fleet_no            VARCHAR(50),
    bus_type            VARCHAR(50)     NOT NULL DEFAULT 'STANDARD',
    capacity            INT             NOT NULL,
    current_route_id    BIGINT,
    driver_staff_id     BIGINT,
    conductor_staff_id  BIGINT,
    status              ENUM('ACTIVE','INACTIVE','MAINTENANCE') NOT NULL DEFAULT 'ACTIVE',
    institution_id      VARCHAR(50)     NOT NULL DEFAULT 'MAIN',
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by          BIGINT,
    updated_by          BIGINT,
    CONSTRAINT pk_transport_buses PRIMARY KEY (id),
    CONSTRAINT uq_transport_buses_reg UNIQUE (registration_no),
    CONSTRAINT fk_tb_route FOREIGN KEY (current_route_id) REFERENCES transport_routes(id) ON DELETE SET NULL,
    CONSTRAINT fk_tb_driver FOREIGN KEY (driver_staff_id) REFERENCES staff(id) ON DELETE SET NULL,
    CONSTRAINT fk_tb_conductor FOREIGN KEY (conductor_staff_id) REFERENCES staff(id) ON DELETE SET NULL,
    CONSTRAINT fk_tb_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_tb_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Student Transport Assignments
CREATE TABLE student_transport_assignments (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    student_id          BIGINT          NOT NULL,
    bus_id              BIGINT          NOT NULL,
    route_id            BIGINT          NOT NULL,
    pickup_stop_id      BIGINT,
    drop_stop_id        BIGINT,
    start_date          DATE            NOT NULL,
    end_date            DATE,
    status              ENUM('ACTIVE','ENDED','TRANSFERRED','CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    institution_id      VARCHAR(50)     NOT NULL DEFAULT 'MAIN',
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by          BIGINT,
    updated_by          BIGINT,
    CONSTRAINT pk_student_transport_assignments PRIMARY KEY (id),
    CONSTRAINT fk_sta_student FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT fk_sta_bus FOREIGN KEY (bus_id) REFERENCES transport_buses(id),
    CONSTRAINT fk_sta_route FOREIGN KEY (route_id) REFERENCES transport_routes(id),
    CONSTRAINT fk_sta_pickup_stop FOREIGN KEY (pickup_stop_id) REFERENCES transport_route_stops(id) ON DELETE SET NULL,
    CONSTRAINT fk_sta_drop_stop FOREIGN KEY (drop_stop_id) REFERENCES transport_route_stops(id) ON DELETE SET NULL,
    CONSTRAINT fk_sta_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_sta_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Transport Assignment History
CREATE TABLE transport_assignment_history (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    assignment_id       BIGINT          NOT NULL,
    student_id          BIGINT          NOT NULL,
    bus_id              BIGINT          NOT NULL,
    route_id            BIGINT          NOT NULL,
    pickup_stop_id      BIGINT,
    drop_stop_id        BIGINT,
    action              VARCHAR(50)     NOT NULL,
    reason              VARCHAR(500),
    changed_by          BIGINT,
    changed_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_transport_assignment_history PRIMARY KEY (id),
    CONSTRAINT fk_tah_assignment FOREIGN KEY (assignment_id) REFERENCES student_transport_assignments(id) ON DELETE CASCADE,
    CONSTRAINT fk_tah_student FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT fk_tah_bus FOREIGN KEY (bus_id) REFERENCES transport_buses(id),
    CONSTRAINT fk_tah_route FOREIGN KEY (route_id) REFERENCES transport_routes(id),
    CONSTRAINT fk_tah_changer FOREIGN KEY (changed_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Indexes for Performance
CREATE INDEX idx_tb_status ON transport_buses(status);
CREATE INDEX idx_tb_route ON transport_buses(current_route_id);
CREATE INDEX idx_tb_driver ON transport_buses(driver_staff_id);
CREATE INDEX idx_tb_conductor ON transport_buses(conductor_staff_id);

CREATE INDEX idx_tr_status ON transport_routes(status);
CREATE INDEX idx_trs_route_order ON transport_route_stops(route_id, stop_order);

CREATE INDEX idx_sta_student_status ON student_transport_assignments(student_id, status);
CREATE INDEX idx_sta_bus_status ON student_transport_assignments(bus_id, status);
CREATE INDEX idx_sta_route_status ON student_transport_assignments(route_id, status);

CREATE INDEX idx_tah_assignment ON transport_assignment_history(assignment_id);
CREATE INDEX idx_tah_student ON transport_assignment_history(student_id);

-- 7. Register Transport Permissions
INSERT INTO permissions (code, description, module) VALUES
    ('TRANSPORT:READ', 'Read transport records, buses, routes, and assignments', 'TRANSPORT'),
    ('TRANSPORT:CREATE', 'Create buses and transport records', 'TRANSPORT'),
    ('TRANSPORT:UPDATE', 'Update bus, route, and transport details', 'TRANSPORT'),
    ('TRANSPORT:DELETE', 'Deactivate or delete transport records', 'TRANSPORT'),
    ('TRANSPORT:ASSIGN', 'Assign students to buses and routes', 'TRANSPORT'),
    ('TRANSPORT:MANAGE_ROUTES', 'Manage routes and route stops', 'TRANSPORT'),
    ('TRANSPORT:MANAGE_STAFF', 'Manage driver and conductor assignments', 'TRANSPORT'),
    ('TRANSPORT:VIEW_REPORTS', 'View transport capacity and operational reports', 'TRANSPORT'),
    ('TRANSPORT:VIEW_HISTORY', 'View transport assignment history and audit logs', 'TRANSPORT')
ON DUPLICATE KEY UPDATE description = VALUES(description), module = VALUES(module);

-- 8. Map Permissions to Roles
-- SUPER_ADMIN: Full access
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'TRANSPORT:READ', 'TRANSPORT:CREATE', 'TRANSPORT:UPDATE', 'TRANSPORT:DELETE',
    'TRANSPORT:ASSIGN', 'TRANSPORT:MANAGE_ROUTES', 'TRANSPORT:MANAGE_STAFF',
    'TRANSPORT:VIEW_REPORTS', 'TRANSPORT:VIEW_HISTORY'
)
WHERE r.code = 'SUPER_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- ADMIN: Full operational transport access
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'TRANSPORT:READ', 'TRANSPORT:CREATE', 'TRANSPORT:UPDATE', 'TRANSPORT:DELETE',
    'TRANSPORT:ASSIGN', 'TRANSPORT:MANAGE_ROUTES', 'TRANSPORT:MANAGE_STAFF',
    'TRANSPORT:VIEW_REPORTS', 'TRANSPORT:VIEW_HISTORY'
)
WHERE r.code = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- MANAGEMENT: Full operational transport access
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'TRANSPORT:READ', 'TRANSPORT:CREATE', 'TRANSPORT:UPDATE',
    'TRANSPORT:ASSIGN', 'TRANSPORT:MANAGE_ROUTES',
    'TRANSPORT:VIEW_REPORTS', 'TRANSPORT:VIEW_HISTORY'
)
WHERE r.code = 'MANAGEMENT'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- STAFF_ADMIN (HR / Staff Admin): Full operational transport access
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'TRANSPORT:READ', 'TRANSPORT:CREATE', 'TRANSPORT:UPDATE',
    'TRANSPORT:ASSIGN', 'TRANSPORT:MANAGE_ROUTES', 'TRANSPORT:MANAGE_STAFF',
    'TRANSPORT:VIEW_REPORTS', 'TRANSPORT:VIEW_HISTORY'
)
WHERE r.code = 'STAFF_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- SUPPORT_AGENT: Full operational transport access
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'TRANSPORT:READ', 'TRANSPORT:CREATE', 'TRANSPORT:UPDATE',
    'TRANSPORT:ASSIGN', 'TRANSPORT:MANAGE_ROUTES',
    'TRANSPORT:VIEW_REPORTS', 'TRANSPORT:VIEW_HISTORY'
)
WHERE r.code = 'SUPPORT_AGENT'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- STUDENT: Read-only access to own transport info
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'TRANSPORT:READ'
WHERE r.code = 'STUDENT'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- PARENT_GUARDIAN: Read-only access to linked children transport info
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'TRANSPORT:READ'
WHERE r.code = 'PARENT_GUARDIAN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- 9. Seed transport staff members if not present (Driver and Conductor)
INSERT IGNORE INTO staff (employee_no, first_name, last_name, email, phone, designation, department, join_date, status)
VALUES
    ('DRV001', 'Rajesh', 'Kumar', 'rajesh.driver@eduflow.internal', '+91 98765 43210', 'Senior Bus Driver', 'Transport', '2023-01-15', 'ACTIVE'),
    ('CND001', 'Sunita', 'Devi', 'sunita.conductor@eduflow.internal', '+91 98765 43211', 'Bus Conductor', 'Transport', '2023-02-01', 'ACTIVE'),
    ('DRV002', 'Vikram', 'Singh', 'vikram.driver@eduflow.internal', '+91 98765 43212', 'Bus Driver', 'Transport', '2023-05-10', 'ACTIVE');
