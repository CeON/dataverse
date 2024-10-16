package edu.harvard.iq.dataverse.search;

import edu.harvard.iq.dataverse.DatasetDao;
import edu.harvard.iq.dataverse.DataverseDao;
import edu.harvard.iq.dataverse.search.index.IndexServiceBean;
import io.vavr.control.Try;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.awaitility.Awaitility;

import javax.inject.Inject;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

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

        System.out.println("Number of solr documents before delete: " + countSolrDocuments());

        solrClient.deleteByQuery("*:*");
        solrClient.commit();

        System.out.println("Number of solr documents after delete: " + countSolrDocuments());

        long numIndexed = Stream.concat(indexDataverses(), indexDatasets()).mapToInt(f -> {
            Try.of(f::get).get();
            return 1;
        }).sum();

        System.out.println("Number of indexed documents: " + numIndexed);

        solrClient.commit();

        Awaitility.await()
                .pollInterval(5, TimeUnit.SECONDS)
                .atMost(10, TimeUnit.MINUTES).until(() -> {
            long numSolr = countSolrDocuments();
            System.out.println("Number of solr documents: " + numSolr);
            return numSolr == 44;
        });
    }

    private long countSolrDocuments() throws SolrServerException, IOException {
        SolrQuery query = new SolrQuery("*:*");
        query.setRows(0);
        return solrClient.query(query).getResults().getNumFound();
    }

    private Stream<Future<String>> indexDatasets() {
        return datasetDao.findAll().stream().map(dataset -> indexService.indexDataset(dataset, true));
    }

    private Stream<Future<String>> indexDataverses() {
        return dataverseDao.findAll().stream().map(dataverse -> {
            if (dataverse.isRoot()) {
                return CompletableFuture.completedFuture("Root");
            }
            return indexService.indexDataverse(dataverse);
        });
    }
}
