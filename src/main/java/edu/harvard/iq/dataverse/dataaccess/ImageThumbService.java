package edu.harvard.iq.dataverse.dataaccess;

import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import org.apache.commons.io.IOUtils;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.logging.Logger;

@Stateless
public class ImageThumbService {

    private int DEFAULT_PREVIEW_SIZE = 400;
    private String THUMBNAIL_SUFFIX = "thumb";

    private static final Logger logger = Logger.getLogger(ImageThumbService.class.getCanonicalName());

    @EJB
    private SettingsServiceBean settingsService;

    public String generatePDFThumbnailFromFile(String fileLocation, int size) {
        logger.fine("entering generatePDFThumb");

        String thumbFileLocation = fileLocation + ".thumb" + size;

        // see if the thumb is already generated and saved:
        if (new File(thumbFileLocation).exists()) {
            return thumbFileLocation;
        }

        // it it doesn't exist yet, let's attempt to generate it:
        long sizeLimit = getThumbnailSizeLimitPDF();

        /*
         * sizeLimit set to -1 means that generation of thumbnails on the fly
         * is disabled:
         */
        logger.fine("pdf size limit: " + sizeLimit);

        if (sizeLimit < 0) {
            logger.fine("returning null!");
            return null;
        }

        /*
         * sizeLimit set to 0 means no limit - generate thumbnails on the fly
         * for all files, regardless of size.
         */
        if (sizeLimit > 0) {
            long fileSize = 0;

            try {
                fileSize = new File(fileLocation).length();
            } catch (Exception ex) {
                //
            }

            if (fileSize == 0 || fileSize > sizeLimit) {
                logger.fine("file size: " + fileSize + ", skipping.");
                // this file is too large, exiting.
                return null;
            }
        }

        String imageMagickExec = System.getProperty("dataverse.path.imagemagick.convert");

        if (imageMagickExec != null) {
            imageMagickExec = imageMagickExec.trim();
        }

        // default location:
        if (imageMagickExec == null || imageMagickExec.equals("")) {
            imageMagickExec = "/usr/bin/convert";
        }

        if (new File(imageMagickExec).exists()) {
            String previewFileLocation;

            previewFileLocation = fileLocation + ".thumb" + DEFAULT_PREVIEW_SIZE;

            if (!((new File(previewFileLocation)).exists())) {
                previewFileLocation = runImageMagick(imageMagickExec, fileLocation, DEFAULT_PREVIEW_SIZE, "pdf");
            }

            if (previewFileLocation == null) {
                return null;
            }

            if (size == DEFAULT_PREVIEW_SIZE) {
                return previewFileLocation;
            }

            if (!((new File(thumbFileLocation)).exists())) {
                thumbFileLocation = runImageMagick(imageMagickExec, previewFileLocation, thumbFileLocation, size, "png");
            }

            return thumbFileLocation;

        }

        logger.fine("returning null");
        return null;

    }

    public String generateImageThumbnailFromFile(String fileLocation, int size) {

        String thumbFileLocation = fileLocation + ".thumb" + size;

        // see if the thumb is already generated and saved:
        if (new File(thumbFileLocation).exists()) {
            return thumbFileLocation;
        }

        // if not, let's attempt to generate the thumb:
        // (but only if the size is below the limit, or there is no limit...
        long fileSize;

        try {
            fileSize = new File(fileLocation).length();
        } catch (Exception ex) {
            fileSize = 0;
        }

        if (isImageOverSizeLimit(fileSize)) {
            return null;
        }

        try {
            logger.fine("attempting to read the image file " + fileLocation + " with ImageIO.read()");
            BufferedImage fullSizeImage = ImageIO.read(new File(fileLocation));

            if (fullSizeImage == null) {
                logger.warning("could not read image with ImageIO.read()");
                return null;
            }

            int width = fullSizeImage.getWidth(null);
            int height = fullSizeImage.getHeight(null);

            logger.fine("image dimensions: " + width + "x" + height);

            thumbFileLocation = rescaleImage(fullSizeImage, width, height, size, fileLocation);

            if (thumbFileLocation != null) {
                return thumbFileLocation;
            }
        } catch (Exception e) {
            logger.warning("Failed to read in an image from " + fileLocation + ": " + e.getMessage());
        }
        return null;

    }

    public String rescaleImage(BufferedImage fullSizeImage, int width, int height, int size, String fileLocation) {
        String outputLocation = fileLocation + "." + THUMBNAIL_SUFFIX + size;
        File outputFile = new File(outputLocation);
        OutputStream outputFileStream = null;

        try {
            outputFileStream = new FileOutputStream(outputFile);
        } catch (IOException ioex) {
            logger.warning("caught IO exception trying to open output stream for " + outputLocation);
            return null;
        }

        try {
            rescaleImage(fullSizeImage, width, height, size, outputFileStream);
        } catch (Exception ioex) {
            logger.warning("caught Exceptiopn trying to create rescaled image " + outputLocation);
            return null;
        } finally {
            IOUtils.closeQuietly(outputFileStream);
        }

        return outputLocation;
    }

