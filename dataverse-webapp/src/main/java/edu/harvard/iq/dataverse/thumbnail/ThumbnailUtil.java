package edu.harvard.iq.dataverse.thumbnail;

import edu.harvard.iq.dataverse.util.FileUtil;
import org.apache.commons.lang3.StringUtils;
import org.primefaces.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class ThumbnailUtil {

    public static String thumbnailAsBase64(Thumbnail thumbnail) {
        if (thumbnail == null) {
            return StringUtils.EMPTY;
        }
        String imageDataBase64 = Base64.encodeToString(thumbnail.getData(), false);
        return FileUtil.DATA_URI_SCHEME + imageDataBase64;
    }
    
    public static InputStream thumbnailAsInputStream(Thumbnail thumbnail) {
        return new ByteArrayInputStream(thumbnail.getData());
    }
    
    public static InputStream thumbnailBase64AsInputStream(String base64) {
        String leadingStringToRemove = FileUtil.DATA_URI_SCHEME;
        String encodedImg = base64.substring(leadingStringToRemove.length());
        
        byte[] decodedImg = java.util.Base64.getDecoder().decode(encodedImg.getBytes(StandardCharsets.UTF_8));
        return new ByteArrayInputStream(decodedImg);
    }
}
