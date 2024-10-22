package edu.harvard.iq.dataverse.branding;

import edu.harvard.iq.dataverse.common.BrandingUtil;
import edu.harvard.iq.dataverse.common.BundleUtil;
import org.junit.jupiter.api.Test;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BrandingUtilTest {

    @Test
    public void testGetInstallationBrandName() {
        System.out.println("testGetInstallationBrandName");
        assertEquals("LibraScholar", BrandingUtil.getInstallationBrandName("LibraScholar"));
        assertEquals(null, BrandingUtil.getInstallationBrandName(null));// misconfiguration to set to null
        assertEquals("", BrandingUtil.getInstallationBrandName(""));// misconfiguration to set to empty string
    }

    @Test
    public void testGetSupportTeamName() throws AddressException, UnsupportedEncodingException {
        System.out.println("testGetSupportTeamName");
        assertEquals("Support", BrandingUtil.getSupportTeamName(null, null));
        assertEquals("Support", BrandingUtil.getSupportTeamName(null, ""));
        assertEquals("LibraScholar Support", BrandingUtil.getSupportTeamName(null, "LibraScholar"));
        assertEquals("LibraScholar Support", BrandingUtil.getSupportTeamName(new InternetAddress("support@librascholar.edu"), "LibraScholar"));
        assertEquals("LibraScholar Support Team", BrandingUtil.getSupportTeamName(new InternetAddress("support@librascholar.edu", "LibraScholar Support Team"), "LibraScholar"));
        assertEquals("", BrandingUtil.getSupportTeamName(new InternetAddress("support@librascholar.edu", ""), "LibraScholar")); // misconfiguration to set to empty string
    }

    @Test
    public void testGetSupportEmailAddress() throws AddressException, UnsupportedEncodingException {
        System.out.println("testGetSupportEmailAddress");
        assertEquals(null, BrandingUtil.getSupportTeamEmailAddress(null));
        assertEquals("support@librascholar.edu", BrandingUtil.getSupportTeamEmailAddress(new InternetAddress("support@librascholar.edu")));
        assertEquals("support@librascholar.edu", BrandingUtil.getSupportTeamEmailAddress(new InternetAddress("support@librascholar.edu", "LibraScholar Support Team")));
        assertEquals("support@librascholar.edu", BrandingUtil.getSupportTeamEmailAddress(new InternetAddress("support@librascholar.edu", ""))); // misconfiguration to set to empty string but doesn't matter
        assertEquals(null, BrandingUtil.getSupportTeamEmailAddress(new InternetAddress(null, "LibraScholar Support Team"))); // misconfiguration to set to null
        assertEquals("", BrandingUtil.getSupportTeamEmailAddress(new InternetAddress("", "LibraScholar Support Team"))); // misconfiguration to set to empty string
    }

    @Test
    public void testWelcomeInAppNotification() {
        System.out.println("testWelcomeInAppNotification");
        String message = BundleUtil.getStringFromBundle("notification.welcome",
                                                                "LibraScholar",
                                                                "<a href=\"http://guides.dataverse.org/en/4.3/user/index.html\">User Guide</a>",
                                                                "<a href=\"https://demo.dataverse.org\">Demo Site</a>"
                                                        );
        System.out.println("message: " + message);
        assertEquals("Welcome to LibraScholar! Get started by adding or finding data. " +
                        "Have questions? Check out the <a href=\"http://guides.dataverse.org/en/4.3/user/index.html\">User Guide</a>. " +
                        "Also, check for your welcome email to verify your address.",
                     message);
    }

    @Test
    public void testWelcomeEmail() {
        System.out.println("testWelcomeEmail");
        String message = BundleUtil.getStringFromBundle("notification.email.welcome",
                                                                "LibraScholar",
                                                                "http://guides.librascholar.edu/en",
                                                                "4.3",
                                                                "LibraScholar Support",
                                                                "support@librascholar.edu"
                                                        );
        System.out.println("message: " + message);
        assertEquals("Welcome to LibraScholar! "
                             + "Have questions? Check out the User Guide at http://guides.librascholar.edu/en/4.3/user/index.html"
                             + " or ask for assistance by writing at support@librascholar.edu .",
                     message);
    }

    @Test
    public void testEmailClosing() {
        System.out.println("testEmailClosing");
        String message = BundleUtil.getStringFromBundle("notification.email.closing",
                                                                "support@librascholar.edu",
                                                                "LibraScholar Support Team"
                                                        );
        System.out.println("message: " + message);
        assertEquals("\n\nYou may contact us for support at support@librascholar.edu.\n\nThank you,\nLibraScholar Support Team",
                     message);
    }

    @Test
    public void testEmailSubject() {
        System.out.println("testEmailSubject");
        String message = BundleUtil.getStringFromBundle("notification.email.create.account.subject",
                                                                "LibraScholar"
                                                        );
        System.out.println("message: " + message);
        assertEquals("LibraScholar: Your account has been created",
                     message);
    }

    @Test
    public void testGetContactHeader() {
        System.out.println("testGetContactHeader");
        assertEquals("Contact the Repository's Support", BrandingUtil.getContactHeader());
    }

}
