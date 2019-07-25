package edu.harvard.iq.dataverse.thumbnail;

import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean.Key;
import edu.harvard.iq.dataverse.thumbnail.ImageThumbnailGenerator;
import edu.harvard.iq.dataverse.thumbnail.InputStreamWrapper;
import edu.harvard.iq.dataverse.thumbnail.Thumbnail;
import edu.harvard.iq.dataverse.thumbnail.Thumbnail.ThumbnailSize;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * @author madryk
 */
@ExtendWith(MockitoExtension.class)
public class ImageThumbnailGeneratorTest {

    @InjectMocks
    private ImageThumbnailGenerator thumbnailGenerator;

    @Mock
    private SettingsServiceBean settingsService;


    @BeforeEach
    public void before() throws IllegalAccessException {
        when(settingsService.getValueForKeyAsLong(Key.ThumbnailImageSizeLimit)).thenReturn(1024L*1024);
        thumbnailGenerator.postConstruct();
    }

    // -------------------- TESTS --------------------

    @Test
    public void isSupported__TRUE() {
        // when & then
        assertTrue(thumbnailGenerator.isSupported("image/png", 1024));
        assertTrue(thumbnailGenerator.isSupported("image/gif", 1024));
        assertTrue(thumbnailGenerator.isSupported("image/jpg", 1024));
    }

    @Test
    public void isSupported__WRONG_CONTENT_TYPE() {
        // when & then
        assertFalse(thumbnailGenerator.isSupported("image/fits", 10));
        assertFalse(thumbnailGenerator.isSupported("application/octet-stream", 10));
        assertFalse(thumbnailGenerator.isSupported("application/pdf", 10));
    }

    @Test
    public void isSupported__TOO_BIG_FILE() {
        // when & then
        assertFalse(thumbnailGenerator.isSupported("image/png", 1024*1024+1));
    }

    @Test
    public void isSupported__THUMBNAILS_DISABLED() {
        // given
        when(settingsService.getValueForKeyAsLong(Key.ThumbnailImageSizeLimit)).thenReturn(-1L);
        thumbnailGenerator.postConstruct();

        // when & then
        assertFalse(thumbnailGenerator.isSupported("application/pdf", 10));
    }


    @Test
    public void generateThumbnail__PREVIEW_SIZE() throws IOException, InterruptedException {
        // given
        byte[] sourceImageBytes = IOUtils.resourceToByteArray("/images/coffeeshop.png");
        InputStream is = new ByteArrayInputStream(sourceImageBytes);
        InputStreamWrapper isWithSize = new InputStreamWrapper(is, sourceImageBytes.length, "image/png");

        // when
        Thumbnail thumbnail = thumbnailGenerator.generateThumbnail(isWithSize, ThumbnailSize.PREVIEW);

        // then
        assertArrayEquals(IOUtils.resourceToByteArray("/images/coffeeshop_thumbnail_400.png"), thumbnail.getData());
        assertEquals(ThumbnailSize.PREVIEW, thumbnail.getSize());
    }


    @Test
    public void generateThumbnail__DEFAULT_SIZE() throws IOException, InterruptedException {
        // given
        byte[] sourceImageBytes = IOUtils.resourceToByteArray("/images/coffeeshop.png");
        InputStream is = new ByteArrayInputStream(sourceImageBytes);
        InputStreamWrapper isWithSize = new InputStreamWrapper(is, sourceImageBytes.length, "image/png");

        // when
        Thumbnail thumbnail = thumbnailGenerator.generateThumbnail(isWithSize, ThumbnailSize.DEFAULT);

        // then
        assertArrayEquals(IOUtils.resourceToByteArray("/images/coffeeshop_thumbnail_64.png"), thumbnail.getData());
        assertEquals(ThumbnailSize.DEFAULT, thumbnail.getSize());
    }


    @Test
    public void generateThumbnail__CARD_SIZE() throws IOException, InterruptedException {
        // given
        byte[] sourceImageBytes = IOUtils.resourceToByteArray("/images/coffeeshop.png");
        InputStream is = new ByteArrayInputStream(sourceImageBytes);
        InputStreamWrapper isWithSize = new InputStreamWrapper(is, sourceImageBytes.length, "image/png");

        // when
        Thumbnail thumbnail = thumbnailGenerator.generateThumbnail(isWithSize, ThumbnailSize.CARD);

        // then
        assertArrayEquals(IOUtils.resourceToByteArray("/images/coffeeshop_thumbnail_48.png"), thumbnail.getData());
        assertEquals(ThumbnailSize.CARD, thumbnail.getSize());
    }


    @Test
    public void generateThumbnail__INVALID_IMAGE() throws IOException, InterruptedException {
        // given
        byte[] sourceImageBytes = IOUtils.resourceToByteArray("/images/sample.txt");
        InputStream is = new ByteArrayInputStream(sourceImageBytes);
        InputStreamWrapper isWithSize = new InputStreamWrapper(is, sourceImageBytes.length, "image/png");

        // when
        Executable generateThumbnailFun = () -> thumbnailGenerator.generateThumbnail(isWithSize, ThumbnailSize.PREVIEW);

        // then
        assertThrows(RuntimeException.class, generateThumbnailFun);
    }

}
