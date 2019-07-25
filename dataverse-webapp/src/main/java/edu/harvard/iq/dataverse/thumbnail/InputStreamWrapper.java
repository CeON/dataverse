package edu.harvard.iq.dataverse.thumbnail;

import java.io.InputStream;

/**
 * Wrapped input stream with additional information
 * about total size of input stream and content type
 * of file returned when input stream is read.
 * 
 * @author madryk
 */
public class InputStreamWrapper {

    private InputStream inputStream;
    
    private long size;

    private String contentType;

    // -------------------- CONSTRUCTORS --------------------
    
    public InputStreamWrapper(InputStream inputStream, long size, String contentType) {
        this.inputStream = inputStream;
        this.size = size;
        this.contentType = contentType;
    }
    
    // -------------------- GETTERS --------------------
    
    public InputStream getInputStream() {
        return inputStream;
    }

    public long getSize() {
        return size;
    }

    public String getContentType() {
        return contentType;
    }
}
