package edu.harvard.iq.dataverse.datafile;

import com.google.common.base.Preconditions;
import edu.harvard.iq.dataverse.DataFile;
import edu.harvard.iq.dataverse.dataset.ThumbnailSourceFileProvider;
import edu.harvard.iq.dataverse.thumbnail.InputStreamWrapper;
import edu.harvard.iq.dataverse.thumbnail.Thumbnail;
import edu.harvard.iq.dataverse.thumbnail.ThumbnailGeneratorManager;
import edu.harvard.iq.dataverse.thumbnail.Thumbnail.ThumbnailSize;
import org.apache.commons.collections4.IteratorUtils;

import javax.ejb.Stateless;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import java.util.ArrayList;
import java.util.List;

@Stateless
public class DataFileThumbnailGenerator {
    
    private List<ThumbnailSourceFileProvider> thumbnailSourceFileProviders;
    
    private ThumbnailGeneratorManager thumbnailGeneratorManager;
    
    
    // -------------------- CONSTRUCTORS --------------------
    
    @Deprecated /* for ejb */
    public DataFileThumbnailGenerator() {}
    
    @Inject
    public DataFileThumbnailGenerator(Instance<ThumbnailSourceFileProvider> thumbnailSourceFileProviders,
                                        ThumbnailGeneratorManager thumbnailGeneratorManager) {
        
        this.thumbnailSourceFileProviders = IteratorUtils.toList(thumbnailSourceFileProviders.iterator());
        this.thumbnailGeneratorManager = thumbnailGeneratorManager;
    }
    
    // -------------------- LOGIC --------------------
    
    public boolean isSupported(DataFile dataFile) {
        if (dataFile.isHarvested() || "".equals(dataFile.getStorageIdentifier())) {
            return false;
        }

        return thumbnailSourceFileProviders.stream()
                .anyMatch(provider -> provider.isApplicable(dataFile));
    }
    
    public List<Thumbnail> generateThumbnailAllSizes(DataFile dataFile) {
        Preconditions.checkArgument(isSupported(dataFile));
        List<Thumbnail> thumbnails = new ArrayList<Thumbnail>();
        
        for (ThumbnailSize thumbnailSize: ThumbnailSize.values()) {
            thumbnails.add(generateThumbnail(dataFile, thumbnailSize));
        }
        
        return thumbnails;
    }
    
    // -------------------- PRIVATE --------------------
    
    private Thumbnail generateThumbnail(DataFile dataFile, ThumbnailSize thumbnailSize) {
        
        ThumbnailSourceFileProvider provider = obtainProvider(dataFile);
        
        InputStreamWrapper is = provider.obtainThumbnailSourceFile(dataFile);
        
        return thumbnailGeneratorManager.generateThumbnail(is, thumbnailSize);
        
    }
    
    private ThumbnailSourceFileProvider obtainProvider(DataFile dataFile) {
        return thumbnailSourceFileProviders.stream()
                .filter(provider -> provider.isApplicable(dataFile))
                .findFirst()
                .get();
    }
    
}
