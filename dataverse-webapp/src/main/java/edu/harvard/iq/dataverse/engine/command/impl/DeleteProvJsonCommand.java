package edu.harvard.iq.dataverse.engine.command.impl;

import edu.harvard.iq.dataverse.dataaccess.DataAccess;
import edu.harvard.iq.dataverse.dataaccess.StorageIO;
import edu.harvard.iq.dataverse.engine.command.AbstractCommand;
import edu.harvard.iq.dataverse.engine.command.CommandContext;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.engine.command.RequiredPermissions;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.engine.command.exception.IllegalCommandException;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.user.Permission;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.logging.Logger;

@RequiredPermissions(Permission.EditDataset)
public class DeleteProvJsonCommand extends AbstractCommand<DataFile> {

    private static final Logger logger = Logger.getLogger(DeleteProvJsonCommand.class.getCanonicalName());

    private DataFile dataFile;
    private final boolean saveContext;

    public DeleteProvJsonCommand(DataverseRequest aRequest, DataFile dataFile, boolean saveContext) {
        super(aRequest, dataFile);
        this.dataFile = dataFile;
        this.saveContext = saveContext;
    }

    @Override
    public DataFile execute(CommandContext ctxt)  {

        final String provJsonExtension = "prov-json.json";

        try {
            StorageIO<DataFile> dataAccess = ctxt.dataAccess().getStorageIO(dataFile);
            dataAccess.deleteAuxObject(provJsonExtension);
            logger.info("provenance json delete passed io step");
        } catch (NoSuchFileException nf) {
            //if this command is called and there is no file, we keep going
        } catch (IOException ex) {
            String error = "Exception caught deleting provenance aux object: " + ex;
            throw new IllegalCommandException(error, this);
        }

        dataFile.setProvEntityName("");
        if (saveContext) {
            dataFile = ctxt.files().save(dataFile);
        }
        return dataFile;
    }

}
