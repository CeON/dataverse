create table othertermsofuse
(
    id       serial       not null
        constraint othertermsofuse_pkey
            primary key,
    active   boolean,
    name     varchar(255) not null
        constraint othertermsofuse_name_key
            unique,
    position bigint       not null
);

INSERT INTO othertermsofuse(id, active, name, "position")
VALUES (1, false, 'All rights reserved', 1);
INSERT INTO othertermsofuse(id, active, name, "position")
VALUES (2, false, 'Restricted access', 2);