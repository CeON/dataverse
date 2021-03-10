package edu.harvard.iq.dataverse.engine.command.impl;

import edu.harvard.iq.dataverse.dataaccess.StorageIO;
import edu.harvard.iq.dataverse.engine.command.AbstractCommand;
import edu.harvard.iq.dataverse.engine.command.CommandContext;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.engine.command.RequiredPermissions;
import edu.harvard.iq.dataverse.engine.command.exception.IllegalCommandException;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.user.Permission;
import org.apache.commons.io.IOUtils;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

@RequiredPermissions(Permission.EditDataset)
public class GetProvJsonCommand extends AbstractCommand<JsonObject> {

    private static final Logger logger = Logger.getLogger(GetProvJsonCommand.class.getCanonicalName());

    private final DataFile dataFile;

    public GetProvJsonCommand(DataverseRequest aRequest, DataFile dataFile) {
        super(aRequest, dataFile);
        this.dataFile = dataFile;
    }

    @Override
    public JsonObject execute(CommandContext ctxt)  {

        final String provJsonExtension = "prov-json.json";
        InputStream inputStream = null;

        try {
            StorageIO<DataFile> storageIO = ctxt.dataAccess().getStorageIO(dataFile);
            inputStream = storageIO.getAuxFileAsInputStream(provJsonExtension);
            JsonObject jsonObject = null;
            if (null != inputStream) {
                JsonReader jsonReader = Json.createReader(inputStream);
                jsonObject = jsonReader.readObject();
            }
            return jsonObject;
        } catch (IOException ex) {
            String error = "Exception caught in DataAccess.getStorageIO(dataFile) getting file. Error: " + ex;
            throw new IllegalCommandException(error, this);
        } finally {
            IOUtils.closeQuietly(inputStream);
        }
    }
}
