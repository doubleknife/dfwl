-- Existing V1 databases only. Flyway executes and registers this migration once.
-- Do not execute this file manually. New database.sql snapshots already register baseline 2.
-- Duplicate-column errors are intentional failures for incorrectly registered databases.
ALTER TABLE sys_user ADD COLUMN name VARCHAR(128) NULL;
UPDATE sys_user u SET name = u.phone
WHERE NOT EXISTS (SELECT 1 FROM driver d WHERE d.user_id = u.id OR (d.user_id IS NULL AND d.phone = u.phone))
  AND EXISTS (SELECT 1 FROM sys_role r WHERE r.id = u.role_id AND r.role_code <> 'DRIVER');
