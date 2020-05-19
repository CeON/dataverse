CREATE TABLE ingestreport_reportarguments
(
    ingestreport_id bigint
        constraint fk_ingestreport_reportarguments_ingestreport_id
            references ingestreport,
    reportarguments varchar,
    reportarguments_order integer
);

ALTER TABLE ingestreport ADD COLUMN argumentsbundlekey varchar;
