package edu.harvard.iq.dataverse.search;

import edu.harvard.iq.dataverse.DatasetDao;
import edu.harvard.iq.dataverse.DataverseDao;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.search.index.IndexServiceBean;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.awaitility.Awaitility;

import javax.inject.Inject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class SolrIndexCleaner {

    @Inject
    private DataverseDao dataverseDao;
    
    @Inject
    private DatasetDao datasetDao;

    @Inject
    private SolrClient solrClient;
    
    @Inject
    private IndexServiceBean indexService;
    
    // -------------------- LOGIC --------------------
    
    /**
     * Recreates solr index to initial state
     * (that is with all dataverses, datasets and datafiles
     * that are currently in database)
     */
    public void cleanupSolrIndex() throws SolrServerException, IOException {
        
        solrClient.deleteByQuery("*:*");
        for (Dataverse dataverse: dataverseDao.findAll()) {
            if (dataverse.isRoot()) {
                continue;
            }
            indexService.indexDataverse(dataverse);
        }
        for (Dataset dataset: datasetDao.findAll()) {
            indexService.indexDataset(dataset, true);
        }

        solrClient.commit();

        SolrQuery query = new SolrQuery("*:*");
        query.setRows(0);
        Awaitility.await()
                .atMost(5, TimeUnit.MINUTES)
                .pollInterval(2, TimeUnit.SECONDS)
                .until(() -> {
                    long numFound = solrClient.query(query).getResults().getNumFound();
                    System.out.println("Number of documents: " + numFound);
                    return numFound == 44;
                });
    }
}
