package edu.harvard.iq.dataverse.dataset.datasetversion;

import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDatasetVersionCommand;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.Collections;

@Stateless
public class DatasetVersionService {

    private DataverseRequestServiceBean dvRequestService;
    private EjbDataverseEngine commandEngine;

    // -------------------- CONSTRUCTORS --------------------

    @Deprecated
    public DatasetVersionService() {
    }

    @Inject
    public DatasetVersionService(DataverseRequestServiceBean dvRequestService, EjbDataverseEngine commandEngine) {
        this.dvRequestService = dvRequestService;
        this.commandEngine = commandEngine;
    }

    // -------------------- LOGIC --------------------

    public Dataset updateDatasetVersion(Dataset datasetToUpdate, DatasetVersion clonedDatasetVersion, boolean lenientValidation){
        UpdateDatasetVersionCommand updateCommand = new UpdateDatasetVersionCommand(datasetToUpdate, dvRequestService.getDataverseRequest(),
                                                                               Collections.emptyList(), clonedDatasetVersion);
        updateCommand.setValidateLenient(lenientValidation);
        return commandEngine.submit(updateCommand);
    }
}
