package edu.harvard.iq.dataverse.dataverse;

import com.google.common.collect.Lists;
import edu.harvard.iq.dataverse.DatasetFieldType;
import edu.harvard.iq.dataverse.Dataverse;
import edu.harvard.iq.dataverse.DataverseFieldTypeInputLevel;
import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.DataverseServiceBean;
import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.MetadataBlock;
import edu.harvard.iq.dataverse.UserNotification;
import edu.harvard.iq.dataverse.UserNotificationServiceBean;
import edu.harvard.iq.dataverse.authorization.users.AuthenticatedUser;
import edu.harvard.iq.dataverse.engine.command.Command;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.engine.command.impl.CreateDataverseCommand;
import edu.harvard.iq.dataverse.error.DataverseError;
import edu.harvard.iq.dataverse.settings.SettingsWrapper;
import edu.harvard.iq.dataverse.util.BundleUtil;
import edu.harvard.iq.dataverse.util.JsfHelper;
import edu.harvard.iq.dataverse.util.SystemConfig;
import io.vavr.control.Either;
import org.primefaces.model.DualListModel;

import javax.ejb.Stateless;
import javax.faces.application.FacesMessage;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static edu.harvard.iq.dataverse.util.JsfHelper.JH;

@Stateless
public class MetadataBlockService {

    private static final Logger logger = Logger.getLogger(MetadataBlockService.class.getCanonicalName());

    @Inject
    private DataverseSession session;

    @Inject
    private DataverseRequestServiceBean dvRequestService;

    @Inject
    private DataverseServiceBean dataverseService;

    @Inject
    private EjbDataverseEngine commandEngine;

    @Inject
    private UserNotificationServiceBean userNotificationService;

    @Inject
    private SettingsWrapper settingsWrapper;

    @Inject
    private SystemConfig systemConfig;

    // -------------------- LOGIC --------------------

    public Either<DataverseError, Dataverse> saveNewDataverse(Collection<DataverseFieldTypeInputLevel> dftilToBeSaved,
                                                              Dataverse dataverse,
                                                              DualListModel<DatasetFieldType> facets) {

        if (!dataverse.isFacetRoot()) {
            facets.getTarget().clear();
        }

        if (session.getUser().isAuthenticated()) {
            dataverse.setOwner(dataverse.getOwner().getId() != null ? dataverseService.find(dataverse.getOwner().getId()) : null);
            Command<Dataverse> cmd = new CreateDataverseCommand(dataverse,
                    dvRequestService.getDataverseRequest(),
                    facets.getTarget(),
                    Lists.newArrayList(dftilToBeSaved));

            try {
                dataverse = commandEngine.submit(cmd);
            } catch (CommandException ex) {
                logger.log(Level.SEVERE, "Unexpected Exception calling dataverse command", ex);
                JH.addMessage(FacesMessage.SEVERITY_FATAL, BundleUtil.getStringFromBundle("dataverse.create.failure"));
                return Either.left(new DataverseError(ex, BundleUtil.getStringFromBundle("dataverse.create.failure")));
            }

            sendSuccessNotification(dataverse);
            showSuccessMessage();
        } else {
            JH.addMessage(FacesMessage.SEVERITY_FATAL, BundleUtil.getStringFromBundle("dataverse.create.authenticatedUsersOnly"));
            return Either.left(new DataverseError(BundleUtil.getStringFromBundle("dataverse.create.authenticatedUsersOnly")));
        }

        return Either.right(dataverse);
    }

    public List<DataverseFieldTypeInputLevel> getDataverseFieldTypeInputLevelsToBeSaved(Collection<MetadataBlock> metadataBlocks,
                                                                                        DataverseMetaBlockOptions mdbOptions,
                                                                                        Dataverse dataverse) {
        List<DataverseFieldTypeInputLevel> listDFTIL = new ArrayList<>();

        if (!mdbOptions.isInheritMetaBlocksFromParent()) {
            dataverse.getOwnersMetadataBlocks().clear();

            List<MetadataBlock> selectedMetadataBlocks = getSelectedMetadataBlocks(metadataBlocks, mdbOptions);
            dataverse.setMetadataBlocks(selectedMetadataBlocks);
            listDFTIL = getSelectedMetadataFields(selectedMetadataBlocks, dataverse, mdbOptions);
        }

        return listDFTIL;
    }

    public MetadataBlockViewOptions prepareDatasetFieldsToBeEditable(DataverseMetaBlockOptions dataverseMetaBlockOptions, Long metadataBlockId) {
        return dataverseMetaBlockOptions.getMdbViewOptions().put(metadataBlockId,
                MetadataBlockViewOptions.newBuilder()
                        .showDatasetFieldTypes(true)
                        .editableDatasetFieldTypes(true)
                        .selected(true)
                        .build());
    }

