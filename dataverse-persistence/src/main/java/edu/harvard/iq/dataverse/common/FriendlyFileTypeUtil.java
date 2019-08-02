package edu.harvard.iq.dataverse.common;

import edu.harvard.iq.dataverse.common.files.mime.ShapefileMimeType;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;

import java.util.MissingResourceException;

public class FriendlyFileTypeUtil {

    public static String getUserFriendlyFileType(DataFile dataFile) {
        String fileType = dataFile.getContentType();

        if (fileType != null) {
            if (fileType.equalsIgnoreCase(ShapefileMimeType.SHAPEFILE_FILE_TYPE.getMimeValue())) {
                return ShapefileMimeType.SHAPEFILE_FILE_TYPE.getFriendlyName();
            }
            if (fileType.contains(";")) {
                fileType = fileType.substring(0, fileType.indexOf(";"));
            }
            try {
                return BundleUtil.getStringFromPropertyFile(fileType, "MimeTypeDisplay");
            } catch (MissingResourceException e) {
                return fileType;
            }
        }

        return fileType;
    }
    
    
    public static String getUserFriendlyOriginalType(DataFile dataFile) {
        if (!dataFile.isTabularData()) {
            return null;
        }

        String fileType = dataFile.getOriginalFileFormat();

        if (fileType != null && !fileType.equals("")) {
            if (fileType.contains(";")) {
                fileType = fileType.substring(0, fileType.indexOf(";"));
            }
            try {
                return BundleUtil.getStringFromPropertyFile(fileType, "MimeTypeDisplay");
            } catch (MissingResourceException e) {
                return fileType;
            }
        }

        return "UNKNOWN";
    }
}
