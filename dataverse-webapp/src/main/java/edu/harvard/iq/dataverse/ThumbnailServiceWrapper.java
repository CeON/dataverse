/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.harvard.iq.dataverse;

import edu.harvard.iq.dataverse.datafile.DataFileThumbnailService;
import edu.harvard.iq.dataverse.dataset.DatasetThumbnail;
import edu.harvard.iq.dataverse.dataset.DatasetThumbnailService;
import edu.harvard.iq.dataverse.dataverse.DataverseThumbnailService;
import edu.harvard.iq.dataverse.search.SearchConstants;
import edu.harvard.iq.dataverse.search.SolrSearchResult;
import edu.harvard.iq.dataverse.thumbnail.Thumbnail;
import edu.harvard.iq.dataverse.thumbnail.ThumbnailUtil;
import edu.harvard.iq.dataverse.thumbnail.Thumbnail.ThumbnailSize;
import org.apache.commons.lang3.StringUtils;

import javax.ejb.EJB;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Leonid Andreev
 */
@ViewScoped
public class ThumbnailServiceWrapper implements java.io.Serializable {
    @Inject
    PermissionsWrapper permissionsWrapper;
    @EJB
    DataverseServiceBean dataverseService;
    @EJB
    DatasetServiceBean datasetService;
    @EJB
    DatasetVersionServiceBean datasetVersionService;
    @EJB
    DataFileServiceBean dataFileService;
    
    @Inject
    private DataFileThumbnailService dataFileThumbnailService;
    @Inject
    private DatasetThumbnailService datasetThumbnailService;
    @Inject
    private DataverseThumbnailService dataverseThumbnailService;

    private Map<Long, String> dvobjectThumbnailsMap = new HashMap<>();
    private Map<Long, DvObject> dvobjectViewMap = new HashMap<>();


    // it's the responsibility of the user - to make sure the search result
    // passed to this method is of the Datafile type!
    public String getFileCardImageAsBase64Url(SolrSearchResult result) {
        
        Long imageFileId = result.getEntity().getId();
        DataFile dataFile = (DataFile)result.getEntity();
        
        // Before we do anything else, check if it's a harvested dataset; 
        // no need to check anything else if so (harvested objects never have 
        // thumbnails)

        if (result.isHarvested()) {
            return null;
        }
        if (imageFileId == null) {
            return null;
        }

        if (this.dvobjectThumbnailsMap.containsKey(imageFileId)) {
            // Yes, return previous answer
            //logger.info("using cached result for ... "+datasetId);
            return this.dvobjectThumbnailsMap.get(imageFileId);
        }
        
        updateDataFileFromSearchResult(result);

        if (StringUtils.equals(result.getFileAccess(), SearchConstants.RESTRICTED)
                && !permissionsWrapper.hasDownloadFilePermission(dataFile)) {
            dvobjectThumbnailsMap.put(imageFileId, null);
            return null;
        }
        
        
        if (!dataFileThumbnailService.isThumbnailAvailable(dataFile)) {
            dvobjectThumbnailsMap.put(imageFileId, null);
            return null;
        }

        Thumbnail thumbnail = dataFileThumbnailService.getThumbnail(dataFile, ThumbnailSize.CARD);
        String cardImageUrl = ThumbnailUtil.thumbnailAsBase64(thumbnail);
        
        
        this.dvobjectThumbnailsMap.put(imageFileId, cardImageUrl);
        //logger.info("datafile id " + imageFileId + ", returning " + cardImageUrl);

        if (!(dvobjectViewMap.containsKey(imageFileId)
                && dvobjectViewMap.get(imageFileId).isInstanceofDataFile())) {

            dvobjectViewMap.put(imageFileId, result.getEntity());

        }

        return cardImageUrl;
    }

    // it's the responsibility of the user - to make sure the search result
    // passed to this method is of the Dataset type!
    public String getDatasetCardImageAsBase64Url(SolrSearchResult result) {
        // Before we do anything else, check if it's a harvested dataset; 
        // no need to check anything else if so (harvested datasets never have 
        // thumbnails)

        if (result.isHarvested()) {
            return null;
        }

        // Check if the search result ("card") contains an entity, before 
        // attempting to convert it to a Dataset. It occasionally happens that 
        // solr has indexed datasets that are no longer in the database. If this
        // is the case, the entity will be null here; and proceeding any further
        // results in a long stack trace in the log file. 
        if (result.getEntity() == null) {
            return null;
        }
        Dataset dataset = (Dataset) result.getEntity();

        Long versionId = result.getDatasetVersionId();

        // TODO autoselect if result.isPublishedState() ?
        return getDatasetCardImageAsBase64Url(dataset, versionId);
    }

    public String getDatasetCardImageAsBase64Url(Dataset dataset, Long versionId) {
        Long datasetId = dataset.getId();
        if (datasetId != null) {
            if (this.dvobjectThumbnailsMap.containsKey(datasetId)) {
                // Yes, return previous answer
                // (at max, there could only be 2 cards for the same dataset
                // on the page - the draft, and the published version; but it's 
                // still nice to try and cache the result - especially if it's an
                // uploaded logo - we don't want to read it off disk twice). 
                return this.dvobjectThumbnailsMap.get(datasetId);
            }
        }

        DatasetThumbnail datasetThumbnail = datasetThumbnailService.getThumbnailBase64(dataset).orElse(null);
        
        if (datasetThumbnail != null) {
            this.dvobjectThumbnailsMap.put(datasetId, datasetThumbnail.getBase64image());
            return datasetThumbnail.getBase64image();
        }

        // And finally, try to auto-select the thumbnail (unless instructed not to):
        this.dvobjectThumbnailsMap.put(datasetId, null);
        return null;
    }

    // it's the responsibility of the user - to make sure the search result
    // passed to this method is of the Dataverse type!
    public String getDataverseCardImageAsBase64Url(SolrSearchResult result) {
        Thumbnail thumbnail = dataverseThumbnailService.getDataverseLogoThumbnail(result.getEntityId());
        return ThumbnailUtil.thumbnailAsBase64(thumbnail);
    }

    public void resetObjectMaps() {
        dvobjectThumbnailsMap = new HashMap<>();
        dvobjectViewMap = new HashMap<>();
    }

    private void updateDataFileFromSearchResult(SolrSearchResult result) {
        if (result.getTabularDataTags() != null) {
            for (String tabularTagLabel : result.getTabularDataTags()) {
                DataFileTag tag = new DataFileTag();
                try {
                    tag.setTypeByLabel(tabularTagLabel);
                    tag.setDataFile((DataFile) result.getEntity());
                    ((DataFile) result.getEntity()).addTag(tag);
                } catch (IllegalArgumentException iax) {
                    // ignore 
                }
            }
        }
        
    }

}
