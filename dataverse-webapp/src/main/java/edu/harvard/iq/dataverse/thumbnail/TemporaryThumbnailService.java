package edu.harvard.iq.dataverse.thumbnail;

import com.google.common.collect.Maps;
import edu.harvard.iq.dataverse.thumbnail.Thumbnail.ThumbnailSize;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class TemporaryThumbnailService {

    @Inject
    private ThumbnailGeneratorManager thumbnailGenerator;
    
    private Map<String, Map<ThumbnailSize, Thumbnail>> storageIds = Maps.newHashMap();
    
    
    // -------------------- LOGIC --------------------
    
    public synchronized boolean generateDefaultThumbnails(File sourceFile, String sourceContentType, String storageId) {
        if (!thumbnailGenerator.isSupported(sourceContentType, sourceFile.length())) {
            return false;
        }
        ThumbnailSize[] thumbnailSizes = ThumbnailSize.values();
        
        for (ThumbnailSize thumbnailSize: thumbnailSizes) {
            if (!generateThumbnail(sourceFile, sourceContentType, storageId, thumbnailSize)) {
                return false;
            }
        }
        return true;
    }
    
    public synchronized Thumbnail getThumbnail(String storageId, ThumbnailSize thumbnailSize) {
        if (!storageIds.containsKey(storageId)) {
            return null;
        }
        if (!storageIds.get(storageId).containsKey(thumbnailSize)) {
            return null;
        }
        return storageIds.get(storageId).get(thumbnailSize);
    }
    
    public synchronized Collection<Thumbnail> listThumbnails(String storageId) {
        if (!storageIds.containsKey(storageId)) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableCollection(storageIds.get(storageId).values());
    }
    
    public synchronized void removeGenerated(String storageId) {
        if (!storageIds.containsKey(storageId)) {
            return;
        }
        storageIds.remove(storageId);
    }
    
    // -------------------- PRIVATE --------------------
    
    private boolean generateThumbnail(File sourceFile, String sourceContentType, String storageId, ThumbnailSize thumbnailSize) {
        if (!storageIds.containsKey(storageId)) {
            storageIds.put(storageId, new HashMap<>());
        }
        
        try (InputStream is = new FileInputStream(sourceFile)) {
            InputStreamWrapper isWithSize = new InputStreamWrapper(is, sourceFile.length(), sourceContentType);
            Thumbnail thumbnail = thumbnailGenerator.generateThumbnail(isWithSize, thumbnailSize);
            
            storageIds.get(storageId).put(thumbnailSize, thumbnail);
            return true;
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return false;
    }
    
}
