create table consent
(
    id serial
        constraint consent_pkey
            primary key,
    name varchar(255)
        constraint consent_name_key
            unique
);

create table consentaction
(
    id serial
        constraint consentaction_pkey
            primary key,
    actionoptions json,
    consentactiontype text not null,
    consent_id bigint
        constraint fk_consentaction_consent_id
            references consent
);

create table consentdetails
(
    id serial
        constraint consentdetails_pkey
            primary key,
    text text not null,
    language varchar(255) not null,
    hidden boolean not null,
    required boolean not null,
    consent_id bigint not null
        constraint fk_consentdetails_consent_id
            references consent
);
