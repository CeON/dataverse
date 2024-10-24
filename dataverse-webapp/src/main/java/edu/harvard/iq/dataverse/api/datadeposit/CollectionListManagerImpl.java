package edu.harvard.iq.dataverse.api.datadeposit;

import edu.harvard.iq.dataverse.DatasetDao;
import edu.harvard.iq.dataverse.DataverseDao;
import edu.harvard.iq.dataverse.PermissionServiceBean;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDatasetVersionCommand;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.Permission;
import org.apache.abdera.Abdera;
import org.apache.abdera.i18n.iri.IRI;
import org.apache.abdera.model.Entry;
import org.apache.abdera.model.Feed;
import org.swordapp.server.AuthCredentials;
import org.swordapp.server.CollectionListManager;
import org.swordapp.server.SwordAuthException;
import org.swordapp.server.SwordConfiguration;
import org.swordapp.server.SwordError;
import org.swordapp.server.SwordServerException;
import org.swordapp.server.UriRegistry;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.xml.namespace.QName;
import java.util.List;
import java.util.logging.Logger;

public class CollectionListManagerImpl implements CollectionListManager {

    private static final Logger logger = Logger.getLogger(CollectionListManagerImpl.class.getCanonicalName());
    @EJB
    DataverseDao dataverseDao;
    @EJB
    DatasetDao datasetDao;
    @EJB
    PermissionServiceBean permissionService;
    @Inject
    SwordAuth swordAuth;
    @Inject
    private UrlManagerServiceBean urlManagerServiceBean;

    private HttpServletRequest request;

    @Override
    public Feed listCollectionContents(IRI iri, AuthCredentials authCredentials, SwordConfiguration swordConfiguration) throws SwordServerException, SwordAuthException, SwordError {
        AuthenticatedUser user = swordAuth.auth(authCredentials);
        DataverseRequest dvReq = new DataverseRequest(user, request);
        UrlManager urlManager = urlManagerServiceBean.getUrlManager(iri.toString());
        String dvAlias = urlManager.getTargetIdentifier();
        if (urlManager.getTargetType().equals("dataverse") && dvAlias != null) {

            Dataverse dv = dataverseDao.findByAlias(dvAlias);

            if (dv != null) {
                /**
                 * We'll say having AddDataset is enough to use this API
                 * endpoint, which means you are a Contributor to that
                 * dataverse. If we let just anyone call this endpoint, they
                 * will be able to see if the supplied dataverse is published or
                 * not.
                 */
                if (!permissionService.requestOn(dvReq, dv).has(Permission.AddDataset)) {
                    throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "user " + user.getDisplayInfo().getTitle() + " is not authorized to list datasets in dataverse " + dv.getAlias());
                }
                Abdera abdera = new Abdera();
                Feed feed = abdera.newFeed();
                feed.setTitle(dv.getName());
                String baseUrl = urlManagerServiceBean.getHostnamePlusBaseUrlPath();
                List<Dataset> datasets = datasetDao.findByOwnerId(dv.getId());
                for (Dataset dataset : datasets) {
                    /**
                     * @todo Will this be performant enough with production
                     * data, say in the root dataverse? Remove this todo if
                     * there are no complaints. :)
                     */
                    if (!permissionService.isUserAllowedOn(user, new UpdateDatasetVersionCommand(dataset, dvReq), dataset)) {
                        continue;
                    }
                    String editUri = baseUrl + "/edit/study/" + dataset.getGlobalIdString();
                    String editMediaUri = baseUrl + "/edit-media/study/" + dataset.getGlobalIdString();
                    Entry entry = feed.addEntry();
                    entry.setId(editUri);
                    entry.setTitle(datasetDao.getTitleFromLatestVersion(dataset.getId()));
                    entry.setBaseUri(new IRI(editUri));
                    entry.addLink(editMediaUri, "edit-media");
                    feed.addEntry(entry);
                }
                Boolean dvHasBeenReleased = dv.isReleased();
                feed.addSimpleExtension(new QName(UriRegistry.SWORD_STATE, "dataverseHasBeenReleased"), dvHasBeenReleased.toString());
                return feed;
            } else {
                throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Could not find dataverse: " + dvAlias);
            }
        } else {
            throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Couldn't determine target type or identifer from URL: " + iri);
        }
    }

    public void setRequest(HttpServletRequest request) {
        this.request = request;
    }

}
