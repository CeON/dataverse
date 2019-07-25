package edu.harvard.iq.dataverse.dataset;

import com.google.common.base.Preconditions;
import edu.harvard.iq.dataverse.DataFile;
import edu.harvard.iq.dataverse.dataaccess.DataAccess;
import edu.harvard.iq.dataverse.dataaccess.StorageIO;
import edu.harvard.iq.dataverse.thumbnail.InputStreamWrapper;

import javax.ejb.Stateless;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channel;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.logging.Logger;

@Stateless
public class WorldMapThumbnailSourceFileProvider implements ThumbnailSourceFileProvider {

    private static final Logger logger = Logger.getLogger(WorldMapThumbnailSourceFileProvider.class.getCanonicalName());
    public static String WORLDMAP_IMAGE_SUFFIX = "img";
    
    private DataAccess dataAccess = new DataAccess();
    
    // -------------------- LOGIC --------------------
    
    @Override
    public boolean isApplicable(DataFile dataFile) {
        return dataFile.getContentType().equalsIgnoreCase("application/zipped-shapefile") || (dataFile.isTabularData() && dataFile.hasGeospatialTag());
    }

    @Override
    public InputStreamWrapper obtainThumbnailSourceFile(DataFile dataFile) {
        Preconditions.checkArgument(isApplicable(dataFile));

        StorageIO<DataFile> storageIO = null;
        InputStream worldMapImageInputStream = null;
        
        try {
            storageIO = dataAccess.getStorageIO(dataFile);
            storageIO.open();
            
            Channel worldMapImageChannel = storageIO.openAuxChannel(WORLDMAP_IMAGE_SUFFIX);
            
            if (worldMapImageChannel == null) {
                logger.warning("Could not open channel for aux ." + WORLDMAP_IMAGE_SUFFIX );
                return null;
            }
            long worldMapImageSize = storageIO.getAuxObjectSize(WORLDMAP_IMAGE_SUFFIX);
            
            worldMapImageInputStream = Channels.newInputStream((ReadableByteChannel) worldMapImageChannel);
            
            return new InputStreamWrapper(worldMapImageInputStream, worldMapImageSize, "image/png");
        } catch (IOException e) {
            return null;
        }
        
    }

}
