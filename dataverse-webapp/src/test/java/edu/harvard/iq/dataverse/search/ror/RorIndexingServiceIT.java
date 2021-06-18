package edu.harvard.iq.dataverse.search.ror;

import com.google.common.collect.ImmutableSet;
import edu.harvard.iq.dataverse.arquillian.arquillianexamples.WebappArquillianDeployment;
import edu.harvard.iq.dataverse.persistence.ror.RorData;
import edu.harvard.iq.dataverse.persistence.ror.RorLabel;
import edu.harvard.iq.dataverse.search.RorSolrClient;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.assertj.core.api.Assertions;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.transaction.api.annotation.TransactionMode;
import org.jboss.arquillian.transaction.api.annotation.Transactional;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import java.io.IOException;

@RunWith(Arquillian.class)
@Transactional(TransactionMode.ROLLBACK)
public class RorIndexingServiceIT extends WebappArquillianDeployment {

    @Inject
    private RorIndexingService rorIndexingService;

    @Inject
    @RorSolrClient
    private SolrClient solrClient;

    @Test
    public void indexRorRecord() throws IOException, SolrServerException {
        //given
        String rorId = "testRor";
        String name = "testName";
        String countryName = "Poland";
        String countryCode = "PL";
        final ImmutableSet<String> aliases = ImmutableSet.of("alias");
        final ImmutableSet<String> acronyms = ImmutableSet.of("acronym");
        final ImmutableSet<RorLabel> labels = ImmutableSet.of(new RorLabel("label", "123"));

        final RorData rorData = new RorData(rorId, name, countryName, countryCode, aliases, acronyms, labels);
        rorData.setId(1L);

        //when
        final UpdateResponse updateResponse = rorIndexingService.indexRorRecord(rorData);
        final QueryResponse response = solrClient.query(new SolrQuery("*"));

        //then
        Assertions.assertThat(updateResponse.getStatus()).isEqualTo(0);
        Assertions.assertThat(response.getResults().size()).isEqualTo(1);

    }
}
