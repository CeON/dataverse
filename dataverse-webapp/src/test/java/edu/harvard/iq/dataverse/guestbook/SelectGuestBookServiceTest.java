package edu.harvard.iq.dataverse.guestbook;

import edu.harvard.iq.dataverse.dataset.datasetversion.DatasetVersionServiceBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SelectGuestBookServiceTest {

    @InjectMocks
    private SelectGuestBookService selectGuestBookService;

    @Mock
    private DatasetVersionServiceBean versionService;

    @Test
    public void saveGuestbookChanges() {
        //given

        //when


        //then

    }
}