-- Read-only checks for the reported accounts. Do not select password_hash,
-- session tokens, or authentication secrets.

-- 1) Account state and assigned role codes.
SELECT u.id, u.username, u.full_name, u.status, r.code AS role_code
FROM users u
LEFT JOIN user_roles ur ON ur.user_id = u.id
LEFT JOIN roles r ON r.id = ur.role_id
WHERE LOWER(u.username) = LOWER('Ankit9661')
   OR LOWER(u.full_name) = LOWER('Satyam Kumar Singh')
ORDER BY u.id, r.code;

-- 2) Effective grants actually resolved from those role assignments.
SELECT DISTINCT u.username, r.code AS role_code, p.code AS permission_code
FROM users u
JOIN user_roles ur ON ur.user_id = u.id
JOIN roles r ON r.id = ur.role_id
JOIN role_permissions rp ON rp.role_id = r.id
JOIN permissions p ON p.id = rp.permission_id
WHERE LOWER(u.username) = LOWER('Ankit9661')
   OR LOWER(u.full_name) = LOWER('Satyam Kumar Singh')
ORDER BY u.username, r.code, p.code;

-- 3) Confirm the additive baseline migration has run.
SELECT version, description, success, installed_on
FROM flyway_schema_history
WHERE version = '10';

-- 4) Check for duplicate standard role codes (the unique constraint should
-- prevent duplicates, but this is useful when auditing a pre-existing DB).
SELECT UPPER(code) AS normalized_code, COUNT(*) AS matching_roles
FROM roles
GROUP BY UPPER(code)
HAVING COUNT(*) > 1;
