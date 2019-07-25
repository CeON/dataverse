package edu.harvard.iq.dataverse.dataset;

import edu.harvard.iq.dataverse.DataFile;

public class DatasetThumbnail {

    private final String base64image;
    private final DataFile dataFile;
    private final String filename;

    // -------------------- CONSTRUCTORS --------------------
    
    public DatasetThumbnail(String base64image) {
        this(base64image, null, null);
    }
    
    public DatasetThumbnail(String base64image, DataFile dataFile, String filename) {
        this.base64image = base64image;
        this.dataFile = dataFile;
        this.filename = filename;
    }

    // -------------------- GETTERS --------------------
    
    public String getBase64image() {
        return base64image;
    }

    public DataFile getDataFile() {
        return dataFile;
    }

    public boolean isFromDataFile() {
        return dataFile != null;
    }

    public String getFilename() {
        return filename;
    }
}
