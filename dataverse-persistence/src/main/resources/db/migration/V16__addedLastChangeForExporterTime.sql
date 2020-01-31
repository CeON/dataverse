DO $$
BEGIN
  IF EXISTS(SELECT *
    FROM information_schema.columns
    WHERE table_name='dataset' and column_name='guestbookchangetime')
  AND NOT EXISTS(SELECT *
    FROM information_schema.columns
    WHERE table_name='dataset' and column_name='lastchangeforexportertime')
  THEN
      ALTER TABLE dataset RENAME COLUMN guestbookchangetime TO lastchangeforexportertime;
  ELSE
      ALTER TABLE dataset ADD COLUMN IF NOT EXISTS lastchangeforexportertime timestamp;
      ALTER TABLE dataset DROP COLUMN IF EXISTS guestbookchangetime;
  END IF;
END $$;

