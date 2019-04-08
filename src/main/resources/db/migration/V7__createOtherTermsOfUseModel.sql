create table othertermsofuse
(
    id       serial       not null
        constraint othertermsofuse_pkey
            primary key,
    active   boolean,
    name     varchar(255) not null
        constraint othertermsofuse_name_key
            unique
);

INSERT INTO othertermsofuse(id, active, name)
VALUES (1, false, 'All rights reserved');
INSERT INTO othertermsofuse(id, active, name)
VALUES (2, false, 'Restricted access');