package edu.harvard.iq.dataverse.thumbnail;

import edu.harvard.iq.dataverse.thumbnail.Thumbnail.ThumbnailSize;

public interface ThumbnailGenerator {

    boolean isSupported(String contentType, long fileSize);
    
    Thumbnail generateThumbnail(InputStreamWrapper sourceFileInputStream, ThumbnailSize thumbnailSize);
}
