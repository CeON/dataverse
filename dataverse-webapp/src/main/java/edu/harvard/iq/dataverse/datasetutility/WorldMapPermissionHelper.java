package edu.harvard.iq.dataverse.datasetutility;

import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.MapLayerMetadataServiceBean;
import edu.harvard.iq.dataverse.PermissionServiceBean;
import edu.harvard.iq.dataverse.persistence.DvObject;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.datafile.MapLayerMetadata;
import edu.harvard.iq.dataverse.persistence.datafile.license.FileTermsOfUse.TermsOfUseType;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.user.GuestUser;
import edu.harvard.iq.dataverse.persistence.user.Permission;
import edu.harvard.iq.dataverse.persistence.user.User;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.settings.SettingsWrapper;
import org.omnifaces.cdi.ViewScoped;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class originally encapsulated display logic for the DatasetPage
 * <p>
 * It allows the following checks without redundantly querying the db to
 * check permissions or if MapLayerMetadata exists
 * <p>
 * - canUserSeeMapDataButton (private)
 * - canUserSeeMapDataButtonFromPage (public)
 * - canUserSeeMapDataButtonFromAPI (public)
 * <p>
 * - canSeeMapButtonReminderToPublish (private)
 * - canSeeMapButtonReminderToPublishFromPage (public)
 * - canSeeMapButtonReminderToPublishFromAPI (public)
 * <p>
 * - canUserSeeExploreWorldMapButton (private)
 * - canUserSeeExploreWorldMapButtonFromPage (public)
 * - canUserSeeExploreWorldMapButtonFromAPI (public)
 *
 * @author rmp553
 */
@ViewScoped
@Named
public class WorldMapPermissionHelper implements java.io.Serializable {

    @Inject
    SettingsWrapper settingsWrapper;
    @Inject
    SettingsServiceBean settingsService;
    @Inject
    MapLayerMetadataServiceBean mapLayerMetadataService;
    @Inject
    PermissionServiceBean permissionService;
    @Inject
    DataverseSession session;


    private final Map<Long, Boolean> fileMetadataWorldMapExplore = new HashMap<>(); // { FileMetadata.id : Boolean } 
    private Map<Long, MapLayerMetadata> mapLayerMetadataLookup = null;
    private final Map<Permission, Boolean> datasetPermissionMap = new HashMap<>();


    public WorldMapPermissionHelper() {

    }


    /**
     * Using a DataFile id, retrieve an associated MapLayerMetadata object
     * <p>
     * The MapLayerMetadata objects have been fetched at page inception by
     * "loadMapLayerMetadataLookup()"
     */
    public MapLayerMetadata getMapLayerMetadata(DataFile df) {
        if (df == null) {
            return null;
        }
        if (mapLayerMetadataLookup == null) {
            loadMapLayerMetadataLookup(df.getOwner());
        }
        return this.mapLayerMetadataLookup.get(df.getId());
    }


    /*
     * Call this when using the API
     *   - calls private method canUserSeeExploreWorldMapButton
     */
    public boolean canUserSeeExploreWorldMapButtonFromAPI(FileMetadata fm, User user) {

        if (fm == null) {
            return false;
        }
        if (user == null) {
            return false;
        }
        if (!this.permissionService.userOn(user, fm.getDataFile()).has(Permission.DownloadFile)) {
            return false;
        }

        return this.canUserSeeExploreWorldMapButton(fm);
    }

    /**
     * Call this from a Dataset or File page
     * - calls private method canUserSeeExploreWorldMapButton
     * <p>
     * WARNING: Before calling this, make sure the user has download
     * permission for the file!!  (See DatasetPage.canDownloadFile())
     *
     * @param FileMetadata fm
     * @return boolean
     */
    public boolean canUserSeeExploreWorldMapButtonFromPage(FileMetadata fm) {

        if (fm == null) {
            return false;
        }
        if (this.fileMetadataWorldMapExplore.containsKey(fm.getId())) {
            // Yes, return previous answer
            //logger.info("using cached result for candownloadfile on filemetadata "+fid);
            return this.fileMetadataWorldMapExplore.get(fm.getId());
        }
        
        boolean canUserExploreWorldMap = this.canUserSeeExploreWorldMapButton(fm);
        this.fileMetadataWorldMapExplore.put(fm.getId(), canUserExploreWorldMap);
        
        return canUserExploreWorldMap;
    }