    public MetadataBlockViewOptions prepareDatasetFieldsToBeUnEditable(DataverseMetaBlockOptions dataverseMetaBlockOptions, Long metadataBlockId) {
        return dataverseMetaBlockOptions.getMdbViewOptions().put(metadataBlockId,
                MetadataBlockViewOptions.newBuilder()
                        .showDatasetFieldTypes(true)
                        .editableDatasetFieldTypes(false)
                        .selected(dataverseMetaBlockOptions.isMetaBlockSelected(metadataBlockId))
                        .build());
    }

    public MetadataBlockViewOptions prepareDatasetFieldsToBeHidden(DataverseMetaBlockOptions dataverseMetaBlockOptions, Long metadataBlockId) {
        return dataverseMetaBlockOptions.getMdbViewOptions().put(metadataBlockId,
                MetadataBlockViewOptions.newBuilder()
                        .showDatasetFieldTypes(false)
                        .selected(dataverseMetaBlockOptions.isMetaBlockSelected(metadataBlockId))
                        .build());
    }

    // -------------------- PRIVATE --------------------

    private List<DataverseFieldTypeInputLevel> getSelectedMetadataFields(List<MetadataBlock> selectedMetadataBlocks, Dataverse dataverse, DataverseMetaBlockOptions mdbOptions) {
        List<DataverseFieldTypeInputLevel> listDFTIL = new ArrayList<>();

        for (MetadataBlock selectedMetadataBlock : selectedMetadataBlocks) {
            for (DatasetFieldType dsft : selectedMetadataBlock.getDatasetFieldTypes()) {

                if (isDatasetFieldChildOrParentRequired(dsft, mdbOptions)) {
                    listDFTIL.add(createDataverseFieldTypeInputLevel(dsft, dataverse, true, true));
                }

                if (isDatasetFieldChildOrParentNotIncluded(dsft, mdbOptions)) {
                    listDFTIL.add(createDataverseFieldTypeInputLevel(dsft, dataverse, false, false));
                }
            }

        }

        return listDFTIL;
    }

    private void showSuccessMessage() {
        JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("dataverse.create.success",
                Arrays.asList(settingsWrapper.getGuidesBaseUrl(), systemConfig.getGuidesVersion())));
    }

    private void sendSuccessNotification(Dataverse dataverse) {
        userNotificationService.sendNotification((AuthenticatedUser) session.getUser(), dataverse.getCreateDate(),
                UserNotification.Type.CREATEDV, dataverse.getId());
    }

    private boolean isDatasetFieldChildOrParentNotIncluded(DatasetFieldType dsft, DataverseMetaBlockOptions mdbOptions) {
        return (!dsft.isHasParent() && !mdbOptions.isDsftIncludedField(dsft.getId()))
                || (dsft.isHasParent() && !mdbOptions.isDsftIncludedField(dsft.getParentDatasetFieldType().getId()));
    }

    private boolean isDatasetFieldChildOrParentRequired(DatasetFieldType dsft, DataverseMetaBlockOptions mdbOptions) {
        DatasetFieldViewOptions dsftViewOptions = mdbOptions.getDatasetFieldViewOptions().get(dsft.getId());

        DatasetFieldViewOptions parentDsftViewOptions = null;

        if (dsft.isHasParent()) {
            parentDsftViewOptions = mdbOptions.getDatasetFieldViewOptions().get(dsft.getParentDatasetFieldType().getId());
        }

        boolean isParentIncluded = parentDsftViewOptions != null && parentDsftViewOptions.isIncluded();

        return dsftViewOptions.isRequiredField() && !dsft.isRequired()
                && ((!dsft.isHasParent() && dsftViewOptions.isIncluded())
                || (dsft.isHasParent() && isParentIncluded));
    }

    private DataverseFieldTypeInputLevel createDataverseFieldTypeInputLevel(DatasetFieldType dsft,
                                                                            Dataverse dataverse,
                                                                            boolean isRequired,
                                                                            boolean isIncluded) {
        DataverseFieldTypeInputLevel dftil = new DataverseFieldTypeInputLevel();
        dftil.setDatasetFieldType(dsft);
        dftil.setDataverse(dataverse);
        dftil.setRequired(isRequired);
        dftil.setInclude(isIncluded);
        return dftil;
    }

    private List<MetadataBlock> getSelectedMetadataBlocks(Collection<MetadataBlock> allMetadataBlocks, DataverseMetaBlockOptions mdbOptions) {
        List<MetadataBlock> selectedBlocks = new ArrayList<>();

        for (MetadataBlock mdb : allMetadataBlocks) {
            if (!mdbOptions.isInheritMetaBlocksFromParent() && (mdbOptions.isMetaBlockSelected(mdb.getId()) || mdb.isCitationMetaBlock())) {
                selectedBlocks.add(mdb);
            }
        }

        return selectedBlocks;
    }

}
