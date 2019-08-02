package edu.harvard.iq.dataverse.util;

import edu.harvard.iq.dataverse.UserNotification;
import edu.harvard.iq.dataverse.notification.NotificationType;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MailUtilTest {

    private String rootDataverseName;
    UserNotification userNotification = new UserNotification();

    @Before
    public void setUp() {
        rootDataverseName = "LibraScholar";
        userNotification = new UserNotification();
    }

    @Test
    public void testParseSystemAddress() {
        assertEquals("support@librascholar.edu", MailUtil.parseSystemAddress("support@librascholar.edu").getAddress());
        assertEquals("support@librascholar.edu", MailUtil.parseSystemAddress("LibraScholar Support Team <support@librascholar.edu>").getAddress());
        assertEquals("LibraScholar Support Team", MailUtil.parseSystemAddress("LibraScholar Support Team <support@librascholar.edu>").getPersonal());
        assertEquals("support@librascholar.edu", MailUtil.parseSystemAddress("\"LibraScholar Support Team\" <support@librascholar.edu>").getAddress());
        assertEquals("LibraScholar Support Team", MailUtil.parseSystemAddress("\"LibraScholar Support Team\" <support@librascholar.edu>").getPersonal());
        assertEquals(null, MailUtil.parseSystemAddress(null));
        assertEquals(null, MailUtil.parseSystemAddress(""));
        assertEquals(null, MailUtil.parseSystemAddress("LibraScholar Support Team support@librascholar.edu"));
        assertEquals(null, MailUtil.parseSystemAddress("\"LibraScholar Support Team <support@librascholar.edu>"));
        assertEquals(null, MailUtil.parseSystemAddress("support1@dataverse.org, support@librascholar.edu"));
    }

    @Test
    public void testSubjectCreateAccount() {
        userNotification.setType(NotificationType.CREATEACC);
        assertEquals("LibraScholar: Your account has been created", MailUtil.getSubjectTextBasedOnNotification(userNotification, rootDataverseName, null));
    }

    @Test
    public void testSubjectAssignRole() {
        userNotification.setType(NotificationType.ASSIGNROLE);
        assertEquals("LibraScholar: You have been assigned a role", MailUtil.getSubjectTextBasedOnNotification(userNotification, rootDataverseName, null));
    }

    @Test
    public void testSubjectCreateDataverse() {
        userNotification.setType(NotificationType.CREATEDV);
        assertEquals("LibraScholar: Your dataverse has been created", MailUtil.getSubjectTextBasedOnNotification(userNotification, rootDataverseName, null));
    }

    @Test
    public void testSubjectRevokeRole() {
        userNotification.setType(NotificationType.REVOKEROLE);
        assertEquals("LibraScholar: Your role has been revoked", MailUtil.getSubjectTextBasedOnNotification(userNotification, rootDataverseName, null));
    }

    @Test
    public void testSubjectRequestFileAccess() {
        userNotification.setType(NotificationType.REQUESTFILEACCESS);
        assertEquals("LibraScholar: Access has been requested for a restricted file", MailUtil.getSubjectTextBasedOnNotification(userNotification, rootDataverseName, null));
    }

    @Test
    public void testSubjectGrantFileAccess() {
        userNotification.setType(NotificationType.GRANTFILEACCESS);
        assertEquals("LibraScholar: You have been granted access to a restricted file", MailUtil.getSubjectTextBasedOnNotification(userNotification, rootDataverseName, null));
    }

    @Test
    public void testSubjectRejectFileAccess() {
        userNotification.setType(NotificationType.REJECTFILEACCESS);
        assertEquals("LibraScholar: Your request for access to a restricted file has been rejected", MailUtil.getSubjectTextBasedOnNotification(userNotification, rootDataverseName, null));
    }

    @Test
    public void testSubjectMapLayerUpdated() {
        userNotification.setType(NotificationType.MAPLAYERUPDATED);
        assertEquals("LibraScholar: WorldMap layer added to dataset", MailUtil.getSubjectTextBasedOnNotification(userNotification, rootDataverseName, null));
    }

    @Test
    public void testSubjectMapLayerDeleteFailed() {
        userNotification.setType(NotificationType.MAPLAYERDELETEFAILED);
        assertEquals("LibraScholar: Failed to delete WorldMap layer", MailUtil.getSubjectTextBasedOnNotification(userNotification, rootDataverseName, null));
    }

    @Test
    public void testSubjectCreateDataset() {
        userNotification.setType(NotificationType.CREATEDS);
        assertEquals("LibraScholar: Your dataset has been created", MailUtil.getSubjectTextBasedOnNotification(userNotification, rootDataverseName, null));
    }

    @Test
    public void testSubjectSubmittedDS() {
        userNotification.setType(NotificationType.SUBMITTEDDS);
        assertEquals("LibraScholar: Your dataset has been submitted for review", MailUtil.getSubjectTextBasedOnNotification(userNotification, rootDataverseName, null));
    }

    @Test
    public void testSubjectPublishedDS() {
        userNotification.setType(NotificationType.PUBLISHEDDS);
        assertEquals("LibraScholar: Your dataset has been published", MailUtil.getSubjectTextBasedOnNotification(userNotification, rootDataverseName, null));
    }

    @Test
    public void testSubjectReturnedDS() {
        userNotification.setType(NotificationType.RETURNEDDS);
        assertEquals("LibraScholar: Your dataset has been returned", MailUtil.getSubjectTextBasedOnNotification(userNotification, rootDataverseName, null));
    }

    @Test
    public void testSubjectChecksumFail() {
        userNotification.setType(NotificationType.CHECKSUMFAIL);
        assertEquals("LibraScholar: Your upload failed checksum validation", MailUtil.getSubjectTextBasedOnNotification(userNotification, rootDataverseName, null));
    }

    @Test
    public void testSubjectFileSystemImport() {
        userNotification.setType(NotificationType.FILESYSTEMIMPORT);
        //TODO SEK add a dataset version to get the Dataset Title which is actually used in the subject now
        assertEquals("Dataset LibraScholar has been successfully uploaded and verified", MailUtil.getSubjectTextBasedOnNotification(userNotification, rootDataverseName, null));
    }

    @Test
    public void testSubjectChecksumImport() {
        userNotification.setType(NotificationType.CHECKSUMIMPORT);
        assertEquals("LibraScholar: Your file checksum job has completed", MailUtil.getSubjectTextBasedOnNotification(userNotification, rootDataverseName, null));
    }

    @Test
    public void testSubjectConfirmEmail() {
        userNotification.setType(NotificationType.CONFIRMEMAIL);
        assertEquals("LibraScholar: Verify your email address", MailUtil.getSubjectTextBasedOnNotification(userNotification, rootDataverseName, null));
    }
}
