-- H2: preserve the calendar date and discard the old deadline time.
-- Stop the application and back up the database before applying to an existing database.
ALTER TABLE announcements ALTER COLUMN deadline DATE;
