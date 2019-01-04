package edu.harvard.iq.dataverse.dataverse.messages.dto;

import edu.harvard.iq.dataverse.dataverse.messages.DataverseLocalizedMessage;
import edu.harvard.iq.dataverse.dataverse.messages.DataverseTextMessage;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DataverseMessagesMapperTest {

    private static final LocalDateTime FROM_TIME =
            LocalDateTime.of(2018, 10, 1, 9, 15, 45);
    private static final LocalDateTime TO_TIME =
            LocalDateTime.of(2018, 11, 2, 10, 25, 55);

    private DataverseMessagesMapper mapper;

    @Before
    public void setUp() {
        mapper = new DataverseMessagesMapper();
    }

    @Test
    public void shouldConvertTextMessageToDto() {
        // given
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
        Set<DataverseLocalizedMessageDto> localesDto = mapper.mapDefaultLocales();

        // then
        assertEquals(2, localesDto.size());
        assertTrue(localesDto.containsAll(asList(
                    new DataverseLocalizedMessageDto("pl", null, "Polski"),
                    new DataverseLocalizedMessageDto("en", null, "English"))
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
                                             LocalDateTime fromTime, LocalDateTime toTime, int locales) {
        assertEquals(id, dto.getId());
        assertEquals(active, dto.isActive());
        assertEquals(fromTime, dto.getFromTime());
        assertEquals(toTime, dto.getToTime());
        assertEquals(locales, dto.getDataverseLocalizedMessage().size());
    }

    private DataverseTextMessage aTextMessage(Long id) {
        DataverseTextMessage textMessage = new DataverseTextMessage();
        textMessage.setId(id);
        textMessage.setActive(true);
        textMessage.setFromTime(FROM_TIME);
        textMessage.setToTime(TO_TIME);

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