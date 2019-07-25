package edu.harvard.iq.dataverse.thumbnail;

import com.google.common.base.Preconditions;
import edu.harvard.iq.dataverse.qualifiers.ProductionBean;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean.Key;
import edu.harvard.iq.dataverse.thumbnail.Thumbnail.ThumbnailSize;
import io.vavr.control.Try;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.PostConstruct;
import javax.ejb.Stateless;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.logging.Logger;

@Stateless
public class PdfThumbnailGenerator implements ThumbnailGenerator {

    private static final Logger logger = Logger.getLogger(PdfThumbnailGenerator.class.getCanonicalName());
    
    
    private SettingsServiceBean settingsService;
    
    private Runtime runtime = Runtime.getRuntime();
    
    private boolean imageMagicAvailable;
    private String imageMagickExec;
    private long sizeLimit;
    
    // -------------------- CONSTRUCTORS --------------------
    
    @Inject
    public PdfThumbnailGenerator(@ProductionBean SettingsServiceBean settingsService) {
        this.settingsService = settingsService;
    }
    
    @PostConstruct
    public void postConstruct() {
        imageMagickExec = settingsService.getValueForKey(Key.ImageMagickConvertBinPath);
        
        if (StringUtils.isNotBlank(imageMagickExec) && !new File(imageMagickExec).exists()) {
            imageMagicAvailable = false;
            imageMagickExec = StringUtils.EMPTY;
            logger.warning("ImageMagick is configured but executable could not be found. Pdf thumbnail generation will be disabled");
        } else {
            imageMagicAvailable = StringUtils.isNotBlank(imageMagickExec);
        }
        
        sizeLimit = settingsService.getValueForKeyAsLong(Key.ThumbnailPDFSizeLimit);
    }
    
    // -------------------- LOGIC --------------------
    
    @Override
    public boolean isSupported(String contentType, long fileSize) {
        if (!imageMagicAvailable) {
            return false;
        }
        if (!contentType.equalsIgnoreCase("application/pdf")) {
            return false;
        }
        return !isFileOverSizeLimit(fileSize);
    }


    @Override
    public Thumbnail generateThumbnail(InputStreamWrapper sourceInputStream, ThumbnailSize thumbnailSize) {
        Preconditions.checkArgument(isSupported(sourceInputStream.getContentType(), sourceInputStream.getSize()));
        
        logger.fine("entering generatePDFThumb");

        File sourceTempFile = null;
        File destTempFile = null;
        try {
            sourceTempFile = File.createTempFile("tempFileToRescale", ".tmp");
            destTempFile = File.createTempFile("tempFileRescaled", ".tmp");
            
            FileUtils.copyInputStreamToFile(sourceInputStream.getInputStream(), sourceTempFile);
            
            runImageMagick(sourceTempFile.getAbsolutePath(), destTempFile.getAbsolutePath(), thumbnailSize.getSize(), "pdf");
            
            byte[] thumbnailBytes = FileUtils.readFileToByteArray(destTempFile);
            return new Thumbnail(thumbnailBytes, thumbnailSize);
            
        } catch (IOException e) {
            throw new RuntimeException("Unable to generate thumbnail", e);
        } finally {
            if (sourceTempFile != null) {
                sourceTempFile.delete();
            }
            if (destTempFile != null) {
                destTempFile.delete();
            }
        }
    }

    private void runImageMagick(String fileLocation, String thumbFileLocation, int thumbSize, String format) {

        String imageMagickCmd = imageMagickExec + " pdf:" + fileLocation + "[0] -thumbnail " + thumbSize + "x" + thumbSize + " -flatten -strip png:" + thumbFileLocation;

        logger.fine("ImageMagick command line: " + imageMagickCmd);
        int exitValue = 1;
        Process process;
        
        try {
            process = runtime.exec(imageMagickCmd);
            exitValue = process.waitFor();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Error while trying to run ImageMagick command", e);
        }

        if (exitValue != 0 || !(new File(thumbFileLocation).exists())) {
            String imageMagickErrorResponse = Try.of(() -> IOUtils.toString(process.getErrorStream(), Charset.defaultCharset()))
                    .getOrElse(StringUtils.EMPTY);
            throw new RuntimeException("Thumbnail file not created: " + imageMagickErrorResponse);
        }
    }

    private boolean isFileOverSizeLimit(long fileSize) {
        
        if (sizeLimit < 0) {
            return true;
        }

        if (sizeLimit == 0) {
            return false;
        }

        // this is a broken file of size 0, or
        // this file is too large - no thumbnail:
        return fileSize == 0 || fileSize > sizeLimit;

    }
}