    private static void rescaleImage(BufferedImage fullSizeImage, int width, int height, int size, OutputStream outputStream) throws IOException {

        double scaleFactor;
        int thumbHeight = size;
        int thumbWidth = size;

        if (width > height) {
            scaleFactor = ((double) size) / (double) width;
            thumbHeight = (int) (height * scaleFactor);
        } else {
            scaleFactor = ((double) size) / (double) height;
            thumbWidth = (int) (width * scaleFactor);
        }

        logger.fine("scale factor: " + scaleFactor);
        logger.fine("thumbnail dimensions: " + thumbWidth + "x" + thumbHeight);

        Image thumbImage = fullSizeImage.getScaledInstance(thumbWidth, thumbHeight, java.awt.Image.SCALE_FAST);

        ImageWriter writer;
        Iterator iter = ImageIO.getImageWritersByFormatName("png");
        if (iter.hasNext()) {
            writer = (ImageWriter) iter.next();
        } else {
            throw new IOException("Failed to locatie ImageWriter plugin for image type PNG");
        }

        BufferedImage lowRes = new BufferedImage(thumbWidth, thumbHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = lowRes.createGraphics();
        g2.drawImage(thumbImage, 0, 0, null);
        g2.dispose();

        try {
            ImageOutputStream ios = ImageIO.createImageOutputStream(outputStream);
            writer.setOutput(ios);

            // finally, save thumbnail image:
            writer.write(lowRes);
            writer.dispose();

            ios.close();
            thumbImage.flush();
            //fullSizeImage.flush();
            lowRes.flush();
        } catch (Exception ex) {
            logger.warning("Caught exception trying to generate thumbnail: " + ex.getMessage());
            throw new IOException("Caught exception trying to generate thumbnail: " + ex.getMessage());
        }
    }

    private boolean isImageOverSizeLimit(long fileSize) {
        return isFileOverSizeLimit("Image", fileSize);
    }

    private boolean isFileOverSizeLimit(String fileType, long fileSize) {
        long sizeLimit = getThumbnailSizeLimit(fileType);

        /*
         * sizeLimit set to -1 means that generation of thumbnails on the fly
         * is disabled:
         */
        if (sizeLimit < 0) {
            return true;
        }

        /*
         * sizeLimit set to 0 means no limit - generate thumbnails on the fly
         * for all files, regardless of size.
         */
        if (sizeLimit == 0) {
            return false;
        }

        if (fileSize == 0 || fileSize > sizeLimit) {
            // this is a broken file of size 0, or
            // this file is too large - no thumbnail:
            return true;
        }

        return false;
    }

    private long getThumbnailSizeLimitPDF() {
        return getThumbnailSizeLimit("PDF");
    }

    private long getThumbnailSizeLimit(String fileType) {
        String option = null;
        if (fileType.equals("Image")) {
            option = settingsService.getValueForKey(SettingsServiceBean.Key.ThumbnailImageSizeLimit);
        } else if (fileType.equals("PDF")) {
            option = settingsService.getValueForKey(SettingsServiceBean.Key.ThumbnailPDFSizeLimit);
        }
        Long limit = null;

        if (option != null && !option.isEmpty()) {
            limit = new Long(option);
        }

        return limit == null ? 0 : limit;
    }

    private String runImageMagick(String imageMagickExec, String fileLocation, int size, String format) {
        String thumbFileLocation = fileLocation + ".thumb" + size;
        return runImageMagick(imageMagickExec, fileLocation, thumbFileLocation, size, format);
    }

    private String runImageMagick(String imageMagickExec, String fileLocation, String thumbFileLocation, int size, String format) {
        String imageMagickCmd;

        if ("pdf".equals(format)) {
            imageMagickCmd = imageMagickExec + " pdf:" + fileLocation + "[0] -thumbnail " + size + "x" + size + " -flatten -strip png:" + thumbFileLocation;
        } else {
            imageMagickCmd = imageMagickExec + " " + format + ":" + fileLocation + " -thumbnail " + size + "x" + size + " -flatten -strip png:" + thumbFileLocation;
        }

        logger.fine("ImageMagick command line: " + imageMagickCmd);
        int exitValue;

        try {
            Runtime runtime = Runtime.getRuntime();
            Process process = runtime.exec(imageMagickCmd);
            exitValue = process.waitFor();
        } catch (Exception e) {
            exitValue = 1;
        }

        if (exitValue == 0 && new File(thumbFileLocation).exists()) {
            logger.fine("returning " + thumbFileLocation);
            return thumbFileLocation;
        }

        return null;
    }
}
