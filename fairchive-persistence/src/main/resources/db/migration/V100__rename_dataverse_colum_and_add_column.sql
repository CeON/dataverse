ALTER TABLE dataverse RENAME COLUMN defaultcontributorrole_id TO defaultdatasetcontributorrole_id;
ALTER TABLE dataverse ADD defaultdataversecontributorrole_id int8 NULL;
