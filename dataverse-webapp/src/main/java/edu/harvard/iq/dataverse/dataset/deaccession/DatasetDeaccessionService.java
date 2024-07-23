package edu.harvard.iq.dataverse.dataset.deaccession;

import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.annotations.PermissionNeeded;
import edu.harvard.iq.dataverse.annotations.processors.permissions.extractors.DatasetFromVersion;
import edu.harvard.iq.dataverse.engine.command.impl.DeaccessionDatasetVersionCommand;
import edu.harvard.iq.dataverse.interceptors.LoggedCall;
import edu.harvard.iq.dataverse.interceptors.Restricted;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.user.Permission;
import edu.harvard.iq.dataverse.search.index.IndexServiceBean;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;
import java.util.stream.Collectors;

@Stateless
public class DatasetDeaccessionService {

    @PersistenceContext
    private EntityManager em;

    @Inject
    private IndexServiceBean indexService;

    @Inject
    private EjbDataverseEngine commandEngine;

    @Inject
    private DataverseRequestServiceBean dvRequestService;

    // -------------------- LOGIC --------------------

    @LoggedCall
    @Restricted(@PermissionNeeded(needs = {Permission.PublishDataset}))
    public List<DatasetVersion> deaccessVersions(
            @PermissionNeeded(extractor = DatasetFromVersion.class) List<DatasetVersion> versions,
            String deaccessionReason , String deaccessionForwardURLFor) {
        return versions.stream()
                .map(v -> deaccessDatasetVersion(v, deaccessionReason, deaccessionForwardURLFor))
                .collect(Collectors.toList());
    }

    @LoggedCall
    @Restricted(@PermissionNeeded(needs = {Permission.PublishDataset}))
    public List<DatasetVersion> deaccessReleasedVersions(
            @PermissionNeeded(extractor = DatasetFromVersion.class) List<DatasetVersion> versions,
            String deaccessionReason, String deaccessionForwardURLFor) {
        return versions.stream()
                .filter(DatasetVersion::isReleased)
                .map(v -> deaccessDatasetVersion(v, deaccessionReason, deaccessionForwardURLFor))
                .collect(Collectors.toList());
    }

    // -------------------- PRIVATE --------------------

    private DatasetVersion deaccessDatasetVersion(DatasetVersion version, String deaccessionReason, String deaccessionForwardURLFor) {
        return commandEngine.submit(new DeaccessionDatasetVersionCommand(
                dvRequestService.getDataverseRequest(), version, deaccessionReason, deaccessionForwardURLFor));
    }
}
