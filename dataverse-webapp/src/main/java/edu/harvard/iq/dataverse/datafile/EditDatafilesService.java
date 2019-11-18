package edu.harvard.iq.dataverse.datafile;

import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.dataset.DatasetThumbnail;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDatasetThumbnailCommand;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;

import javax.ejb.Stateless;
import javax.inject.Inject;

@Stateless
public class EditDatafilesService {

    private EjbDataverseEngine commandEngine;
    private DataverseRequestServiceBean dvRequestService;

    // -------------------- CONSTRUCTORS --------------------

    @Deprecated
    public EditDatafilesService() {
    }

    @Inject
    public EditDatafilesService(EjbDataverseEngine commandEngine, DataverseRequestServiceBean dvRequestService) {
        this.commandEngine = commandEngine;
        this.dvRequestService = dvRequestService;
    }

    // -------------------- LOGIC --------------------

    /**
     * Replaces default thumbnail with the one provided.
     *
     * @param datasetForNewThumbnail dataset that will have new thumbnail
     * @param datafileThumbnailId id of the thumbnail that will be set for dataset
     */
    public DatasetThumbnail changeDatasetThumbnail(Dataset datasetForNewThumbnail, long datafileThumbnailId) {
        return commandEngine.submit(new UpdateDatasetThumbnailCommand(dvRequestService.getDataverseRequest(),
                                                                      datasetForNewThumbnail,
                                                                      UpdateDatasetThumbnailCommand.UserIntent.setDatasetFileAsThumbnail,
                                                                      datafileThumbnailId,
                                                                      null));

    }
}
