package edu.harvard.iq.dataverse.dataset;

import edu.harvard.iq.dataverse.persistence.dataset.DownloadDatasetLog;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

@Stateless
public class DownloadDatasetLogService {

    @PersistenceContext(unitName = "VDCNet-ejbPU")
    private EntityManager em;

    // -------------------- LOGIC --------------------

    public int fetchDownloadCountForDataset(Long datasetId) {
        DownloadDatasetLog log = em.find(DownloadDatasetLog.class, datasetId);
        return log != null ? log.getCount() : 0;
    }

    public void incrementDownloadCountForDataset(Long datasetId) {
        DownloadDatasetLog log = em.find(DownloadDatasetLog.class, datasetId);
        if (log == null) {
            DownloadDatasetLog datasetLog = new DownloadDatasetLog();
            datasetLog.setDatasetId(datasetId);
            datasetLog.setCount(1);
            em.persist(datasetLog);
        } else {
            log.setCount(log.getCount() + 1);
        }
    }
}
