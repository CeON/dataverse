package edu.harvard.iq.dataverse.search;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;

import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.Specializes;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * CDI compliant factory of {@link SolrClient} objects
 * to be used in test environment.
 * <p>
 * Class will start solr server (with dataverse specific
 * configuration) inside docker container.
 * 
 * @author madryk
 * @see /docker/test_solr/Dockerfile in test resources
 */
public class TestSolrClientFactory extends SolrClientFactory {

    private static final Logger LOGGER = Logger.getLogger(TestSolrClientFactory.class.getCanonicalName());

    private static final String DEFAULT_SOLR_TEST_PORT = "8984";
    private static final String DEFAULT_SOLR_TEST_HOST = "localhost";

    // -------------------- LOGIC --------------------
    
    @Produces
    @Specializes
    public SolrClient produceSolrClient() throws IOException {
        String urlString = resolveSolrURL() + "/solr/collection1";
        LOGGER.fine("Creating test SolrClient at url: " + urlString);
        
        return new HttpSolrClient.Builder(urlString).build();
    }

    @Produces
    @Specializes
    @RorSolrClient
    public SolrClient produceRorSolrClient() {
        String urlString = resolveSolrURL() + "/solr/rorSuggestions";
        LOGGER.fine("Creating test SolrClient at url: " + urlString);

        return new HttpSolrClient.Builder(urlString).build();
    }


    public void disposeSolrClient(@Disposes SolrClient solrClient) throws IOException {
        solrClient.close();
    }

    // -------------------- PRIVATE --------------------

    private static String resolveSolrURL() {
        return "http://" + getPropertyOrDefault("test.solr.host", DEFAULT_SOLR_TEST_HOST) + ":" +
                getPropertyOrDefault("test.solr.port", DEFAULT_SOLR_TEST_PORT);
    }

    private static String getPropertyOrDefault(String property, String defaultValue) {
        String port = System.getProperty(property);
        if (port == null) {
            return defaultValue;
        }
        return port;
    }

}
