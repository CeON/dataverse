package edu.harvard.iq.dataverse.dataset.deaccession;

import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion.VersionState;
import edu.harvard.iq.dataverse.search.IndexServiceBean;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

@Stateless
public class DatasetDeaccessionBean {

    @PersistenceContext
    EntityManager em;

    IndexServiceBean indexService;

    // -------------------- CONSTRUCTORS --------------------

    @Deprecated
    public DatasetDeaccessionBean() { }

    @Inject
    public DatasetDeaccessionBean(IndexServiceBean indexService) {
        this.indexService = indexService;
    }

    // -------------------- LOGIC --------------------

    public DatasetVersion deaccessDatasetVersion(Dataset containingDataset, DatasetVersion deaccessionVersion,
                                                 String deaccessionReason, String deaccessionForwardURLFor)  {
        if (containingDataset == null || !containingDataset.equals(deaccessionVersion.getDataset())) {
            throw new RuntimeException("Provided dataset is not equal to the dataset of version being deaccessed.");
        }

        deaccessionVersion.setVersionNote(deaccessionReason);
        deaccessionVersion.setArchiveNote(deaccessionForwardURLFor);
        deaccessionVersion.setVersionState(VersionState.DEACCESSIONED);
        DatasetVersion merged = em.merge(deaccessionVersion);

        Dataset dataset = merged.getDataset();
        indexService.indexDataset(dataset, true);
        em.merge(dataset);
        return merged;
    }
}
