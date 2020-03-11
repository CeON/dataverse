package edu.harvard.iq.dataverse.consent.api;

import com.google.common.collect.Lists;
import edu.harvard.iq.dataverse.persistence.consent.Consent;
import edu.harvard.iq.dataverse.persistence.consent.ConsentAction;
import edu.harvard.iq.dataverse.persistence.consent.ConsentActionType;
import edu.harvard.iq.dataverse.persistence.consent.ConsentDetails;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConsentValidatorTest {

    private ConsentValidator consentValidator = new ConsentValidator();

    // -------------------- TESTS --------------------

    @Test
    public void validateConsentEditing_WithCorrectEditedConsent() {
        //given
        ConsentApiDto consentApiDto = prepareTestConsentApiDto();
        Consent consent = prepareTestConsent();

        //when
        List<String> errors = consentValidator.validateConsentEditing(consentApiDto, consent);

        //then
        Assert.assertTrue(errors.isEmpty());
    }

    @Test
    public void validateConsentEditing_WithIncorrectlyEditedConsent() {
        //given
        ConsentApiDto consentApiDto = prepareTestConsentApiDtoWithInvalidProperties();
        Consent consent = prepareTestConsent();

        //when
        List<String> errors = consentValidator.validateConsentEditing(consentApiDto, consent);

        //then
        Assert.assertFalse(errors.isEmpty());
        Assertions.assertAll(() -> assertEquals("Consent names must be equal", errors.get(0)),
                             () -> assertEquals("Consent display order must be equal", errors.get(1)),
                             () -> assertEquals("Consent details cannot be edited!", errors.get(2)),
                             () -> assertEquals("New consent detail has duplicated language", errors.get(3)),
                             () -> assertEquals("New consent detail text cannot be empty", errors.get(4)),
                             () -> assertEquals("Action options were not correctly filled out for: SEND_NEWSLETTER_EMAIL", errors.get(5)));
    }

    // -------------------- PRIVATE --------------------

    private ConsentApiDto prepareTestConsentApiDto() {
        ConsentApiDto cons = new ConsentApiDto(1L,
                                               "testName",
                                               1,
                                               true,
                                               false,
                                               Lists.newArrayList(),
                                               Lists.newArrayList());

        ConsentDetailsApiDto consDetails = new ConsentDetailsApiDto(1L, Locale.ENGLISH, "testCons");
        ConsentActionApiDto consAction = new ConsentActionApiDto(1L,
                                                                          ConsentActionType.SEND_NEWSLETTER_EMAIL,
                                                                          "{\"email\":\"test@gmail.com\"}");

        cons.getConsentDetails().add(consDetails);
        cons.getConsentActions().add(consAction);

        return cons;
    }

    private ConsentApiDto prepareTestConsentApiDtoWithInvalidProperties() {
        ConsentApiDto cons = new ConsentApiDto(1L,
                                               "invalidName",
                                               2,
                                               true,
                                               false,
                                               Lists.newArrayList(),
                                               Lists.newArrayList());

        ConsentDetailsApiDto consDetails = new ConsentDetailsApiDto(1L, Locale.ENGLISH, "");
        ConsentDetailsApiDto consDetails2 = new ConsentDetailsApiDto(null, Locale.ENGLISH, "");
        ConsentActionApiDto consAction = new ConsentActionApiDto(1L,
                                                                          ConsentActionType.SEND_NEWSLETTER_EMAIL,
                                                                          "");

        cons.getConsentDetails().add(consDetails);
        cons.getConsentDetails().add(consDetails2);
        cons.getConsentActions().add(consAction);

        return cons;
    }

    private Consent prepareTestConsent() {
        Consent cons = new Consent("testName", 1, true, false);
        cons.setId(1L);
        ConsentDetails consDetails = new ConsentDetails(cons, Locale.ENGLISH, "testCons");
        consDetails.setId(1L);
        ConsentAction consAction = new ConsentAction(cons,
                                                     ConsentActionType.SEND_NEWSLETTER_EMAIL,
                                                     "{\"email\":\"test@gmail.com\"}");
        consAction.setId(1L);

        cons.getConsentDetails().add(consDetails);
        cons.getConsentActions().add(consAction);

        return cons;
    }
}