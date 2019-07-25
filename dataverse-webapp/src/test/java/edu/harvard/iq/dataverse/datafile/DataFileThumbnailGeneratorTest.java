package edu.harvard.iq.dataverse.datafile;

import edu.harvard.iq.dataverse.DataFile;
import edu.harvard.iq.dataverse.Dataset;
import edu.harvard.iq.dataverse.dataset.ThumbnailSourceFileProvider;
import edu.harvard.iq.dataverse.harvest.client.HarvestingClient;
import edu.harvard.iq.dataverse.mocks.MocksFactory;
import edu.harvard.iq.dataverse.thumbnail.InputStreamWrapper;
import edu.harvard.iq.dataverse.thumbnail.Thumbnail;
import edu.harvard.iq.dataverse.thumbnail.Thumbnail.ThumbnailSize;
import edu.harvard.iq.dataverse.thumbnail.ThumbnailGeneratorManager;
import org.apache.commons.collections4.IteratorUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.enterprise.inject.Instance;

import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author madryk
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class DataFileThumbnailGeneratorTest {

    private DataFileThumbnailGenerator dataFileThumbnailGenerator;
    
    @Mock
    private ThumbnailSourceFileProvider sourceFileProvider1;
    
    @Mock
    private ThumbnailSourceFileProvider sourceFileProvider2;
    
    @Mock
    Instance<ThumbnailSourceFileProvider> thumbnailSourceFileProviders;
    
    @Mock
    private ThumbnailGeneratorManager thumbnailGeneratorManager;
    
    
    @BeforeEach
    public void before() {
        when(thumbnailSourceFileProviders.iterator()).thenReturn(IteratorUtils.arrayIterator(sourceFileProvider1, sourceFileProvider2));
        
        dataFileThumbnailGenerator = new DataFileThumbnailGenerator(thumbnailSourceFileProviders, thumbnailGeneratorManager);
    }
    
    // -------------------- TESTS --------------------
    
    @Test
    public void isSupported() {
        // given
        DataFile dataFile = MocksFactory.makeDataFile();
        dataFile.setStorageIdentifier("1515");
        when(sourceFileProvider1.isApplicable(dataFile)).thenReturn(false);
        when(sourceFileProvider2.isApplicable(dataFile)).thenReturn(true);
        
        // when & then
        assertTrue(dataFileThumbnailGenerator.isSupported(dataFile));
    }
    
    @Test
    public void isSupported__NO_SUPPORTED_FILE_PROVIDER() {
        // given
        DataFile dataFile = MocksFactory.makeDataFile();
        dataFile.setStorageIdentifier("1515");
        when(sourceFileProvider1.isApplicable(dataFile)).thenReturn(false);
        when(sourceFileProvider2.isApplicable(dataFile)).thenReturn(false);
        
        // when & then
        assertFalse(dataFileThumbnailGenerator.isSupported(dataFile));
    }
    
    @Test
    public void isSupported__NO_FOR_HARVESTED() {
        // given
        Dataset dataset = MocksFactory.makeDataset();
        dataset.setHarvestedFrom(new HarvestingClient());
        
        DataFile dataFile = MocksFactory.makeDataFile();
        dataFile.setOwner(dataset);
        dataFile.setStorageIdentifier("");
        
        // when & then
        assertFalse(dataFileThumbnailGenerator.isSupported(dataFile));
    }
    
    @Test
    public void generateThumbnailAllSizes() {
        // given
        DataFile dataFile = MocksFactory.makeDataFile();
        dataFile.setStorageIdentifier("1515");
        when(sourceFileProvider1.isApplicable(dataFile)).thenReturn(false);
        when(sourceFileProvider2.isApplicable(dataFile)).thenReturn(true);
        
        InputStreamWrapper is = mock(InputStreamWrapper.class);
        
        Thumbnail thumbnailCard = mock(Thumbnail.class);
        Thumbnail thumbnailDefault = mock(Thumbnail.class);
        Thumbnail thumbnailPreview = mock(Thumbnail.class);
        
        when(sourceFileProvider2.obtainThumbnailSourceFile(dataFile)).thenReturn(is);
        when(thumbnailGeneratorManager.generateThumbnail(is, ThumbnailSize.CARD)).thenReturn(thumbnailCard);
        when(thumbnailGeneratorManager.generateThumbnail(is, ThumbnailSize.DEFAULT)).thenReturn(thumbnailDefault);
        when(thumbnailGeneratorManager.generateThumbnail(is, ThumbnailSize.PREVIEW)).thenReturn(thumbnailPreview);
        
        // when
        List<Thumbnail> retThumbnails = dataFileThumbnailGenerator.generateThumbnailAllSizes(dataFile);
        
        // then
        assertThat(retThumbnails, contains(thumbnailPreview, thumbnailDefault, thumbnailCard));
    }
    
    @Test
    public void generateThumbnailAllSizes__NOT_SUPPORTED() {
        // given
        DataFile dataFile = MocksFactory.makeDataFile();
        dataFile.setStorageIdentifier("1515");
        when(sourceFileProvider1.isApplicable(dataFile)).thenReturn(false);
        when(sourceFileProvider2.isApplicable(dataFile)).thenReturn(false);
        
        // when
        Executable generateThumbnailFun = () -> dataFileThumbnailGenerator.generateThumbnailAllSizes(dataFile);
        
        // then
        assertThrows(IllegalArgumentException.class, generateThumbnailFun);
    }
}
