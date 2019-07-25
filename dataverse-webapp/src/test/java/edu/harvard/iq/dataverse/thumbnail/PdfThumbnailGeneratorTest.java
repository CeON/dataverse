package edu.harvard.iq.dataverse.thumbnail;

import com.google.common.io.Files;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean.Key;
import edu.harvard.iq.dataverse.thumbnail.InputStreamWrapper;
import edu.harvard.iq.dataverse.thumbnail.PdfThumbnailGenerator;
import edu.harvard.iq.dataverse.thumbnail.Thumbnail;
import edu.harvard.iq.dataverse.thumbnail.Thumbnail.ThumbnailSize;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author madryk
 */
@ExtendWith(MockitoExtension.class)
public class PdfThumbnailGeneratorTest {

    @InjectMocks
    private PdfThumbnailGenerator thumbnailGenerator;

    @Mock
    private SettingsServiceBean settingsService;

    @Mock
    private Runtime runtime;

    @Captor
    private ArgumentCaptor<String> commandNameCaptor;

    private File fakeImageMagickFile;


    @BeforeEach
    public void before() throws IllegalAccessException {
        fakeImageMagickFile = Files.createTempDir();
        FieldUtils.writeField(thumbnailGenerator, "runtime", runtime, true);

        when(settingsService.getValueForKey(Key.ImageMagickConvertBinPath)).thenReturn(fakeImageMagickFile.getAbsolutePath());
        when(settingsService.getValueForKeyAsLong(Key.ThumbnailPDFSizeLimit)).thenReturn(1024L);
        thumbnailGenerator.postConstruct();
    }

    @AfterEach
    public void after() {
        if (fakeImageMagickFile != null) {
            fakeImageMagickFile.delete();
        }
    }

    // -------------------- TESTS --------------------

    @Test
    public void isSupported__TRUE() {
        // when & then
        assertTrue(thumbnailGenerator.isSupported("application/pdf", 1024));
    }

    @Test
    public void isSupported__WRONG_CONTENT_TYPE() {
        // when & then
        assertFalse(thumbnailGenerator.isSupported("image/png", 10));
        assertFalse(thumbnailGenerator.isSupported("application/octet-stream", 10));
    }

    @Test
    public void isSupported__TOO_BIG_FILE() {
        // when & then
        assertFalse(thumbnailGenerator.isSupported("application/pdf", 1025));
    }

    @Test
    public void isSupported__THUMBNAILS_DISABLED() {
        // given
        when(settingsService.getValueForKeyAsLong(Key.ThumbnailPDFSizeLimit)).thenReturn(-1L);
        thumbnailGenerator.postConstruct();

        // when & then
        assertFalse(thumbnailGenerator.isSupported("application/pdf", 10));
    }

    @Test
    public void isSupported__NO_CONFIGURED_IMAGE_MAGICK() {
        // given
        when(settingsService.getValueForKey(Key.ImageMagickConvertBinPath)).thenReturn(StringUtils.EMPTY);
        thumbnailGenerator.postConstruct();

        // when & then
        assertFalse(thumbnailGenerator.isSupported("application/pdf", 10));
    }

    @Test
    public void isSupported__IMAGE_MAGICK_NOT_FOUND() {
        // given
        when(settingsService.getValueForKey(Key.ImageMagickConvertBinPath)).thenReturn("/not/existing/path");
        thumbnailGenerator.postConstruct();

        // when & then
        assertFalse(thumbnailGenerator.isSupported("application/pdf", 10));
    }


    @Test
    public void generateThumbnail() throws IOException, InterruptedException {
        // given
        Process process = mock(Process.class);
        when(process.waitFor()).thenReturn(0);

        when(runtime.exec(anyString())).thenAnswer(answerImageMagick(process));

        byte[] sourceFileBytes = "source_data".getBytes();
        InputStream is = new ByteArrayInputStream(sourceFileBytes);
        InputStreamWrapper isWithSize = new InputStreamWrapper(is, sourceFileBytes.length, "application/pdf");

        // when
        Thumbnail thumbnail = thumbnailGenerator.generateThumbnail(isWithSize, ThumbnailSize.PREVIEW);

        // then
        verify(runtime).exec(commandNameCaptor.capture());
        assertImageMagickCommand(commandNameCaptor.getValue());


        assertArrayEquals("thumbnail_data".getBytes(), thumbnail.getData());
        assertEquals(ThumbnailSize.PREVIEW, thumbnail.getSize());
    }

    @Test
    public void generateThumbnail__NOT_SUPPORTED() {
        // given
        byte[] sourceFileBytes = "some_bytes".getBytes();
        InputStream is = new ByteArrayInputStream(sourceFileBytes);
        InputStreamWrapper isWithSize = new InputStreamWrapper(is, sourceFileBytes.length, "image/gif");

        // when
        Executable generateThumbnailFun = () -> thumbnailGenerator.generateThumbnail(isWithSize, ThumbnailSize.PREVIEW);

        // then
        assertThrows(IllegalArgumentException.class, generateThumbnailFun);
    }

    @Test
    public void generateThumbnail__IMAGE_MAGICK_ERROR() throws InterruptedException, IOException {
        // given
        Process process = mock(Process.class);
        when(process.waitFor()).thenReturn(1);
        when(process.getErrorStream()).thenReturn(new ByteArrayInputStream("image magick error message".getBytes()));

        when(runtime.exec(anyString())).thenReturn(process);

        byte[] sourceFileBytes = "some_bytes".getBytes();
        InputStream is = new ByteArrayInputStream(sourceFileBytes);
        InputStreamWrapper isWithSize = new InputStreamWrapper(is, sourceFileBytes.length, "application/pdf");

        // when
        Executable generateThumbnailFun = () -> thumbnailGenerator.generateThumbnail(isWithSize, ThumbnailSize.PREVIEW);

        // then
        RuntimeException throwedException = assertThrows(RuntimeException.class, generateThumbnailFun);
        assertEquals("Thumbnail file not created: image magick error message", throwedException.getMessage());
    }

    // -------------------- PRIVATE --------------------

    private void assertImageMagickCommand(String actualCommand) {
        String[] commandParts = StringUtils.split(actualCommand);

        assertThat(commandParts, arrayWithSize(7));
        assertThat(commandParts[0], is(fakeImageMagickFile.getAbsolutePath()));
        assertThat(commandParts[1], allOf(startsWith("pdf:"), endsWith("[0]")));
        assertThat(commandParts[2], is("-thumbnail"));
        assertThat(commandParts[3], is("400x400"));
        assertThat(commandParts[4], is("-flatten"));
        assertThat(commandParts[5], is("-strip"));
        assertThat(commandParts[6], startsWith("png:"));

    }

    private String extractThumbDestination(String command) {
        int thumbDestIndex = StringUtils.indexOf(command, " png:") + " png:".length();
        return StringUtils.substring(command, thumbDestIndex);
    }

    private Answer<Process> answerImageMagick(Process process) {
        return (invocation) -> {
            String thumbDest = extractThumbDestination(invocation.getArgument(0));
            FileUtils.writeByteArrayToFile(new File(thumbDest), "thumbnail_data".getBytes());
            return process;
        };
    }
}
