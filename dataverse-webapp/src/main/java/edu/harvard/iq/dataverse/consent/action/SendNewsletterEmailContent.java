package edu.harvard.iq.dataverse.consent.action;

public class SendNewsletterEmailContent {

    private String firstName;
    private String lastName;
    private String newsletterSenderEmail;
    private String newsletterReceiverEmail;

    // -------------------- CONSTRUCTORS --------------------
    @Deprecated /* Only used for Jackson parser */
    public SendNewsletterEmailContent() {
    }

    public SendNewsletterEmailContent(String firstName, String lastName, String newsletterSenderEmail, String newsletterReceiverEmail) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.newsletterSenderEmail = newsletterSenderEmail;
        this.newsletterReceiverEmail = newsletterReceiverEmail;
    }

    // -------------------- GETTERS --------------------

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getNewsletterSenderEmail() {
        return newsletterSenderEmail;
    }

    public String getNewsletterReceiverEmail() {
        return newsletterReceiverEmail;
    }
}
