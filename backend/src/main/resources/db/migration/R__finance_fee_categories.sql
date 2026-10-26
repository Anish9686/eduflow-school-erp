INSERT INTO fee_categories (name, description, is_active)
VALUES
    ('Tuition', 'Tuition and instructional fees', 1),
    ('Transport', 'Student transportation fees', 1),
    ('Hostel', 'Residential and hostel fees', 1),
    ('Examination', 'Examination and assessment fees', 1),
    ('Other', 'Other school fees', 1)
ON DUPLICATE KEY UPDATE is_active = 1;
