package edu.harvard.iq.dataverse.dataverse.messages;

import edu.harvard.iq.dataverse.Dataverse;
import edu.harvard.iq.dataverse.dataverse.messages.dto.DataverseLocalizedMessageDto;
import edu.harvard.iq.dataverse.dataverse.messages.dto.DataverseMessagesMapper;
import edu.harvard.iq.dataverse.dataverse.messages.dto.DataverseTextMessageDto;
import org.junit.Before;
import org.junit.Test;

import javax.persistence.EntityManager;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DataverseTextMessageServiceBeanTest {

    private DataverseTextMessageServiceBean service;
    private EntityManager em;
    private DataverseMessagesMapper mapper;

    @Before
    public void setUp() {
        em = mock(EntityManager.class);
        mapper = new DataverseMessagesMapper();
        service = new DataverseTextMessageServiceBean(em, mapper);
    }

    @Test
    public void shouldReturnNewTextMessageDto() {
        // when
        DataverseTextMessageDto dto = service.newTextMessage(1L);

        // then
        verifyNewDto(dto);
    }

    @Test
    public void shouldReturnTextMessageDto() {
        // given
        DataverseTextMessage message = new DataverseTextMessage();
        message.setId(1L);
        Dataverse dataverse = new Dataverse();
        dataverse.setId(100L);
        message.setDataverse(dataverse);

        // and
        when(em.find(DataverseTextMessage.class, 1L)).thenReturn(message);

        // when
        DataverseTextMessageDto dto = service.getTextMessage(1L);

        // then
        assertEquals(new Long(1L), dto.getId());
        verify(em).find(DataverseTextMessage.class, 1L);
    }

    private void verifyDefaultLocales(DataverseTextMessageDto dto) {
        assertEquals(2, dto.getDataverseLocalizedMessage().size());
        verifyDefaultLocale(dto.getDataverseLocalizedMessage(), "en", "English");
        verifyDefaultLocale(dto.getDataverseLocalizedMessage(), "pl", "Polski");
    }

    private void verifyDefaultLocale(Set<DataverseLocalizedMessageDto> locales, String locale, String language) {
        assertTrue(locales.stream().anyMatch(lm ->
                    lm.getLocale().equals(locale) &&
                    lm.getLanguage().equals(language) &&
                    lm.getMessage() == null
                ));
    }

    private void verifyNewDto(DataverseTextMessageDto dto) {
        assertEquals(new Long(1L), dto.getDataverseId());
        assertNotNull(dto);
        assertNull(dto.getId());
        assertNull(dto.getFromTime());
        assertNull(dto.getToTime());
        assertFalse(dto.isActive());
        verifyDefaultLocales(dto);
    }
}