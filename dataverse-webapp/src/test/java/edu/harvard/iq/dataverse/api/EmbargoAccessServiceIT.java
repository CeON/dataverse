package edu.harvard.iq.dataverse.api;

import edu.harvard.iq.dataverse.DatasetDao;
import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.arquillian.arquillianexamples.WebappArquillianDeployment;
import edu.harvard.iq.dataverse.authorization.AuthenticationServiceBean;
import edu.harvard.iq.dataverse.dataset.EmbargoAccessService;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.user.GuestUser;
import org.jboss.arquillian.transaction.api.annotation.TransactionMode;
import org.jboss.arquillian.transaction.api.annotation.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.ejb.EJB;
import javax.inject.Inject;
import java.sql.Date;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Transactional(TransactionMode.ROLLBACK)
public class EmbargoAccessServiceIT extends WebappArquillianDeployment {

    @Inject
    private EmbargoAccessService embargoAccess;

    @Inject
    private DatasetDao datasetDao;

    @Inject
    private DataverseSession dataverseSession;

    @EJB
    private AuthenticationServiceBean authenticationService;

    @Inject
    private DataverseRequestServiceBean dvRequest;

    @Test
    public void shouldCheckEmbargoRestriction_userWithPermissions() {
        // given
        Dataset dataset = datasetDao.find(57L);
        dataset.setEmbargoDate(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)));
        dataverseSession.setUser(authenticationService.getAdminUser());

        // when&then
        Assertions.assertFalse(embargoAccess.isRestrictedByEmbargo(dataset));
    }

    @Test
    public void shouldCheckEmbargoRestriction_userWithoutPermissions() {
        // given
        Dataset dataset = datasetDao.find(57L);
        dataset.setEmbargoDate(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)));
        dataverseSession.setUser(GuestUser.get());

        // when&then
        Assertions.assertTrue(embargoAccess.isRestrictedByEmbargo(dataset));
    }
}
