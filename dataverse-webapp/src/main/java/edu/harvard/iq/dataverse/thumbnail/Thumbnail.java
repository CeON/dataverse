package edu.harvard.iq.dataverse.thumbnail;

public class Thumbnail {

    public enum ThumbnailSize {
        PREVIEW(400),
        DEFAULT(64),
        CARD(48);
        
        private int size;
        
        private ThumbnailSize(int size) {
            this.size = size;
        }

        public int getSize() {
            return size;
        }
    }
    
    private byte[] data;
    private ThumbnailSize size;
    
    
    // -------------------- CONSTRUCTORS --------------------
    
    public Thumbnail(byte[] data, ThumbnailSize size) {
        super();
        this.data = data;
        this.size = size;
    }

    // -------------------- GETTERS --------------------
    
    public byte[] getData() {
        return data;
    }

    public ThumbnailSize getSize() {
        return size;
    }
}
