INSERT INTO permissions (code, description, module) VALUES
    ('INVENTORY:READ', 'Read inventory items and stock levels', 'INVENTORY'),
    ('INVENTORY:MANAGE', 'Manage inventory items and stock movements', 'INVENTORY'),
    ('LIBRARY:READ', 'Read library catalog and issues', 'LIBRARY'),
    ('LIBRARY:MANAGE', 'Manage library catalog items', 'LIBRARY'),
    ('LIBRARY:ISSUE', 'Issue library items', 'LIBRARY'),
    ('LIBRARY:RETURN', 'Return library items', 'LIBRARY'),
    ('HOSTEL:READ', 'Read hostel rooms and allocations', 'HOSTEL'),
    ('HOSTEL:MANAGE', 'Manage hostel rooms and allocations', 'HOSTEL'),
    ('SUPPORT:READ', 'Read support tickets', 'SUPPORT'),
    ('SUPPORT:MANAGE', 'Manage support tickets', 'SUPPORT')
ON DUPLICATE KEY UPDATE description = VALUES(description), module = VALUES(module);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'INVENTORY:READ','INVENTORY:MANAGE',
    'LIBRARY:READ','LIBRARY:MANAGE','LIBRARY:ISSUE','LIBRARY:RETURN',
    'HOSTEL:READ','HOSTEL:MANAGE',
    'SUPPORT:READ','SUPPORT:MANAGE'
)
WHERE r.code = 'SUPER_ADMIN'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );
