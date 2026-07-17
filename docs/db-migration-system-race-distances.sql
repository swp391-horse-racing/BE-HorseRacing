ALTER TABLE system_settings
    ADD COLUMN race_distances_meters_json LONGTEXT NULL;

UPDATE system_settings
SET race_distances_meters_json = '[1000,1200,1500]'
WHERE race_distances_meters_json IS NULL
   OR race_distances_meters_json = '';

ALTER TABLE system_settings
    MODIFY COLUMN race_distances_meters_json LONGTEXT NOT NULL;