    /**
     * WARNING: Before calling this, make sure the user has download
     * permission for the file!!  (See DatasetPage.canDownloadFile())
     * <p>
     * Should there be a Explore WorldMap Button for this file?
     * See table in: https://github.com/IQSS/dataverse/issues/1618
     * <p>
     * (1) Does the file have MapLayerMetadata?
     * (2) Are the proper settings in place
     *
     * @param fm FileMetadata
     * @return boolean
     */
    private boolean canUserSeeExploreWorldMapButton(FileMetadata fm) {
        
        /* -----------------------------------------------------
           Does a Map Exist?
         ----------------------------------------------------- */
        if (!(this.hasMapLayerMetadata(fm))) {
            //See if it does
            MapLayerMetadata layer_metadata = mapLayerMetadataService.findMetadataByDatafile(fm.getDataFile());
            if (layer_metadata != null) {
                if (mapLayerMetadataLookup == null) {
                    loadMapLayerMetadataLookup(fm.getDataFile().getOwner());
                }
                // yes: keep going...
                mapLayerMetadataLookup.put(layer_metadata.getDataFile().getId(), layer_metadata);
            } else {
                // Nope: no button
                return false;
            }
        }
              
        /*
            Is setting for GeoconnectViewMaps true?
            Nope? no button
        */
        if (!settingsService.isTrueForKey(SettingsServiceBean.Key.GeoconnectViewMaps)) {
            return false;
        }
        //----------------------------------------------------------------------
        //(0) Before we give it to you - if version is deaccessioned and user
        // does not have edit dataset permission then may download
        //----------------------------------------------------------------------

        if (fm.getDatasetVersion().isDeaccessioned()) {
            return this.doesSessionUserHavePermission(Permission.EditDataset, fm);
        }
        //Check for restrictions

        boolean isRestrictedFile = fm.getTermsOfUse().getTermsOfUseType() == TermsOfUseType.RESTRICTED;

        // --------------------------------------------------------------------
        //  Is the file Unrestricted ?        
        // --------------------------------------------------------------------
        if (!isRestrictedFile) {
            return true;
        }

        // --------------------------------------------------------------------
        // Conditions (2) through (4) are for Restricted files
        // --------------------------------------------------------------------


        if (session.getUser() instanceof GuestUser) {
            return false;
        }


        // --------------------------------------------------------------------
        // (3) Does the User have DownloadFile Permission at the **Dataset** level 
        // --------------------------------------------------------------------


        if (!this.doesSessionUserHavePermission(Permission.DownloadFile, fm)) {
            return false;
        }
        
        /* -----------------------------------------------------
             Yes: User can view button!
         ----------------------------------------------------- */
        return true;
    }


    /*
     Check if the FileMetadata.dataFile has an associated MapLayerMetadata object
    
     The MapLayerMetadata objects have been fetched at page inception by "loadMapLayerMetadataLookup()" 
     */
    public boolean hasMapLayerMetadata(FileMetadata fm) {
        if (fm == null) {
            return false;
        }
        if (fm.getDataFile() == null) {
            return false;
        }
        if (mapLayerMetadataLookup == null) {
            loadMapLayerMetadataLookup(fm.getDataFile().getOwner());
        }
        return doesDataFileHaveMapLayerMetadata(fm.getDataFile());
    }

    /**
     * Check if a DataFile has an associated MapLayerMetadata object
     * <p>
     * The MapLayerMetadata objects have been fetched at page inception by
     * "loadMapLayerMetadataLookup()"
     */
    private boolean doesDataFileHaveMapLayerMetadata(DataFile df) {
        if (df == null) {
            return false;
        }
        if (df.getId() == null) {
            return false;
        }
        return this.mapLayerMetadataLookup.containsKey(df.getId());
    }


