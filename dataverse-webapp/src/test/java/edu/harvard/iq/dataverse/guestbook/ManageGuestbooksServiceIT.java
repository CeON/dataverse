package edu.harvard.iq.dataverse.guestbook;

import edu.harvard.iq.dataverse.DataverseServiceBean;
import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.GuestbookServiceBean;
import edu.harvard.iq.dataverse.arquillian.arquillianexamples.WebappArquillianDeployment;
import edu.harvard.iq.dataverse.authorization.AuthenticationServiceBean;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.transaction.api.annotation.TransactionMode;
import org.jboss.arquillian.transaction.api.annotation.Transactional;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

@RunWith(Arquillian.class)
@Transactional(TransactionMode.ROLLBACK)
public class ManageGuestbooksServiceIT extends WebappArquillianDeployment {
    @PersistenceContext(unitName = "VDCNet-ejbPU")
    private EntityManager em;

    @EJB
    private ManageGuestbooksService manageGuestbooksService;

    @Inject
    private GuestbookServiceBean guestbookService;

    @Inject
    private DataverseServiceBean dataverseService;

    @Inject
    private DataverseSession dataverseSession;

    @EJB
    private AuthenticationServiceBean authenticationServiceBean;

    @Test
    public void shouldDeleteGuestbook() {
        // given
        dataverseSession.setUser(authenticationServiceBean.getAdminUser());
        Dataverse dataverse = dataverseService.findByAlias("ownmetadatablocks");
        long guestbookId = dataverse.getGuestbooks().get(0).getId();

        // when
        manageGuestbooksService.deleteGuestbook(guestbookId);

        // then
        Assert.assertEquals(0, dataverseService.findByAlias("ownmetadatablocks").getGuestbooks().size());
        Assert.assertNull(guestbookService.find(guestbookId));
    }


    @Test
    public void shouldEnableGuestbook() {
        // given
        dataverseSession.setUser(authenticationServiceBean.getAdminUser());
        Dataverse dataverse = dataverseService.findByAlias("ownmetadatablocks");
        long guestbookId = dataverse.getGuestbooks().get(0).getId();

        // when
        manageGuestbooksService.enableGuestbook(guestbookId);

        // then
        Assert.assertTrue(dataverseService.findByAlias("ownmetadatablocks").getGuestbooks().get(0).isEnabled());
        Assert.assertTrue(guestbookService.find(guestbookId).isEnabled());
    }
}
