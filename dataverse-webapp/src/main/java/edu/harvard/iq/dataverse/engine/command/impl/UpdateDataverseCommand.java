package edu.harvard.iq.dataverse.engine.command.impl;

import edu.harvard.iq.dataverse.engine.command.AbstractCommand;
import edu.harvard.iq.dataverse.engine.command.CommandContext;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.engine.command.RequiredPermissions;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse.DataverseType;
import edu.harvard.iq.dataverse.persistence.dataverse.DataverseFieldTypeInputLevel;
import edu.harvard.iq.dataverse.persistence.user.Permission;

import java.util.ArrayList;
import java.util.List;

/**
 * Update an existing dataverse.
 *
 * @author michael
 */
@RequiredPermissions(Permission.EditDataverse)
public class UpdateDataverseCommand extends AbstractCommand<Dataverse> {

    private final Dataverse editedDv;
    private final List<DatasetFieldType> facetList;
    private final List<Dataverse> featuredDataverseList;
    private final List<DataverseFieldTypeInputLevel> inputLevelList;

    public UpdateDataverseCommand(Dataverse editedDv, List<DatasetFieldType> facetList, List<Dataverse> featuredDataverseList,
                                  DataverseRequest aRequest, List<DataverseFieldTypeInputLevel> inputLevelList) {
        super(aRequest, editedDv);
        this.editedDv = editedDv;
        // add update template uses this command but does not
        // update facet list or featured dataverses
        if (facetList != null) {
            this.facetList = new ArrayList<>(facetList);
        } else {
            this.facetList = null;
        }
        if (featuredDataverseList != null) {
            this.featuredDataverseList = new ArrayList<>(featuredDataverseList);
        } else {
            this.featuredDataverseList = null;
        }
        if (inputLevelList != null) {
            this.inputLevelList = new ArrayList<>(inputLevelList);
        } else {
            this.inputLevelList = null;
        }
    }

    @Override
    public Dataverse execute(CommandContext ctxt)  {
        DataverseType oldDvType = ctxt.dataverses().find(editedDv.getId()).getDataverseType();
        String oldDvAlias = ctxt.dataverses().find(editedDv.getId()).getAlias();
        String oldDvName = ctxt.dataverses().find(editedDv.getId()).getName();
        Dataverse result = ctxt.dataverses().save(editedDv);

        if (facetList != null) {
            ctxt.facets().deleteFacetsFor(result);
            int i = 0;
            for (DatasetFieldType df : facetList) {
                ctxt.facets().create(i++, df.getId(), result.getId());
            }
        }
        if (featuredDataverseList != null) {
            ctxt.featuredDataverses().deleteFeaturedDataversesFor(result);
            int i = 0;
            for (Dataverse dv : featuredDataverseList) {
                ctxt.featuredDataverses().create(i++, dv.getId(), result.getId());
            }
        }
        if (inputLevelList != null) {
            ctxt.fieldTypeInputLevels().deleteFacetsFor(result);
            for (DataverseFieldTypeInputLevel obj : inputLevelList) {
                ctxt.fieldTypeInputLevels().create(obj);
            }
        }

        ctxt.index().indexDataverse(result);

        List<Dataverse> dvChildrenToUpdate = ctxt.dataverses().findByOwnerId(result.getId());
        dvChildrenToUpdate.forEach(dvForUpdate -> ctxt.index().indexDataverse(dvForUpdate));

        //When these values are changed we need to reindex all children datasets
        //This check is not recursive as all the values just report the immediate parent
        //
        //This runs async to not slow down editing --MAD 4.9.4
        if (!oldDvType.equals(editedDv.getDataverseType())
                || !oldDvName.equals(editedDv.getName())
                || !oldDvAlias.equals(editedDv.getAlias())) {
            List<Dataset> datasets = ctxt.datasets().findByOwnerId(editedDv.getId());
            ctxt.index().asyncIndexDatasetList(datasets, true);
        }

        return result;
    }
}