    /**
     * Create a hashmap consisting of { DataFile.id : MapLayerMetadata object}
     * <p>
     * Very few DataFiles will have associated MapLayerMetadata objects so only
     * use 1 query to get them
     */
    private void loadMapLayerMetadataLookup(Dataset dataset) {
        mapLayerMetadataLookup = new HashMap<>();
        if (dataset == null) {
        }
        if (dataset.getId() == null) {
            return;
        }
        List<MapLayerMetadata> mapLayerMetadataList = mapLayerMetadataService.getMapLayerMetadataForDataset(dataset);
        if (mapLayerMetadataList == null) {
            return;
        }
        for (MapLayerMetadata layer_metadata : mapLayerMetadataList) {
            mapLayerMetadataLookup.put(layer_metadata.getDataFile().getId(), layer_metadata);
        }

    }// A DataFile may have a related MapLayerMetadata object


    /**
     * Check if this is a mappable file type.
     * <p>
     * Currently (2/2016)
     * - Shapefile (zipped shapefile)
     * - Tabular file with Geospatial Data tag
     *
     * @param fm
     * @return
     */
    private boolean isPotentiallyMappableFileType(FileMetadata fm) {
        if (fm == null) {
            return false;
        }

        // Yes, it's a shapefile
        //
        if (this.isShapefileType(fm)) {
            return true;
        }

        // Yes, it's tabular with a geospatial tag
        //
        if (fm.getDataFile().isTabularData()) {
            return fm.getDataFile().hasGeospatialTag();
        }
        return false;
    }


    public boolean isShapefileType(FileMetadata fm) {
        if (fm == null) {
            return false;
        }
        if (fm.getDataFile() == null) {
            return false;
        }

        return fm.getDataFile().isShapefileType();
    }


    /**
     * Call this from a Dataset or File page
     * - calls private method canSeeMapButtonReminderToPublish
     * <p>
     * WARNING: Assumes user isAuthenicated AND has Permission.EditDataset
     * - These checks should be made on the DatasetPage or FilePage which calls this method
     *
     * @param FileMetadata fm
     * @return boolean
     */
    public boolean canSeeMapButtonReminderToPublishFromPage(FileMetadata fm) {
        if (fm == null) {
            return false;
        }

        if (mapLayerMetadataLookup == null) {
            loadMapLayerMetadataLookup(fm.getDatasetVersion().getDataset());
        }

        return this.canSeeMapButtonReminderToPublish(fm);

    }


    /**
     * Call this when using the API
     * - calls private method canSeeMapButtonReminderToPublish
     *
     * @param fm
     * @param user
     * @return
     */
    public boolean canSeeMapButtonReminderToPublishFromAPI(FileMetadata fm, User user) {
        if (fm == null) {
            return false;
        }
        if (user == null) {
            return false;
        }

        if (!this.permissionService.userOn(user, fm.getDataFile().getOwner()).has(Permission.EditDataset)) {
            return false;
        }

        return this.canSeeMapButtonReminderToPublish(fm);

    }


    /**
     * Assumes permissions have been checked!!
     * <p>
     * See table in: https://github.com/IQSS/dataverse/issues/1618
     * <p>
     * Can the user see a reminder to publish button?
     * (1) Is the view GeoconnectViewMaps
     * (2) Is this file a Shapefile or a Tabular file tagged as Geospatial?
     * (3) Is this DataFile released?  Yes, don't need reminder
     * (4) Does a map already exist?  Yes, don't need reminder
     */
    private boolean canSeeMapButtonReminderToPublish(FileMetadata fm) {
        if (fm == null) {
            return false;
        }

        // Is this user authenticated with EditDataset permission?
        //
        if (!(isUserAuthenticatedWithEditDatasetPermission(fm))) {
            return false;
        }

        //  (1) Is the view GeoconnectViewMaps 
        if (!settingsService.isTrueForKey(SettingsServiceBean.Key.GeoconnectCreateEditMaps)) {
            return false;
        }


        // (2) Is this file a Shapefile or a Tabular file tagged as Geospatial?
        //
        if (!(this.isPotentiallyMappableFileType(fm))) {
            return false;
        }

        // (3) Is this DataFile released?  Yes, don't need reminder
        //
        if (fm.getDataFile().isReleased()) {
            return false;
        }

        // (4) Does a map already exist?  Yes, don't need reminder
        //       
        return !this.hasMapLayerMetadata(fm);

        // Looks good
        //
    }

