-- Chronic condition ICD-10 families (used by the Primary Care Wellness high-priority check).
INSERT INTO dbo.condition_group (icd_prefix, condition_name, is_chronic) VALUES
    ('E10',   'Type 1 Diabetes',                 TRUE),
    ('E11',   'Type 2 Diabetes',                 TRUE),
    ('I10',   'Essential Hypertension',          TRUE),
    ('E78',   'Hyperlipidemia',                  TRUE),
    ('J45',   'Asthma',                          TRUE),
    ('N18',   'Chronic Kidney Disease',          TRUE),
    ('I25',   'Chronic Ischemic Heart Disease',  TRUE),
    ('E03',   'Hypothyroidism',                  TRUE),
    ('G47.3', 'Sleep Apnea',                     TRUE),
    ('M81',   'Osteoporosis',                    TRUE);
