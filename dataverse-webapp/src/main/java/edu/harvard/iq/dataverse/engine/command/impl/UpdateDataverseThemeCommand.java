package edu.harvard.iq.dataverse.engine.command.impl;

import edu.harvard.iq.dataverse.engine.command.AbstractCommand;
import edu.harvard.iq.dataverse.engine.command.CommandContext;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.engine.command.RequiredPermissions;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.user.Permission;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Update an existing dataverse.
 *
 * @author michael
 */
@RequiredPermissions(Permission.EditDataverse)
public class UpdateDataverseThemeCommand extends AbstractCommand<Dataverse> {
    private final Dataverse editedDv;
    private final File uploadedFile;
    private final Path logoPath = Paths.get("../docroot/logos");

    public UpdateDataverseThemeCommand(Dataverse editedDv, File uploadedFile, DataverseRequest aRequest) {
        super(aRequest, editedDv);
        this.uploadedFile = uploadedFile;
        this.editedDv = editedDv;

    }

    /**
     * Update Theme and Widget related data for this dataverse, and
     * do file management needed for theme images.
     *
     * @param ctxt
     * @return
     */
    @Override
    public Dataverse execute(CommandContext ctxt) {
        // Get current dataverse, so we can delete current logo file if necessary
        Dataverse currentDv = ctxt.dataverses().find(editedDv.getId());
        File logoFileDir = new File(logoPath.toFile(), editedDv.getId().toString());
        File currentFile = null;
        if (currentDv.getDataverseTheme() != null && currentDv.getDataverseTheme().getLogo() != null) {
            currentFile = new File(logoFileDir, currentDv.getDataverseTheme().getLogo());
        }
        try {
            // If edited logo field is empty, and a logoFile currently exists, delete it
            if (editedDv.getDataverseTheme() == null || editedDv.getDataverseTheme().getLogo() == null) {
                if (currentFile != null) {
                    currentFile.delete();
                }
            } // If edited logo file isn't empty,and uploaded File exists, delete currentFile and copy uploaded file from temp dir to logos dir
            else if (uploadedFile != null) {
                File newFile = new File(logoFileDir, editedDv.getDataverseTheme().getLogo());
                if (currentFile != null) {
                    currentFile.delete();
                }
                logoFileDir.mkdirs();
                Files.copy(uploadedFile.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            // save updated dataverse to db
            return ctxt.dataverses().save(editedDv);

        } catch (IOException e) {
            throw new CommandException("Error saving logo file", e, this); // improve error handling

        }

    }

}
