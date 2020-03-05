package edu.harvard.iq.dataverse.consent.action;

import com.amazonaws.thirdparty.jackson.databind.ObjectMapper;
import com.amazonaws.util.json.Jackson;
import edu.harvard.iq.dataverse.consent.ConsentActionDto;
import edu.harvard.iq.dataverse.consent.ConsentDetailsDto;
import edu.harvard.iq.dataverse.mail.EmailContent;
import edu.harvard.iq.dataverse.mail.MailService;
import io.vavr.control.Try;

public class SendNewsletterEmailAction implements Action{

    private MailService mailService;
    private String repositoryName;

    // -------------------- CONSTRUCTORS --------------------

    public SendNewsletterEmailAction(MailService mailService, String repositoryName) {
        this.mailService = mailService;
        this.repositoryName = repositoryName;
    }

    // -------------------- LOGIC --------------------

    public void executeAction(ConsentActionDto consentActionDto){
        SendNewsletterEmailContent sendNewsletterEmailContent = parseEmailActionContent(consentActionDto);

        EmailContent emailContent = prepareEmail(repositoryName, consentActionDto, sendNewsletterEmailContent);

        mailService.sendMailAsync(sendNewsletterEmailContent.getEmail(), emailContent);
    }

    // -------------------- PRIVATE --------------------

    private SendNewsletterEmailContent parseEmailActionContent(ConsentActionDto consentActionDto) {
        ObjectMapper objectMapper = Jackson.getObjectMapper();
        return Try.of(() -> objectMapper.readValue(consentActionDto.getActionOptions(),
                                                                                      SendNewsletterEmailContent.class))
                .getOrElseThrow(throwable -> new RuntimeException(
                        "There was a problem with parsing consent action with id: " + consentActionDto.getId(),
                        throwable));
    }

    private EmailContent prepareEmail(String repositoryName, ConsentActionDto consentActionDto, SendNewsletterEmailContent actionContent){
        String emailSubject = repositoryName + " New consent for personal data processing";

        StringBuilder emailBodyConstructor = new StringBuilder();

        ConsentDetailsDto consentDetails = consentActionDto.getOwner().getConsentDetails();
        String consentText = consentDetails.getText();

        String emailBody = emailBodyConstructor.append("Consent text: ")
                .append(consentText)
                .append("\n\n")
                .append("First name: ")
                .append(actionContent.getFirstName())
                .append("\n")
                .append("Last name: ")
                .append(actionContent.getLastName())
                .append("\n")
                .append("E-mail: ")
                .append(actionContent.getEmail())
                .toString();

        return new EmailContent(emailSubject, emailBody, "");
    }
}
