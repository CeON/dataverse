package edu.harvard.iq.dataverse.thumbnail;

import com.google.common.base.Preconditions;
import edu.harvard.iq.dataverse.files.mime.ImageMimeType;
import edu.harvard.iq.dataverse.qualifiers.ProductionBean;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean.Key;
import edu.harvard.iq.dataverse.thumbnail.Thumbnail.ThumbnailSize;

import javax.annotation.PostConstruct;
import javax.ejb.Stateless;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.inject.Inject;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.logging.Logger;

@Stateless
public class ImageThumbnailGenerator implements ThumbnailGenerator {

    private static final Logger logger = Logger.getLogger(ImageThumbnailGenerator.class.getCanonicalName());
    
    private SettingsServiceBean settingsService;
    
    private long sizeLimit;
    
    
    // -------------------- CONSTRUCTORS --------------------
    
    @Inject
    public ImageThumbnailGenerator(@ProductionBean SettingsServiceBean settingsService) {
        this.settingsService = settingsService;
    }
    
    @PostConstruct
    public void postConstruct() {
        sizeLimit = settingsService.getValueForKeyAsLong(Key.ThumbnailImageSizeLimit);
    }
    
    
    // -------------------- LOGIC --------------------
    
    @Override
    public boolean isSupported(String contentType, long fileSize) {
        // Some browsers (Chrome?) seem to identify FITS files as mime
        // type "image/fits" on upload; this is both incorrect (the official
        // mime type for FITS is "application/fits", and problematic: then
        // the file is identified as an image, and the page will attempt to 
        // generate a preview - which of course is going to fail...
        if (ImageMimeType.FITSIMAGE.getMimeValue().equalsIgnoreCase(contentType)) {
            return false;
        }
        if (!contentType.startsWith("image/")) {
            return false;
        }
        return !isFileOverSizeLimit(fileSize);
    }

    @Override
    public Thumbnail generateThumbnail(InputStreamWrapper sourceInputStream, ThumbnailSize thumbnailSize) {
        Preconditions.checkArgument(isSupported(sourceInputStream.getContentType(), sourceInputStream.getSize()));
        
        logger.fine("attempting to read the image file " + sourceInputStream + " with ImageIO.read()");
        BufferedImage fullSizeImage;
        try {
            fullSizeImage = ImageIO.read(sourceInputStream.getInputStream());
        } catch (IOException e1) {
            throw new RuntimeException("could not read image with ImageIO.read()", e1);
        }
        
        if (fullSizeImage == null) {
            throw new RuntimeException("could not read image with ImageIO.read()");
        }

        int width = fullSizeImage.getWidth(null);
        int height = fullSizeImage.getHeight(null);

        logger.fine("image dimensions: " + width + "x" + height);

        byte[] thumbnailBytes = rescaleImage(fullSizeImage, width, height, thumbnailSize.getSize());
        return new Thumbnail(thumbnailBytes, thumbnailSize);
    }

    // -------------------- PRIVATE --------------------
    
    private byte[] rescaleImage(BufferedImage fullSizeImage, int width, int height, int size) {

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            
            rescaleImage(fullSizeImage, width, height, size, outputStream);
            return outputStream.toByteArray();
            
        } catch (Exception ioex) {
            throw new RuntimeException("caught Exceptiopn trying to create rescaled image", ioex);
        }
    }
    
    
    private static void rescaleImage(BufferedImage fullSizeImage, int width, int height, int size, OutputStream outputStream) throws IOException {

        double scaleFactor = 0.0;
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

        // If we are willing to spend a few extra CPU cycles to generate
        // better-looking thumbnails, we can the SCALE_SMOOTH flag. 
        // SCALE_FAST trades quality for speed.
        Image thumbImage = fullSizeImage.getScaledInstance(thumbWidth, thumbHeight, java.awt.Image.SCALE_FAST);

        
        ImageWriter writer = null;
        Iterator<ImageWriter> iter = ImageIO.getImageWritersByFormatName("png");
        if (iter.hasNext()) {
            writer = iter.next();
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
            throw new IOException("Caught exception trying to generate thumbnail: " + ex.getMessage());
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
