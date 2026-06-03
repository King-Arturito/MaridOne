ALTER TABLE employee
    ADD COLUMN designation VARCHAR(100);

UPDATE employee
SET designation = position
WHERE designation IS NULL;
