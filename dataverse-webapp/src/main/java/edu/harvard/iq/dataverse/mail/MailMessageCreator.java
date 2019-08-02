package edu.harvard.iq.dataverse.mail;

import edu.harvard.iq.dataverse.branding.BrandingUtil;
import edu.harvard.iq.dataverse.util.BundleUtil;
import org.simplejavamail.email.Recipient;

import javax.ejb.Stateless;
import javax.mail.Message;
import javax.mail.internet.InternetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Stateless
class MailMessageCreator {

    String createMailBodyMessage(String messageText, String rootDataverseName, InternetAddress systemAddress) {

        return messageText + BundleUtil.getStringFromBundle("notification.email.closing",
                                                            Arrays.asList(BrandingUtil.getSupportTeamEmailAddress(systemAddress),
                                                                          BrandingUtil.getSupportTeamName(systemAddress, rootDataverseName)));
    }

    String createRecipientName(String reply, InternetAddress systemAddress) {
        return BundleUtil.getStringFromBundle("contact.delegation", Arrays.asList(
                systemAddress.getPersonal(), reply));
    }

    List<Recipient> createRecipients(String to, String recipientName) {
        return Arrays.stream(to.split(","))
                .map(recipient -> new Recipient(recipientName, recipient, Message.RecipientType.TO))
                .collect(Collectors.toList());
    }

}
