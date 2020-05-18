create table ingestreport_reportarguments
(
    ingestreport_id bigint
        constraint fk_ingestreport_reportarguments_ingestreport_id
            references ingestreport,
    reportarguments varchar,
    argumentsbundlekey varchar,
    reportarguments_order integer
);
