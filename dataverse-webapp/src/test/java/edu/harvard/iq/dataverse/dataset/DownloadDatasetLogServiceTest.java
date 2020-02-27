package edu.harvard.iq.dataverse.dataset;

import edu.harvard.iq.dataverse.persistence.dataset.DownloadDatasetLog;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.persistence.EntityManager;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DownloadDatasetLogServiceTest {
    private static final long NON_EXISTING_ENTRY_ID = 1L;
    private static final long EXISTING_ENTRY_ID = 100L;
    private static final int COUNT = 1;

    @Mock
    DownloadDatasetLog testLog;

    @Mock
    EntityManager em;

    @InjectMocks
    DownloadDatasetLogService downloadDatasetLogService;

    @Before
    public void setUp() {
        when(testLog.getCount()).thenReturn(COUNT);
        when(em.find(DownloadDatasetLog.class, EXISTING_ENTRY_ID)).thenReturn(testLog);
        when(em.find(DownloadDatasetLog.class, NON_EXISTING_ENTRY_ID)).thenReturn(null);
    }

    @Test
    public void fetchDownloadCountForDataset_whenNoEntryExistsYet() {
        // when
        int count = downloadDatasetLogService.fetchDownloadCountForDataset(NON_EXISTING_ENTRY_ID);

        // then
        assertThat(count, is(0));
    }

    @Test
    public void fetchDownloadCountForDataset_whenEntryExists() {
        // when
        int count = downloadDatasetLogService.fetchDownloadCountForDataset(EXISTING_ENTRY_ID);

        // then
        assertThat(count, is(COUNT));
    }

    @Test
    public void incrementDownloadCountForDataset_whenNoEntryExistsYet() {
        // when
        downloadDatasetLogService.incrementDownloadCountForDataset(NON_EXISTING_ENTRY_ID);

        // then
        verify(em, times(1)).persist(any(DownloadDatasetLog.class));
    }

    @Test
    public void incrementDownloadCountForDataset_whenEntryExists() {
        // when
        downloadDatasetLogService.incrementDownloadCountForDataset(EXISTING_ENTRY_ID);

        // then
        verify(testLog, times(1)).setCount(eq(COUNT + 1));
    }
}