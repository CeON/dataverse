package edu.harvard.iq.dataverse.dataverse;

import com.google.api.client.util.Lists;
import edu.harvard.iq.dataverse.Dataverse;
import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.arquillian.DataverseArquillian;
import edu.harvard.iq.dataverse.arquillian.arquillianexamples.ArquillianDeployment;
import edu.harvard.iq.dataverse.authorization.users.AuthenticatedUser;
import org.jboss.arquillian.transaction.api.annotation.TransactionMode;
import org.jboss.arquillian.transaction.api.annotation.Transactional;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.primefaces.model.DualListModel;

import javax.ejb.EJB;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

@RunWith(DataverseArquillian.class)
@Transactional(TransactionMode.ROLLBACK)
public class DataverseSaverIT extends ArquillianDeployment {

    @PersistenceContext(unitName = "VDCNet-ejbPU")
    private EntityManager em;

    @EJB
    private DataverseSaver dataverseSaver;

    @Inject
    private DataverseSession dataverseSession;

    private FacesContext facesContext;

    @Before
    public void init() {
        facesContext = FacesContextMocker.mockServletRequest();
    }

    @org.junit.Test
    public void saveNewDataverse() {
        //given
        Dataverse dataverse = prepareDataverse();
        createUser();

        //when
        dataverseSaver.saveNewDataverse(Lists.newArrayList(), dataverse, new DualListModel<>());

        //then
    }

    @org.junit.Test
    public void saveEditedDataverse() {
    }

    private Dataverse prepareDataverse() {
        Dataverse dataverse = new Dataverse();
        dataverse.setMetadataBlockRoot(true);
        dataverse.setName("NICE DATAVERSE");
        dataverse.setAlias("FIRST DATAVERSE");
        dataverse.setDataverseType(Dataverse.DataverseType.UNCATEGORIZED);
        dataverse.setAllowMessagesBanners(false);

        return dataverse;
    }

    private void createUser() {
        AuthenticatedUser user = new AuthenticatedUser();
        user.setSuperuser(true);
        dataverseSession.setUser(user);
    }
}