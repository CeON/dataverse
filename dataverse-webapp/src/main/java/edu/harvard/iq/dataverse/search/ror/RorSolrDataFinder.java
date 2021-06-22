package edu.harvard.iq.dataverse.search.ror;

import edu.harvard.iq.dataverse.search.RorSolrClient;
import edu.harvard.iq.dataverse.search.query.SolrQuerySanitizer;
import io.vavr.control.Try;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.List;

/**
 * Solr data finder dedicated for ROR collection
 */
@Stateless
public class RorSolrDataFinder {

    @Inject
    @RorSolrClient
    private SolrClient solrClient;

    @Inject
    private SolrQuerySanitizer solrQuerySanitizer;

    public List<RorDto> findRorData(String searchPhrase) {
        StringBuilder queryBuilder = new StringBuilder();
        String cleanQuery = solrQuerySanitizer.sanitizeRorQuery(searchPhrase);

        String[] slicedPhrases = cleanQuery.split(" ");

        for (int loopIndex = 0; loopIndex < slicedPhrases.length; loopIndex++) {
            queryBuilder.append(slicedPhrases[loopIndex]);
            queryBuilder.append("*");

            if (isNotLastWord(slicedPhrases, loopIndex)){
                queryBuilder.append(" AND ");
            }
        }

        SolrQuery solrQuery = new SolrQuery(queryBuilder.toString());

        QueryResponse response = Try.of(() -> solrClient.query(solrQuery))
                                    .getOrElseThrow(throwable -> new IllegalStateException("Unable to query ror collection in solr.", throwable));

        return response.getBeans(RorDto.class);
    }

    private boolean isNotLastWord(String[] slicedPhrases, int loopIndex) {
        return loopIndex != slicedPhrases.length - 1;
    }
}
