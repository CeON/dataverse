package edu.harvard.iq.dataverse.mail;

import edu.harvard.iq.dataverse.DataverseServiceBean;
import edu.harvard.iq.dataverse.GenericDao;
import edu.harvard.iq.dataverse.PermissionServiceBean;
import edu.harvard.iq.dataverse.common.BrandingUtil;
import edu.harvard.iq.dataverse.mail.confirmemail.ConfirmEmailServiceBean;
import edu.harvard.iq.dataverse.notification.NotificationObjectType;
import edu.harvard.iq.dataverse.notification.dto.EmailNotificationDto;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.NotificationType;
import edu.harvard.iq.dataverse.util.MailUtil;
import edu.harvard.iq.dataverse.util.SystemConfig;
import io.vavr.Tuple2;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.mail.internet.InternetAddress;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MailMessageCreatorTest {

    private MailMessageCreator mailMessageCreator;

    @Mock
    private SystemConfig systemConfig;

    @Mock
    private PermissionServiceBean permissionService;

    @Mock
    private DataverseServiceBean dataverseService;

    @Mock
    private ConfirmEmailServiceBean confirmEmailService;

    @Mock
    private GenericDao genericDao;

    private static String GUIDESBASEURL = "http://guides.dataverse.org";
    private static String GUIDESVERSION = "V8";
    private static String SITEURL = "http://localhost:8080";
    private static String ROOTDVNAME = "Root";
    private static String SYSTEMEMAIL = "test@icm.pl";

    private Dataverse testDataverse = createTestDataverse();

    @BeforeEach
    void prepare() {

        Dataverse rootDataverse = new Dataverse();
        rootDataverse.setName(ROOTDVNAME);

        Mockito.when(dataverseService.findRootDataverse()).thenReturn(rootDataverse);
        Mockito.when(dataverseService.find(createTestEmailNotificationDto().getDvObjectId())).thenReturn(testDataverse);
        Mockito.when(systemConfig.getDataverseSiteUrl()).thenReturn(SITEURL);
        Mockito.when(systemConfig.getGuidesBaseUrl()).thenReturn(GUIDESBASEURL);
        Mockito.when(systemConfig.getGuidesVersion()).thenReturn(GUIDESVERSION);

        mailMessageCreator = new MailMessageCreator(systemConfig, permissionService, dataverseService, confirmEmailService, genericDao);
    }

    @Test
    void createMailFooterMessage() {
        //given
        InternetAddress systemEmail = MailUtil.parseSystemAddress(SYSTEMEMAIL);
        String messageText = "Nice message";

        //when
        String footerMessage = mailMessageCreator.createMailFooterMessage(messageText, ROOTDVNAME, systemEmail);

        //then
        Assert.assertEquals(messageText + getFooterMessage(), footerMessage);

    }

    @Test
    void createRecipientName() {
    }

    @Test
    void createRecipients() {
    }

    @Test
    void getMessageAndSubject() {
        //given
        EmailNotificationDto testEmailNotificationDto = createTestEmailNotificationDto();

        //when
        Tuple2<String, String> messageAndSubject = mailMessageCreator.getMessageAndSubject(testEmailNotificationDto, Optional.empty(), "test@icm.pl");

        //then
        Assert.assertEquals(getCreateDataverseMessage(), messageAndSubject._1);
        Assert.assertEquals(getCreateDataverseSubject(), messageAndSubject._2);
    }

    private String getFooterMessage() {
        return "\n\nYou may contact us for support at " + SYSTEMEMAIL + ".\n\nThank you,\n" +
                BrandingUtil.getSupportTeamName(MailUtil.parseSystemAddress(SYSTEMEMAIL), ROOTDVNAME);
    }

    private String getCreateDataverseMessage() {
        return "Hello, \n" +
                "Your new dataverse named " + testDataverse.getDisplayName() + " (view at " + SITEURL + "/dataverse/" + testDataverse.getAlias()
                + " ) was created in  (view at  )." +
                " To learn more about what you can do with your dataverse, check out the Dataverse Management" +
                " - User Guide at " + GUIDESBASEURL + "/" + GUIDESVERSION + "/user/dataverse-management.html .";
    }

    private String getCreateDataverseSubject() {
        return "Root: Your dataverse has been created";
    }

    private Dataverse createTestDataverse() {
        Dataverse dataverse = new Dataverse();
        dataverse.setName("NICE DATAVERSE");
        dataverse.setAlias("nicedataverse");

        return dataverse;
    }

    private EmailNotificationDto createTestEmailNotificationDto() {
        return new EmailNotificationDto("useremail@test.com",
                                        NotificationType.CREATEDV,
                                        1L,
                                        NotificationObjectType.DATAVERSE,
                                        new AuthenticatedUser());
    }
}