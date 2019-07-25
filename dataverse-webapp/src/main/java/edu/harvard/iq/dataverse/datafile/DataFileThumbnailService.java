package edu.harvard.iq.dataverse.datafile;

import edu.harvard.iq.dataverse.DataFile;
import edu.harvard.iq.dataverse.dataaccess.DataAccess;
import edu.harvard.iq.dataverse.dataaccess.StorageIO;
import edu.harvard.iq.dataverse.dataaccess.StorageIOUtils;
import edu.harvard.iq.dataverse.thumbnail.Thumbnail;
import edu.harvard.iq.dataverse.thumbnail.Thumbnail.ThumbnailSize;

import javax.ejb.Stateless;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Stateless
public class DataFileThumbnailService {
    private static final String THUMBNAIL_SUFFIX = "thumb";
    
    
    private DataAccess dataAccess = new DataAccess();
    
    
    // -------------------- LOGIC --------------------
    
    public boolean isThumbnailAvailable(DataFile dataFile) {
        return dataFile.isPreviewImageAvailable();
    }
    
    public Thumbnail getThumbnail(DataFile dataFile, ThumbnailSize thumbnailSize) {
        try {
            StorageIO<DataFile> storageIO = dataAccess.getStorageIO(dataFile);
            
            byte[] thumbnailContent = StorageIOUtils.fetchAuxFileAsBytes(storageIO, THUMBNAIL_SUFFIX + thumbnailSize.getSize());
            return new Thumbnail(thumbnailContent, thumbnailSize);
        } catch (IOException ioEx) {
            throw new RuntimeException("Error retrieving thumbnail", ioEx);
        }
    }
    
    public void saveThumbnails(DataFile dataFile, List<Thumbnail> thumbnails) {
        for (Thumbnail thumbnail: thumbnails) {
            saveThumbnailInStorage(dataFile, thumbnail);
        }
        dataFile.setPreviewImageAvailable(true);
    }
    
    public void saveThumbnail(DataFile dataFile, Thumbnail thumbnail) {
        saveThumbnailInStorage(dataFile, thumbnail);
        dataFile.setPreviewImageAvailable(true);
    }
    
    public void removeThumbnails(DataFile dataFile) {
        dataFile.setPreviewImageAvailable(false);
        removeFromStorage(dataFile);
    }
    
    
    
    // -------------------- PRIVATE --------------------
    
    private void saveThumbnailInStorage(DataFile dataFile, Thumbnail thumbnail) {
        try {
            StorageIO<DataFile> storageIO = dataAccess.getStorageIO(dataFile);
            StorageIOUtils.saveBytesAsAuxFile(storageIO, thumbnail.getData(), THUMBNAIL_SUFFIX + thumbnail.getSize().getSize());
        } catch (IOException e) {
            throw new RuntimeException("Error while saving thumbnail", e);
        }
    }
    
    private void removeFromStorage(DataFile dataFile) {
        StorageIO<DataFile> storageIO;
        try {
            storageIO = dataAccess.getStorageIO(dataFile);
            
            List<String> thumbnailAuxObjects = storageIO.listAuxObjects().stream()
                .filter(auxObject -> auxObject.startsWith(THUMBNAIL_SUFFIX))
                .collect(Collectors.toList());
            
            for (String thumbnailAuxObject: thumbnailAuxObjects) {
                storageIO.deleteAuxObject(thumbnailAuxObject);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error while removing thumbnails", e);
        }
        
    }
    
}
