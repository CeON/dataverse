package edu.harvard.iq.dataverse.consent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsentServiceTest {

    @Mock
    private ConsentDao consentDao;

    @InjectMocks
    private ConsentService consentService;

    @Test
    public void prepareConsentsForView() {
        //given

        //when

        //then

    }
}