    /**
     * WARNING: Assumes user isAuthenicated AND has Permission.EditDataset
     * - These checks are made on the DatasetPage which calls this method
     */
    public boolean canUserSeeMapDataButtonFromPage(FileMetadata fm) {

        if (fm == null) {
            return false;
        }

        // Is this user authenticated with EditDataset permission?
        //
        if (!(isUserAuthenticatedWithEditDatasetPermission(fm))) {
            return false;
        }
        if (mapLayerMetadataLookup == null) {
            loadMapLayerMetadataLookup(fm.getDatasetVersion().getDataset());
        }
        return this.canUserSeeMapDataButton(fm);
    }


    /**
     * Call this when using the API
     * - calls private method canUserSeeMapDataButton
     *
     * @param fm
     * @param user
     * @return
     */
    public boolean canUserSeeMapDataButtonFromAPI(FileMetadata fm, User user) {
        if (fm == null) {
            return false;
        }
        if (user == null) {
            return false;
        }

        if (!this.permissionService.userOn(user, fm.getDataFile().getOwner()).has(Permission.EditDataset)) {
            return false;
        }

        return this.canUserSeeMapDataButton(fm);

    }

    /**
     * WARNING: Assumes user isAuthenicated AND has Permission.EditDataset
     * - These checks are made on the DatasetPage which calls this method
     * <p>
     * Should there be a Map Data Button for this file?
     * see table in: https://github.com/IQSS/dataverse/issues/1618
     * (1) Is the user logged in?
     * (2) Is this file a Shapefile or a Tabular file tagged as Geospatial?
     * (3) Does the logged in user have permission to edit the Dataset to which this FileMetadata belongs?
     * (4) Is the create Edit Maps flag set to true?
     * (5) Any of these conditions:
     * 9a) File Published
     * (b) Draft: File Previously published
     *
     * @param fm FileMetadata
     * @return boolean
     */
    private boolean canUserSeeMapDataButton(FileMetadata fm) {
        if (fm == null) {
            return false;
        }

        //  (1) Is this file a Shapefile or a Tabular file tagged as Geospatial?
        //  TO DO:  EXPAND FOR TABULAR FILES TAGGED AS GEOSPATIAL!
        //
        if (!(this.isPotentiallyMappableFileType(fm))) {
            return false;
        }


        //  (2) Is the view GeoconnectViewMaps 
        if (!settingsService.isTrueForKey(SettingsServiceBean.Key.GeoconnectCreateEditMaps)) {
            return false;
        }


        // (3) Is File restricted? if Yes - no button.
        if (fm.getTermsOfUse().getTermsOfUseType() == TermsOfUseType.RESTRICTED) {
            return false;
        }


        //  (4) Is File released?
        //
        return fm.getDataFile().isReleased();

        // Nope
    }

    private boolean isUserAuthenticatedWithEditDatasetPermission(FileMetadata fm) {

        // Is the user authenticated?
        //
        if (!(isSessionUserAuthenticated())) {
            return false;
        }

        //  If so, can the logged in user edit the Dataset to which this FileMetadata belongs?
        //
        return this.doesSessionUserHavePermission(Permission.EditDataset, fm);

    }

    public boolean isSessionUserAuthenticated() {


        if (session == null) {
            return false;
        }

        if (session.getUser() == null) {
            return false;
        }

        return session.getUser().isAuthenticated();

    }

    private boolean doesSessionUserHavePermission(Permission permissionToCheck, FileMetadata fileMetadata) {
        if (permissionToCheck == null) {
            return false;
        }

        DvObject objectToCheck = null;

        if (permissionToCheck.equals(Permission.EditDataset)) {
            objectToCheck = fileMetadata.getDatasetVersion().getDataset();
        } else if (permissionToCheck.equals(Permission.DownloadFile)) {
            objectToCheck = fileMetadata.getDataFile();
        }

        if (objectToCheck == null) {
            return false;
        }

        if (this.session.getUser() == null) {
            return false;
        }

        if (this.permissionService == null) {
            return false;
        }

        // Has this check already been done? 
        // 
        if (this.datasetPermissionMap.containsKey(permissionToCheck)) {
            // Yes, return previous answer
            return this.datasetPermissionMap.get(permissionToCheck);
        }

        // Check the permission
        //

        boolean hasPermission = this.permissionService.userOn(this.session.getUser(), objectToCheck).has(permissionToCheck);

        // Save the permission
        this.datasetPermissionMap.put(permissionToCheck, hasPermission);

        // return true/false
        return hasPermission;
    }


}
