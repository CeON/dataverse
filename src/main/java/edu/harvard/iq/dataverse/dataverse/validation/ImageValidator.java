package edu.harvard.iq.dataverse.dataverse.validation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ImageValidator {

    public static boolean isImageResolutionTooBig(byte[] imageBytes, int maxWidth, int maxHeight) {

        InputStream in = new ByteArrayInputStream(imageBytes);

        BufferedImage buf = null;
        try {
            buf = ImageIO.read(in);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return buf != null && (buf.getWidth() > maxWidth || buf.getHeight() > maxHeight);

    }
}
