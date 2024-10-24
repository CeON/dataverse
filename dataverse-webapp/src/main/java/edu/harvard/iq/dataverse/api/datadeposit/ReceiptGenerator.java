package edu.harvard.iq.dataverse.api.datadeposit;

import edu.harvard.iq.dataverse.citation.CitationFactory;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import org.apache.abdera.i18n.iri.IRI;
import org.swordapp.server.DepositReceipt;

import java.util.logging.Logger;

public class ReceiptGenerator {

    private static final Logger logger = Logger.getLogger(ReceiptGenerator.class.getCanonicalName());

    private final CitationFactory citationFactory;

    // -------------------- CONSTRUCTORS --------------------

    public ReceiptGenerator(CitationFactory citationFactory) {
        this.citationFactory = citationFactory;
    }

    // -------------------- LOGIC --------------------

    DepositReceipt createDatasetReceipt(String baseUrl, Dataset dataset) {
        logger.fine("baseUrl was: " + baseUrl);
        DepositReceipt depositReceipt = new DepositReceipt();
        String globalId = dataset.getGlobalIdString();
        String editIri = baseUrl + "/edit/study/" + globalId;
        depositReceipt.setEditIRI(new IRI(editIri));
        /**
         * @todo: should setLocation depend on if an atom entry or a zip file
         * was deposited? (This @todo has been carried over from the DVN 3.x
         * version.)
         */
        depositReceipt.setLocation(new IRI(editIri));
        depositReceipt.setEditMediaIRI(new IRI(baseUrl + "/edit-media/study/" + globalId));
        depositReceipt.setStatementURI("application/atom+xml;type=feed", baseUrl + "/statement/study/" + globalId);
        depositReceipt.addDublinCore("bibliographicCitation", citationFactory.create(dataset.getLatestVersion()).toString(false));
        depositReceipt.setSplashUri(dataset.getPersistentURL());
        return depositReceipt;
    }

    DepositReceipt createDataverseReceipt(String baseUrl, Dataverse dataverse) {
        logger.fine("baseUrl was: " + baseUrl);
        DepositReceipt depositReceipt = new DepositReceipt();
        String globalId = dataverse.getAlias();
        String collectionIri = baseUrl + "/collection/dataverse/" + globalId;
        depositReceipt.setSplashUri(collectionIri);
        /**
         * @todo We have to include an "edit" IRI or else we get
         * NullPointerException in getAbderaEntry at
         * https://github.com/swordapp/JavaServer2.0/blob/sword2-server-1.0/src/main/java/org/swordapp/server/DepositReceipt.java#L52
         *
         * Do we want to support a replaceMetadata of dataverses? Probably not.
         * Let's do that with the native API.
         *
         * Typically, we only operate on the "collection" IRI for dataverses, to
         * create a dataset.
         */
        String editIri = baseUrl + "/edit/dataverse/" + globalId;
        depositReceipt.setEditIRI(new IRI(editIri));
        return depositReceipt;
    }

}
