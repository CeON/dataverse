ALTER TABLE authenticateduser
    add column if not exists notificationslanguage varchar(2);

UPDATE authenticateduser
    SET notificationslanguage = 'en'
    WHERE notificationslanguage IS NULL;