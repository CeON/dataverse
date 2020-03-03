package edu.harvard.iq.dataverse.consent;

import com.google.common.collect.Lists;
import edu.harvard.iq.dataverse.persistence.consent.AcceptedConsent;
import edu.harvard.iq.dataverse.persistence.consent.Consent;
import edu.harvard.iq.dataverse.persistence.consent.ConsentDetails;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

class ConsentMapperTest {

    private ConsentMapper consentMapper = new ConsentMapper();

    private static final Locale PREFERRED_LOCALE = Locale.CHINA;

    @Test
    public void consentToConsentDto() {
        //given
        List<Consent> consents = prepareTestConsents();

        //when
        List<ConsentDto> preperedConsents = consents.stream()
                .map(consent -> consentMapper.consentToConsentDto(consent, Locale.CHINA))
                .collect(Collectors.toList());

        //then
        Assertions.assertAll(() -> Assertions.assertEquals(consents.get(0).getName(),
                                                           preperedConsents.get(0).getName()),
                             () -> Assertions.assertEquals(consents.get(0).getConsentDetails().get(0).getText(),
                                                           preperedConsents.get(0).getConsentDetails().getText()),
                             () -> Assertions.assertEquals(consents.get(1).getName(),
                                                           preperedConsents.get(1).getName()),
                             () -> Assertions.assertEquals(consents.get(1).getConsentDetails().get(1).getText(),
                                                           preperedConsents.get(1).getConsentDetails().getText()),
                             () -> Assertions.assertEquals(consents.get(2).getName(),
                                                           preperedConsents.get(2).getName()),
                             () -> Assertions.assertEquals(consents.get(2).getConsentDetails().get(0).getText(),
                                                           preperedConsents.get(2).getConsentDetails().getText()));

    }

    @Test
    public void consentDtoToAcceptedConsent() {
        //given
        List<ConsentDto> consentDtos = prepareTestDtoConsents();
        AuthenticatedUser authenticatedUser = new AuthenticatedUser();

        //when
        List<AcceptedConsent> acceptedConsents = consentDtos.stream()
                .map(consentDto -> consentMapper.consentDtoToAcceptedConsent(consentDto, authenticatedUser))
                .collect(Collectors.toList());

        //then
        Assertions.assertAll(() -> Assertions.assertEquals(consentDtos.get(0).getName(), acceptedConsents.get(0).getName()),
                             () -> Assertions.assertEquals(consentDtos.get(0).getConsentDetails().getText(), acceptedConsents.get(0).getText()),
                             () -> Assertions.assertEquals(authenticatedUser.getAcceptedConsents().get(0).getName(), consentDtos.get(0).getName()),
                             () -> Assertions.assertEquals(consentDtos.get(1).getName(), acceptedConsents.get(1).getName()),
                             () -> Assertions.assertEquals(consentDtos.get(1).getConsentDetails().getText(), acceptedConsents.get(1).getText()),
                             () -> Assertions.assertEquals(authenticatedUser.getAcceptedConsents().get(1).getName(), consentDtos.get(1).getName()));

    }

    // -------------------- PRIVATE --------------------

    private List<ConsentDto> prepareTestDtoConsents() {
        ConsentDetailsDto englishCons = new ConsentDetailsDto(1L, Locale.ENGLISH, "english cons");
        ConsentDto firstConsent = new ConsentDto(1L, "first consent", englishCons, Lists.newArrayList(), 0, true);
        firstConsent.getConsentDetails().setAccepted(true);

        ConsentDetailsDto secondEnglishCons = new ConsentDetailsDto(1L, Locale.ENGLISH, "second english cons");
        ConsentDto secondConsent = new ConsentDto(2L,
                                                  "second consent",
                                                  secondEnglishCons,
                                                  Lists.newArrayList(),
                                                  1,
                                                  true);

        return Lists.newArrayList(firstConsent, secondConsent);
    }

    private List<Consent> prepareTestConsents() {
        Consent requiredEnglish = new Consent("requiredEnglish", 1, true, false);
        requiredEnglish.setId(1L);
        ConsentDetails consentDetails = new ConsentDetails(requiredEnglish, Locale.ENGLISH, "required consent");
        consentDetails.setId(1L);
        requiredEnglish.getConsentDetails().add(consentDetails);

        Consent requiredPolEng = new Consent("requiredPolEng", 2, true, false);
        requiredPolEng.setId(2L);
        ConsentDetails reqPolEngDetails = new ConsentDetails(requiredPolEng, Locale.ENGLISH, "required consent");
        reqPolEngDetails.setId(2L);
        ConsentDetails reqPolEngDetails2 = new ConsentDetails(requiredPolEng, PREFERRED_LOCALE, "wymagana zgoda");
        reqPolEngDetails2.setId(3L);
        requiredPolEng.getConsentDetails().add(reqPolEngDetails);
        requiredPolEng.getConsentDetails().add(reqPolEngDetails2);

        Consent nonRequiredEnglish = new Consent("requiredEnglish", 0, false, false);
        nonRequiredEnglish.setId(3L);
        ConsentDetails nonReqEnglish = new ConsentDetails(nonRequiredEnglish, Locale.ENGLISH, "non required consent");
        nonReqEnglish.setId(4L);
        nonRequiredEnglish.getConsentDetails().add(nonReqEnglish);

        return Lists.newArrayList(requiredEnglish, requiredPolEng, nonRequiredEnglish);
    }
}