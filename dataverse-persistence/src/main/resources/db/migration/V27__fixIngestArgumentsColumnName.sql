ALTER TABLE ingestreport_reportarguments RENAME TO ingestreport_errorarguments;
ALTER TABLE ingestreport_errorarguments RENAME COLUMN reportarguments TO errorarguments;
ALTER TABLE ingestreport_errorarguments RENAME COLUMN reportarguments_order TO errorarguments_order;