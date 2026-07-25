ALTER TABLE profiles
    ADD COLUMN faculty varchar(120),
    ADD COLUMN study_program varchar(160),
    ADD COLUMN course integer;

ALTER TABLE profiles
    ADD CONSTRAINT profiles_course_check CHECK (course IS NULL OR course BETWEEN 1 AND 6);
