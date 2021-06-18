package edu.harvard.iq.dataverse.search.ror;

import edu.harvard.iq.dataverse.persistence.ror.RorData;
import edu.harvard.iq.dataverse.search.RorSolrClient;
import io.vavr.control.Try;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service dedicate for indexing ROR data.
 */
@Stateless
public class RorIndexingService {

    private static final Logger logger = Logger.getLogger(RorIndexingService.class.getCanonicalName());

    @Inject
    @RorSolrClient
    private SolrClient solrServer;

    public UpdateResponse indexRorRecord(RorData rorData) {
        assert rorData.getId() != null;

        SolrInputDocument solrInputDocument = new SolrInputDocument();

        solrInputDocument.addField(RorSolrField.ID.getExactName(), rorData.getId());
        solrInputDocument.addField(RorSolrField.ROR_ID.getExactName(), rorData.getRorId());
        solrInputDocument.addField(RorSolrField.NAME.getExactName(), rorData.getName());
        solrInputDocument.addField(RorSolrField.COUNTRY_NAME.getExactName(), rorData.getCountryName());
        solrInputDocument.addField(RorSolrField.COUNTRY_CODE.getExactName(), rorData.getCountryCode());

        rorData.getNameAliases().forEach(nameAlias -> solrInputDocument.addField(RorSolrField.NAME_ALIAS.getExactName(), nameAlias));
        rorData.getAcronyms().forEach(acronym -> solrInputDocument.addField(RorSolrField.ACRONYM.getExactName(), acronym));
        rorData.getLabels().forEach(label -> solrInputDocument.addField(RorSolrField.LABEL.getExactName(), label.getLabel()));

        Try.of(() -> solrServer.add(solrInputDocument))
           .onFailure(throwable -> logger.log(Level.WARNING, "Unable to add ror record with id: " + rorData.getId()));

        return Try.of(() -> solrServer.commit())
                  .getOrElseThrow(throwable -> new IllegalStateException("Unable to commit ror data to solr.", throwable));

    }
}
