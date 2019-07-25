package edu.harvard.iq.dataverse.thumbnail;

import edu.harvard.iq.dataverse.thumbnail.InputStreamWrapper;
import edu.harvard.iq.dataverse.thumbnail.Thumbnail;
import edu.harvard.iq.dataverse.thumbnail.ThumbnailGenerator;
import edu.harvard.iq.dataverse.thumbnail.ThumbnailGeneratorManager;
import edu.harvard.iq.dataverse.thumbnail.Thumbnail.ThumbnailSize;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author madryk
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ThumbnailGeneratorManagerTest {

    private ThumbnailGeneratorManager thumbnailGeneratorManager;
    
    @Mock
    private ThumbnailGenerator thumbnailGenerator1;
    
    @Mock
    private ThumbnailGenerator thumbnailGenerator2;
    
    @Mock
    Instance<ThumbnailGenerator> thumbnailGenerators;
    
    
    @BeforeEach
    public void before() {
        when(thumbnailGenerators.iterator()).thenReturn(IteratorUtils.arrayIterator(thumbnailGenerator1, thumbnailGenerator2));
        
        thumbnailGeneratorManager = new ThumbnailGeneratorManager(thumbnailGenerators);
    }
    
    // -------------------- TESTS --------------------
    
    @Test
    public void isSupported() {
        // given
        when(thumbnailGenerator1.isSupported("content/type1", 1024)).thenReturn(false);
        when(thumbnailGenerator2.isSupported("content/type1", 1024)).thenReturn(true);
        
        // when & then
        assertTrue(thumbnailGeneratorManager.isSupported("content/type1", 1024));
    }
    
    @Test
    public void isSupported__NO_SUPPORTED_GENERATOR() {
        // given
        when(thumbnailGenerator1.isSupported("content/type1", 1024)).thenReturn(false);
        when(thumbnailGenerator2.isSupported("content/type1", 1024)).thenReturn(false);
        
        // when & then
        assertFalse(thumbnailGeneratorManager.isSupported("content/type1", 1024));
    }
    
    @Test
    public void generateThumbnail() {
        // given
        when(thumbnailGenerator1.isSupported("content/type1", 1024)).thenReturn(false);
        when(thumbnailGenerator2.isSupported("content/type1", 1024)).thenReturn(true);
        
        InputStreamWrapper is = mock(InputStreamWrapper.class);
        when(is.getSize()).thenReturn(1024L);
        when(is.getContentType()).thenReturn("content/type1");
        
        Thumbnail thumbnail = mock(Thumbnail.class);
        
        when(thumbnailGenerator2.generateThumbnail(is, ThumbnailSize.CARD)).thenReturn(thumbnail);
        
        // when
        Thumbnail retThumbnail = thumbnailGeneratorManager.generateThumbnail(is, ThumbnailSize.CARD);
        
        // then
        assertSame(thumbnail, retThumbnail);
    }
    
    @Test
    public void generateThumbnail__NO_SUPPORTED_GENERATOR() {
        // given
        when(thumbnailGenerator1.isSupported(anyString(), anyLong())).thenReturn(false);
        when(thumbnailGenerator2.isSupported(anyString(), anyLong())).thenReturn(false);
        
        // when
        Executable generateThumbnailFun = () -> thumbnailGeneratorManager.generateThumbnail(mock(InputStreamWrapper.class), ThumbnailSize.PREVIEW);
        
        // then
        assertThrows(IllegalArgumentException.class, generateThumbnailFun);
    }
    
}
