ALTER TABLE usernotification
    ADD COLUMN IF NOT EXISTS returntoauthorreason varchar(255);
COMMIT;