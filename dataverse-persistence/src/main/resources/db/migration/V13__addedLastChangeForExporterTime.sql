DO $$
BEGIN
  IF EXISTS(SELECT *
    FROM information_schema.columns
    WHERE table_name='dataset' and column_name='guestbookchangetime')
  THEN
      ALTER TABLE dataset RENAME COLUMN guestbookchangetime TO lastchangeforexportertime;
  ELSE
      ALTER TABLE dataset ADD COLUMN IF NOT EXISTS lastchangeforexportertime timestamp;
  END IF;
END $$;