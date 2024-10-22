package edu.harvard.iq.dataverse.dataverse;

import com.google.common.collect.Lists;
import edu.harvard.iq.dataverse.DataverseDao;
import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.UserServiceBean;
import edu.harvard.iq.dataverse.arquillian.arquillianexamples.WebappArquillianDeployment;
import edu.harvard.iq.dataverse.arquillian.facesmock.FacesContextMocker;
import edu.harvard.iq.dataverse.engine.command.exception.IllegalCommandException;
import edu.harvard.iq.dataverse.error.DataverseError;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.dataverse.DataverseContact;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.GuestUser;
import edu.harvard.iq.dataverse.search.SolrIndexCleaner;
import io.vavr.control.Either;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrDocument;
import org.jboss.arquillian.transaction.api.annotation.TransactionMode;
import org.jboss.arquillian.transaction.api.annotation.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.primefaces.model.DualListModel;

import javax.ejb.EJBTransactionRolledbackException;
import javax.inject.Inject;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static edu.harvard.iq.dataverse.search.DvObjectsSolrAssert.assertDataversePermSolrDocument;
import static edu.harvard.iq.dataverse.search.DvObjectsSolrAssert.assertDataverseSolrDocument;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DataverseServiceIT extends WebappArquillianDeployment {

    @Inject
    private DataverseService dataverseService;

    @Inject
    private DataverseSession dataverseSession;

    @Inject
    private DataverseDao dataverseDao;

    @Inject
    private UserServiceBean userServiceBean;

    @Inject
    private SolrClient solrClient;

    @Inject
    private SolrIndexCleaner solrIndexCleaner;

    @BeforeEach
    public void init() throws SolrServerException, IOException, SQLException {
        FacesContextMocker.mockServletRequest();
        solrIndexCleaner.cleanupSolrIndex();
    }

    // -------------------- TESTS --------------------

    @Test
    public void saveNewDataverse_ShouldSuccessfullySave() throws SolrServerException, IOException, InterruptedException {
        //given
        long userId = loginSessionWithSuperUser();
        Dataverse dataverse = prepareDataverse();

        //when
        Either<DataverseError, Dataverse> savedDataverse = dataverseService.saveNewDataverse(Lists.newArrayList(), dataverse, new DualListModel<>());


        //then
        assertTrue(savedDataverse.isRight());

        Dataverse dbDataverse = dataverseDao.find(savedDataverse.get().getId());
        assertEquals("NICE DATAVERSE", dbDataverse.getName());

        await()
                .atMost(Duration.ofSeconds(15L))
                .until(() -> smtpServer.getMails().stream()
                        .anyMatch(emailModel -> emailModel.getSubject().contains("Your dataverse has been created")));

        await().atMost(Duration.ofSeconds(15L))
                .until(() -> solrClient.getById("dataverse_" + savedDataverse.get().getId()) != null);

        SolrDocument dataverseSolrDoc = solrClient.getById("dataverse_" + savedDataverse.get().getId());
        assertDataverseSolrDocument(dataverseSolrDoc, savedDataverse.get().getId(), "FIRSTDATAVERSE", "NICE DATAVERSE");

        SolrDocument dataversePermSolrDoc = solrClient.getById("dataverse_" + savedDataverse.get().getId() + "_permission");
        assertDataversePermSolrDocument(dataversePermSolrDoc, savedDataverse.get().getId(), Lists.newArrayList(userId));
    }

    @Test
    @Transactional(TransactionMode.ROLLBACK)
    public void saveNewDataverse_WithWrongUser() {
        //given
        final int EXPECTED_DV_COUNT = 9; // Root + 8 dataverses from dbinit

        Dataverse dataverse = prepareDataverse();
        dataverseSession.setUser(GuestUser.get());

        //when
        Either<DataverseError, Dataverse> savedDataverse = dataverseService.saveNewDataverse(Lists.newArrayList(), dataverse, new DualListModel<>());

        //then
        Assertions.assertTrue(savedDataverse.isLeft());
        Assertions.assertEquals(EXPECTED_DV_COUNT, dataverseDao.findAll().size());
    }

    @Test
    @Transactional(TransactionMode.ROLLBACK)
    public void saveEditedDataverse() {
        //given
        loginSessionWithSuperUser();
        Dataverse dataverse = dataverseDao.findRootDataverse();
        String oldDataverseName = dataverse.getName();
        dataverse.setName("UPDATED DATAVERSE");

        //when
        Either<DataverseError, Dataverse> updatedDataverse = dataverseService.saveEditedDataverse(Lists.newArrayList(), dataverse, new DualListModel<>());

        //then
        Assertions.assertNotEquals(oldDataverseName, updatedDataverse.get().getName());
    }

    @Test
    @Transactional(TransactionMode.ROLLBACK)
    public void saveEditedDataverse_WithNonExistingDataverse() {
        //given
        Dataverse dataverse = new Dataverse();

        //when & then
        Assertions.assertThrows(EJBTransactionRolledbackException.class,
                () -> dataverseService.saveEditedDataverse(Lists.newArrayList(), dataverse, new DualListModel<>()));
    }

    @Test
    @Transactional(TransactionMode.ROLLBACK)
    public void publishDataverse() {
        //given
        loginSessionWithSuperUser();
        Dataverse unpublishedDataverse = dataverseDao.findRootDataverse();

        //when
        dataverseService.publishDataverse(unpublishedDataverse);
        Dataverse publishedDataverse = dataverseDao.find(unpublishedDataverse.getId());

        //then
        Assertions.assertTrue(publishedDataverse.isReleased());
    }

    @Test
    @Transactional(TransactionMode.ROLLBACK)
    public void publishDataverse_WithIllegalCommandException() {
        //given
        loginSessionWithSuperUser();
        Dataverse unpublishedDataverse = dataverseDao.findRootDataverse();
        unpublishedDataverse.setPublicationDate(Timestamp.from(Instant.ofEpochMilli(1573738827897L)));

        //when & then
        Assertions.assertThrows(IllegalCommandException.class, () -> dataverseService.publishDataverse(unpublishedDataverse));
    }

    @Test
    @Transactional(TransactionMode.ROLLBACK)
    public void deleteDataverse() {
        //given
        loginSessionWithSuperUser();
        Dataverse unpublishedDataverse = dataverseDao.find(67L);

        //when
        dataverseService.deleteDataverse(unpublishedDataverse);

        //then
        Assertions.assertNull(dataverseDao.find(67L));
    }

    @Test
    @Transactional(TransactionMode.ROLLBACK)
    public void deleteDataverse_withData() {
        //given
        loginSessionWithSuperUser();
        Dataverse dataverseWithData = dataverseDao.find(19L);

        //when & then
        Exception thrown = assertThrows(IllegalCommandException.class, () -> dataverseService.deleteDataverse(dataverseWithData));
        Assertions.assertEquals("Cannot delete non-empty dataverses", thrown.getMessage());
    }

    @Test
    @Transactional(TransactionMode.ROLLBACK)
    public void deleteDataverse_WithIllegalCommandException() {
        //given
        loginSessionWithSuperUser();
        Dataverse unpublishedDataverse = dataverseDao.find(19L);
        unpublishedDataverse.setOwner(null);

        //when
        Assertions.assertThrows(IllegalCommandException.class, () -> dataverseService.deleteDataverse(unpublishedDataverse));
    }

    // -------------------- PRIVATE --------------------

    private long loginSessionWithSuperUser() {
        AuthenticatedUser user = userServiceBean.find(2L);
        dataverseSession.setUser(user);
        return user.getId();
    }

    private Dataverse prepareDataverse() {
        Dataverse dataverse = new Dataverse();
        dataverse.setMetadataBlockRoot(true);
        dataverse.setOwner(dataverseDao.findRootDataverse());
        dataverse.setName("NICE DATAVERSE");
        dataverse.setAlias("FIRSTDATAVERSE");
        dataverse.setFacetRoot(true);
        dataverse.setDataverseType(Dataverse.DataverseType.JOURNALS);
        dataverse.setDataverseContacts(prepareDataverseContact());
        dataverse.setAllowMessagesBanners(false);

        return dataverse;
    }

    private List<DataverseContact> prepareDataverseContact() {
        DataverseContact dataverseContact = new DataverseContact();
        dataverseContact.setContactEmail("test@gmail.com");

        ArrayList<DataverseContact> contacts = new ArrayList<>();
        contacts.add(dataverseContact);
        return contacts;
    }
}