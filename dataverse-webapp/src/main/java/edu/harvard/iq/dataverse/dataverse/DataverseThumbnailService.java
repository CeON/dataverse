package edu.harvard.iq.dataverse.dataverse;

import edu.harvard.iq.dataverse.DataverseServiceBean;
import edu.harvard.iq.dataverse.DataverseTheme;
import edu.harvard.iq.dataverse.thumbnail.InputStreamWrapper;
import edu.harvard.iq.dataverse.thumbnail.Thumbnail;
import edu.harvard.iq.dataverse.thumbnail.ThumbnailGeneratorManager;
import edu.harvard.iq.dataverse.thumbnail.Thumbnail.ThumbnailSize;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

import javax.annotation.PostConstruct;
import javax.ejb.Stateless;
import javax.inject.Inject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Stateless
public class DataverseThumbnailService {
    
    private final static String THUMBNAIL_SUFFIX = "thumb";
    
    @Inject
    private ThumbnailGeneratorManager thumbnailGeneratorManager;
    
    @Inject
    private DataverseServiceBean dataverseService;
    
    
    private File dataverseLogoBaseFile;
    
    @PostConstruct
    public void postConstruct() {
        Properties p = System.getProperties();
        String domainRoot = p.getProperty("com.sun.aas.instanceRoot");

        dataverseLogoBaseFile = new File(domainRoot + File.separator +
                "docroot" + File.separator + "logos");
        
    }
    
    public Thumbnail getDataverseLogoThumbnail(Long dvId) {
        
        File dataverseLogoFile = getLogoById(dvId);
        if (dataverseLogoFile == null) {
            return null;
        }
        
        if (!thumbnailExists(dataverseLogoFile)) {
            return generateThumbnail(dataverseLogoFile);
        }
        
        
        try {
            return new Thumbnail(FileUtils.readFileToByteArray(obtainThumbnailFile(dataverseLogoFile)), ThumbnailSize.CARD);
        } catch (IOException e) {
            throw new RuntimeException("Error reading thumbnail", e);
        }
    }
    
    
    private File obtainThumbnailFile(File dataverseLogoFile) {
        return new File(dataverseLogoFile.getParent(), dataverseLogoFile.getName() + THUMBNAIL_SUFFIX + ThumbnailSize.CARD.getSize());
    }
    
    private boolean thumbnailExists(File dataverseLogoFile) {
        File destThumbnail = obtainThumbnailFile(dataverseLogoFile);
        return destThumbnail.exists();
    }
    
    private Thumbnail generateThumbnail(File dataverseLogoFile) {
        File destThumbnail = obtainThumbnailFile(dataverseLogoFile);
        
        try (InputStream is = new FileInputStream(dataverseLogoFile)) {
            InputStreamWrapper withSize = new InputStreamWrapper(is, dataverseLogoFile.length(), "image/png");
            Thumbnail thumbnail = thumbnailGeneratorManager.generateThumbnail(withSize, ThumbnailSize.CARD);
            FileUtils.writeByteArrayToFile(destThumbnail, thumbnail.getData());
            
            return thumbnail;
        } catch (IOException e) {
            throw new RuntimeException("Error copying thumbnail", e);
        }
    }
    
    private File getLogoById(Long id) {

        DataverseTheme theme = dataverseService.findDataverseThemeByIdQuick(id);
        if (theme == null) {
            return null;
        }
        
        String logoFileName = theme.getLogo();

        if (StringUtils.isNotEmpty(logoFileName)) {
            return new File(dataverseLogoBaseFile, id + File.separator + logoFileName);
        }

        return null;
    }
}
