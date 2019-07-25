package edu.harvard.iq.dataverse.thumbnail;

import com.google.common.base.Preconditions;
import edu.harvard.iq.dataverse.thumbnail.Thumbnail.ThumbnailSize;
import org.apache.commons.collections4.IteratorUtils;

import javax.ejb.Stateless;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import java.util.List;

@Stateless
public class ThumbnailGeneratorManager {

    private List<ThumbnailGenerator> thumbnailGenerators;
    
    // -------------------- CONSTRUCTORS --------------------
    
    @Deprecated /* for ejb */
    public ThumbnailGeneratorManager() {}
    
    @Inject
    public ThumbnailGeneratorManager(Instance<ThumbnailGenerator> thumbnailGenerators) {
        this.thumbnailGenerators = IteratorUtils.toList(thumbnailGenerators.iterator());
    }
    
    // -------------------- LOGIC --------------------
    
    public boolean isSupported(String contentType, long fileSize) {
        return thumbnailGenerators.stream()
                .anyMatch(generator -> generator.isSupported(contentType, fileSize));
    }
    
    public Thumbnail generateThumbnail(InputStreamWrapper is, ThumbnailSize thumbnailSize) {
        Preconditions.checkArgument(isSupported(is.getContentType(), is.getSize()));
        
        ThumbnailGenerator thumbnailGenerator = obtainThumbnailGenerator(is.getContentType(), is.getSize());
        
        return thumbnailGenerator.generateThumbnail(is, thumbnailSize);
    }
    
    // -------------------- PRIVATE --------------------
    
    private ThumbnailGenerator obtainThumbnailGenerator(String contentType, long fileSize) {
        return thumbnailGenerators.stream()
            .filter(generator -> generator.isSupported(contentType, fileSize))
            .findFirst()
            .get();
    }
}
