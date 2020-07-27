package edu.harvard.iq.dataverse.api.datadeposit;

import edu.harvard.iq.dataverse.DataverseDao;
import edu.harvard.iq.dataverse.PermissionServiceBean;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.Permission;
import edu.harvard.iq.dataverse.util.SystemConfig;
import org.swordapp.server.AuthCredentials;
import org.swordapp.server.ServiceDocument;
import org.swordapp.server.ServiceDocumentManager;
import org.swordapp.server.SwordAuthException;
import org.swordapp.server.SwordCollection;
import org.swordapp.server.SwordConfiguration;
import org.swordapp.server.SwordError;
import org.swordapp.server.SwordServerException;
import org.swordapp.server.SwordWorkspace;
import org.swordapp.server.UriRegistry;

import javax.ejb.EJB;
import javax.inject.Inject;
import java.util.List;
import java.util.logging.Logger;

public class ServiceDocumentManagerImpl implements ServiceDocumentManager {

    private static final Logger logger = Logger.getLogger(ServiceDocumentManagerImpl.class.getCanonicalName());
    @EJB
    DataverseDao dataverseDao;
    @EJB
    PermissionServiceBean permissionService;
    @EJB
    SystemConfig systemConfig;
    @Inject
    SwordAuth swordAuth;
    @Inject
    private UrlManagerServiceBean urlManagerServiceBean;

    @Override
    public ServiceDocument getServiceDocument(String sdUri, AuthCredentials authCredentials, SwordConfiguration config)
            throws SwordError, SwordServerException, SwordAuthException {

        AuthenticatedUser user = swordAuth.auth(authCredentials);
        UrlManager urlManager = urlManagerServiceBean.getUrlManager(sdUri);
        String warning = urlManager.getWarning();
        ServiceDocument service = new ServiceDocument();
        SwordWorkspace swordWorkspace = new SwordWorkspace();
        Dataverse rootDataverse = dataverseDao.findRootDataverse();
        if (rootDataverse != null) {
            String name = rootDataverse.getName();
            if (name != null) {
                swordWorkspace.setTitle(name);
            }
        }
        if (warning != null) {
            swordWorkspace.getWrappedWorkspace().setAttributeValue("warning", warning);
        }
        service.setMaxUploadSize(config.getMaxUploadSize());
        String hostnamePlusBaseUrl = urlManagerServiceBean.getHostnamePlusBaseUrlPath();
        if (hostnamePlusBaseUrl == null) {
            ServiceDocument serviceDocument = new ServiceDocument();
            return serviceDocument;
        }

        /**
         * We don't expect this to support Shibboleth groups because even though
         * a Shibboleth user can have an API token the transient
         * shibIdentityProvider String on AuthenticatedUser is only set when a
         * SAML assertion is made at runtime via the browser.
         */
        List<Dataverse> dataverses = permissionService.getDataversesUserHasPermissionOn(user, Permission.AddDataset);
        for (Dataverse dataverse : dataverses) {
            String dvAlias = dataverse.getAlias();
            if (dvAlias != null && !dvAlias.isEmpty()) {
                SwordCollection swordCollection = new SwordCollection();
                swordCollection.setTitle(dataverse.getName());
                swordCollection.setHref(hostnamePlusBaseUrl + "/collection/dataverse/" + dvAlias);
                swordCollection.addAcceptPackaging(UriRegistry.PACKAGE_SIMPLE_ZIP);
                swordCollection.setCollectionPolicy(systemConfig.getApiTermsOfUse());
                swordWorkspace.addCollection(swordCollection);
            }
        }
        service.addWorkspace(swordWorkspace);

        return service;
    }

}
