package edu.harvard.iq.dataverse.persistence.datafile.ingest;

import edu.harvard.iq.dataverse.persistence.JpaRepository;

import javax.ejb.Stateless;

@Stateless
public class IngestReportRepository extends JpaRepository<Long, IngestReport> {

    // -------------------- CONSTRUCTORS --------------------

    public IngestReportRepository() {
        super(IngestReport.class);
    }

    // -------------------- LOGIC --------------------

    public void deleteForDataFileId(Long dataFileId) {
        em.createQuery("DELETE FROM IngestReport r WHERE r.dataFile.id = :fileid")
                .setParameter("fileid", dataFileId)
                .executeUpdate();
    }
}