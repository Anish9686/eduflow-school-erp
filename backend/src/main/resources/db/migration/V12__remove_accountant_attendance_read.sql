-- Revoke ATTENDANCE:READ from ACCOUNTANT role
DELETE rp FROM role_permissions rp
JOIN roles r ON r.id = rp.role_id
JOIN permissions p ON p.id = rp.permission_id
WHERE r.code = 'ACCOUNTANT' AND p.code = 'ATTENDANCE:READ';
