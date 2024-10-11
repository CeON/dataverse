package edu.harvard.iq.dataverse.bannersandmessages.messages.dto;

import com.google.common.collect.ImmutableMap;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.dataverse.bannersandmessages.DataverseLocalizedMessage;
import edu.harvard.iq.dataverse.persistence.dataverse.bannersandmessages.DataverseTextMessage;
import edu.harvard.iq.dataverse.settings.SettingsWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static edu.harvard.iq.dataverse.common.DateUtil.convertToDate;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class DataverseMessagesMapperTest {

    private static final Date FROM_TIME = convertToDate(
            LocalDateTime.of(2018, 10, 1, 9, 15, 45));
    private static final Date TO_TIME = convertToDate(
            LocalDateTime.of(2018, 11, 2, 10, 25, 55));

    private DataverseMessagesMapper mapper;

    @Mock
    private SettingsWrapper settingsWrapper;


    @BeforeEach
    public void setUp() {
        mapper = new DataverseMessagesMapper(settingsWrapper);
    }

    @Test
    public void shouldConvertTextMessageToDto() {
        // given
        when(settingsWrapper.getConfiguredLocales()).thenReturn(ImmutableMap.of("en", "English", "pl", "Polski"));
        DataverseTextMessage textMessage = aTextMessage(1L);

        // when
        DataverseTextMessageDto dto = mapper.mapToDto(textMessage);

        // then
        verifySuccessfullDtoMapping(dto, 1L, true, FROM_TIME, TO_TIME, 2);
        verifyThatLocalesWasMapped(dto, "en", "English", "Info");
        verifyThatLocalesWasMapped(dto, "pl", "Polski", "Komunikat");
    }

    @Test
    public void shouldConvertTextMessageToDtoWithoutLocales() {
        // given
        DataverseTextMessage textMessage = aTextMessageWithoutLocales();

        // when
        DataverseTextMessageDto dto = mapper.mapToDto(textMessage);

        // then
        verifySuccessfullDtoMapping(dto, 1L, true, FROM_TIME, TO_TIME, 0);
    }

    @Test
    public void shouldConvertTextMessagesToDtos() {
        // given
        when(settingsWrapper.getConfiguredLocales()).thenReturn(ImmutableMap.of("en", "English", "pl", "Polski"));
        DataverseTextMessage textMessage = aTextMessage(1L);
        DataverseTextMessage anotherTextMessage = aTextMessage(2L);

        // when
        List<DataverseTextMessageDto> dtos = mapper.mapToDtos(asList(textMessage, anotherTextMessage));

        // then
        assertEquals(2, dtos.size());

        // and
        verifySuccessfullDtoMapping(dtos.get(0), 1L, true, FROM_TIME, TO_TIME, 2);
        verifyThatLocalesWasMapped(dtos.get(0), "en", "English", "Info");
        verifyThatLocalesWasMapped(dtos.get(0), "pl", "Polski", "Komunikat");

        // and
        verifySuccessfullDtoMapping(dtos.get(1), 2L, true, FROM_TIME, TO_TIME, 2);
        verifyThatLocalesWasMapped(dtos.get(1), "en", "English", "Info");
        verifyThatLocalesWasMapped(dtos.get(1), "pl", "Polski", "Komunikat");
    }

    @Test
    public void shouldMapDefaultLocales() {
        // when
        when(settingsWrapper.getConfiguredLocales()).thenReturn(ImmutableMap.of("en", "English", "pl", "Polski"));
        List<DataverseLocalizedMessageDto> localesDto = mapper.mapDefaultLocales();

        // then
        assertEquals(2, localesDto.size());
        assertTrue(localesDto.containsAll(asList(
                new DataverseLocalizedMessageDto("pl", "", "Polski"),
                new DataverseLocalizedMessageDto("en", "", "English"))
                   )
        );
    }

    private void verifyThatLocalesWasMapped(DataverseTextMessageDto dto, String locale, String language, String message) {
        assertTrue(dto.getDataverseLocalizedMessage().stream()
                           .anyMatch(lm -> lm.getLocale().equals(locale) &&
                                   lm.getLanguage().equals(language) &&
                                   lm.getMessage().equals(message)));
    }

    private void verifySuccessfullDtoMapping(DataverseTextMessageDto dto, Long id, boolean active,
                                             Date fromTime, Date toTime, int locales) {
        assertEquals(id, dto.getId());
        assertEquals(active, dto.isActive());
        assertEquals(fromTime, dto.getFromTime());
        assertEquals(toTime, dto.getToTime());
        assertEquals(new Long(100L), dto.getDataverseId());
        assertEquals(locales, dto.getDataverseLocalizedMessage().size());
    }

    private DataverseTextMessage aTextMessage(Long id) {
        DataverseTextMessage textMessage = new DataverseTextMessage();
        textMessage.setId(id);
        textMessage.setActive(true);
        textMessage.setFromTime(FROM_TIME);
        textMessage.setToTime(TO_TIME);
        Dataverse dataverse = new Dataverse();
        dataverse.setId(100L);
        textMessage.setDataverse(dataverse);

        Set<DataverseLocalizedMessage> localizedMessages = new HashSet<>();
        localizedMessages.add(aLocalizedMessage("pl", "Komunikat"));
        localizedMessages.add(aLocalizedMessage("en", "Info"));
        textMessage.setDataverseLocalizedMessages(localizedMessages);

        return textMessage;
    }

    private DataverseTextMessage aTextMessageWithoutLocales() {
        DataverseTextMessage textMessage = aTextMessage(1L);
        textMessage.setDataverseLocalizedMessages(null);
        return textMessage;
    }

    private DataverseLocalizedMessage aLocalizedMessage(String locale, String message) {
        DataverseLocalizedMessage localizedMessage = new DataverseLocalizedMessage();
        localizedMessage.setLocale(locale);
        localizedMessage.setMessage(message);
        return localizedMessage;
    }

}