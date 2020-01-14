package edu.harvard.iq.dataverse.dataset.deaccession;

import edu.harvard.iq.dataverse.annotations.PermissionNeeded;
import edu.harvard.iq.dataverse.interceptors.LoggedCall;
import edu.harvard.iq.dataverse.interceptors.Restricted;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.user.Permission;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

@Stateless
public class DatasetDeaccessionService {

    DatasetDeaccessionBean deaccessDataset;

    // -------------------- CONSTRUCTORS --------------------

    @Deprecated
    public DatasetDeaccessionService() { }

    @Inject
    public DatasetDeaccessionService(DatasetDeaccessionBean deaccessDataset) {
        this.deaccessDataset = deaccessDataset;
    }

    // -------------------- LOGIC --------------------
    
    @LoggedCall
    @Restricted(@PermissionNeeded(needs = {Permission.PublishDataset}))
    public List<DatasetVersion> deaccessVersions(@PermissionNeeded Dataset dataset, List<DatasetVersion> versions,
                                                 String deaccessionReason , String deaccessionForwardURLFor) {
        return versions.stream()
                .map(v -> deaccessDataset.deaccessDatasetVersion(dataset, v, deaccessionReason, deaccessionForwardURLFor))
                .collect(Collectors.toList());
    }

    @LoggedCall
    @Restricted(@PermissionNeeded(needs = {Permission.PublishDataset}))
    public List<DatasetVersion> deaccessReleasedVersions(@PermissionNeeded Dataset dataset, List<DatasetVersion> versions,
                                                         String deaccessionReason ,String deaccessionForwardURLFor) {
        return versions.stream()
                .filter(DatasetVersion::isReleased)
                .map(v -> deaccessDataset.deaccessDatasetVersion(dataset, v, deaccessionReason, deaccessionForwardURLFor))
                .collect(Collectors.toList());
    }
}
