ALTER TABLE public.filemetadata ADD displayorder integer DEFAULT 0 NULL;

DO $$
DECLARE
  rec      RECORD;
  rec2     RECORD;
  iterator integer :=0;
BEGIN
  FOR rec IN SELECT DISTINCT datasetversion.id
             FROM datasetversion
                    JOIN filemetadata ON datasetversion.id = filemetadata.datasetversion_id
  LOOP
    FOR rec2 IN SELECT datasetversion.id, filemetadata.displayorder, filemetadata.id AS fileId
                FROM datasetversion
                       JOIN filemetadata ON datasetversion.id = filemetadata.datasetversion_id
                WHERE datasetversion.id = rec.id
                ORDER BY label

    LOOP
      UPDATE filemetadata SET displayorder = iterator WHERE filemetadata.id = rec2.fileId;
      iterator := iterator + 1;
    END LOOP;
    iterator := 0;
  END LOOP;
END; $$