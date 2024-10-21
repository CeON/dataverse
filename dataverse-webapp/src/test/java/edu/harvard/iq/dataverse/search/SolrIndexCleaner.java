package edu.harvard.iq.dataverse.search;

import com.google.common.base.Stopwatch;
import edu.harvard.iq.dataverse.DatasetDao;
import edu.harvard.iq.dataverse.DataverseDao;
import edu.harvard.iq.dataverse.search.index.IndexServiceBean;
import io.vavr.control.Try;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class SolrIndexCleaner {

    private static final Logger log = LoggerFactory.getLogger(SolrIndexCleaner.class);
    @Inject
    private DataverseDao dataverseDao;
    
    @Inject
    private DatasetDao datasetDao;

    @Inject
    private SolrClient solrClient;
    
    @Inject
    private IndexServiceBean indexService;

    private static boolean indexedAtLeastOnce = false;
    
    // -------------------- LOGIC --------------------
    
    /**
     * Recreates solr index to initial state
     * (that is with all dataverses, datasets and datafiles
     * that are currently in database)
     */
    public void cleanupSolrIndex() throws SolrServerException, IOException {
        Stopwatch watch = new Stopwatch().start();
        long beforeDelete = countSolrDocuments();
        log.info("********* {} Number of solr documents before delete: {}", watch.elapsedMillis(), beforeDelete);

        if (beforeDelete == 0 && indexedAtLeastOnce) {
            throw new RuntimeException("No solr documents found.");
        }

        new UpdateRequest().deleteByQuery("*:*").commit(solrClient, null);

        log.info("********* {} Number of solr documents after delete: {}", watch.elapsedMillis(), countSolrDocuments());

        long numIndexed = Stream.concat(indexDataverses(), indexDatasets()).mapToInt(f -> {
            log.info("********* Index result: {}", Try.of(f::get).get());
            return 1;
        }).sum();

        log.info("********* {} Number of indexed documents: {}", watch.elapsedMillis(), numIndexed);

        Awaitility.await()
                .pollInterval(5, TimeUnit.SECONDS)
                .atMost(1, TimeUnit.MINUTES).until(() -> {
            long numSolr = countSolrDocuments();
            log.info("********* {} Number of solr documents: {}", watch.elapsedMillis(), numSolr);
            return numSolr == 44;
        });

        indexedAtLeastOnce = true;
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

    public void logTestStart(String what) {
        log.info("********* test start: {}", what);
    }

    public void logTestEnd(String what) {
        log.info("********* test end: {}", what);
    }
}
