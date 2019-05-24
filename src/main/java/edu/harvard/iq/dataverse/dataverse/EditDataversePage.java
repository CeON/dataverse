package edu.harvard.iq.dataverse.dataverse;

import edu.harvard.iq.dataverse.ControlledVocabularyValue;
import edu.harvard.iq.dataverse.ControlledVocabularyValueServiceBean;
import edu.harvard.iq.dataverse.DatasetFieldConstant;
import edu.harvard.iq.dataverse.DatasetFieldServiceBean;
import edu.harvard.iq.dataverse.DatasetFieldType;
import edu.harvard.iq.dataverse.Dataverse;
import edu.harvard.iq.dataverse.DataverseFacet;
import edu.harvard.iq.dataverse.DataverseFacetServiceBean;
import edu.harvard.iq.dataverse.DataverseFieldTypeInputLevel;
import edu.harvard.iq.dataverse.DataverseFieldTypeInputLevelServiceBean;
import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.DataverseServiceBean;
import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.MetadataBlock;
import edu.harvard.iq.dataverse.PermissionServiceBean;
import edu.harvard.iq.dataverse.PermissionsWrapper;
import edu.harvard.iq.dataverse.authorization.Permission;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDataverseCommand;
import edu.harvard.iq.dataverse.util.BundleUtil;
import edu.harvard.iq.dataverse.util.JsfHelper;
import org.apache.commons.lang.StringUtils;
import org.primefaces.PrimeFaces;
import org.primefaces.event.TransferEvent;
import org.primefaces.model.DualListModel;

import javax.ejb.EJB;
import javax.faces.application.FacesMessage;
import javax.faces.component.UIComponent;
import javax.faces.component.UIInput;
import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static edu.harvard.iq.dataverse.util.JsfHelper.JH;

@ViewScoped
@Named("editDataversePage")
public class EditDataversePage implements Serializable {

    private static final Logger logger = Logger.getLogger(EditDataversePage.class.getCanonicalName());

    @EJB
    private DataverseServiceBean dataverseService;

    @EJB
    private DatasetFieldServiceBean datasetFieldService;

    @EJB
    private DataverseFieldTypeInputLevelServiceBean dataverseFieldTypeInputLevelService;

    @EJB
    private PermissionServiceBean permissionService;

    @EJB
    private EjbDataverseEngine commandEngine;

    @Inject
    private PermissionsWrapper permissionsWrapper;

    @Inject
    private ControlledVocabularyValueServiceBean controlledVocabularyValueServiceBean;

    @Inject
    private DataverseFacetServiceBean dataverseFacetService;

    @Inject
    private DataverseSession session;

    @Inject
    private DataverseRequestServiceBean dvRequestService;

    private Long dataverseId;
    private Long ownerId;
    private Dataverse dataverse;
    private boolean openMetadataBlock;
    private boolean editInputLevel;
    private Long facetMetadataBlockId;
    private List<MetadataBlock> allMetadataBlocks;
    private List<ControlledVocabularyValue> dataverseSubjectControlledVocabularyValues;
    private DualListModel<DatasetFieldType> facets = new DualListModel<>(new ArrayList<>(), new ArrayList<>());

    // -------------------- GETTERS --------------------

    public Dataverse getDataverse() {
        return dataverse;
    }

    public Long getDataverseId() {
        return dataverseId;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public Long getFacetMetadataBlockId() {
        return facetMetadataBlockId;
    }

    public List<ControlledVocabularyValue> getDataverseSubjectControlledVocabularyValues() {
        return dataverseSubjectControlledVocabularyValues;
    }

    public DualListModel<DatasetFieldType> getFacets() {
        return facets;
    }

    public List<MetadataBlock> getAllMetadataBlocks() {
        return allMetadataBlocks;
    }

    public boolean isEditInputLevel() {
        return editInputLevel;
    }

    // -------------------- LOGIC --------------------

    public String init() {
        dataverse = dataverseService.find(dataverseId);

        if (!dataverse.isReleased() && !permissionService.on(dataverse).has(Permission.ViewUnpublishedDataverse)) {
            return permissionsWrapper.notAuthorized();
        }

        ownerId = dataverse.getOwner() != null ? dataverse.getOwner().getId() : null;
        setupForGeneralInfoEdit();

        return StringUtils.EMPTY;
    }

    public void validateAlias(FacesContext context, UIComponent toValidate, Object value) {
        if (!StringUtils.isEmpty((String) value)) {
            String alias = (String) value;

            boolean aliasFound = false;
            Dataverse dv = dataverseService.findByAlias(alias);

            if (dv != null && !dv.getId().equals(dataverse.getId())) {
                aliasFound = true;
            }
            if (aliasFound) {
                ((UIInput) toValidate).setValid(false);
                FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR, BundleUtil.getStringFromBundle("dataverse.alias"), BundleUtil.getStringFromBundle("dataverse.alias.taken"));
                context.addMessage(toValidate.getClientId(context), message);
            }
        }
    }

    public void showDatasetFieldTypes(Long mdbId) {
        showDatasetFieldTypes(mdbId, true);
    }

    public void showDatasetFieldTypes(Long mdbId, boolean allowEdit) {
        for (MetadataBlock mdb : allMetadataBlocks) {
            if (mdb.getId().equals(mdbId)) {
                mdb.setShowDatasetFieldTypes(true);
            }
        }
        setEditInputLevel(allowEdit);
    }

    public void hideDatasetFieldTypes(Long mdbId) {
        for (MetadataBlock mdb : allMetadataBlocks) {
            if (mdb.getId().equals(mdbId)) {
                mdb.setShowDatasetFieldTypes(false);
            }
        }
        setEditInputLevel(false);
    }

    public void editMetadataBlocks() {
        if (!dataverse.isMetadataBlockRoot()) {
            refreshAllMetadataBlocks();
        }
    }

    public void editMetadataBlocks(boolean checkVal) {
        setInheritMetadataBlockFromParent(checkVal);
        if (!dataverse.isMetadataBlockRoot()) {
            refreshAllMetadataBlocks();
        }
    }

    public String resetToInherit() {

        setInheritMetadataBlockFromParent(true);
        return saveEditedDataverse();
    }

    public boolean isInheritMetadataBlockFromParent() {
        return !dataverse.isMetadataBlockRoot();
    }

    public boolean isInheritFacetFromParent() {
        return !dataverse.isFacetRoot();
    }

    public void toggleFacetRoot() {
        if (!dataverse.isFacetRoot()) {
            initFacets();
        }
    }

    public boolean isUserCanChangeAllowMessageAndBanners() {
        return session.getUser().isSuperuser();
    }

    public String saveEditedDataverse() {
        List<DataverseFieldTypeInputLevel> listDFTIL = new ArrayList<>();

        if (dataverse.isMetadataBlockRoot()) {
            dataverse.getOwnersMetadataBlocks().clear();

            List<MetadataBlock> selectedMetadataBlocks = getSelectedMetadataBlocks();
            dataverse.setMetadataBlocks(selectedMetadataBlocks);
            listDFTIL = getSelectedMetadataFields(selectedMetadataBlocks);
        }

        if (!dataverse.isFacetRoot()) {
            facets.getTarget().clear();
        }

        UpdateDataverseCommand cmd = new UpdateDataverseCommand(dataverse, facets.getTarget(), null, dvRequestService.getDataverseRequest(), listDFTIL);

        try {
            dataverse = commandEngine.submit(cmd);

            JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("dataverse.update.success"));
        } catch (CommandException ex) {
            logger.log(Level.SEVERE, "Unexpected Exception calling dataverse command", ex);
            String errMsg = BundleUtil.getStringFromBundle("dataverse.update.failure");
            JH.addMessage(FacesMessage.SEVERITY_FATAL, errMsg);
            return StringUtils.EMPTY;
        }

        return returnRedirect();
    }

    public void refresh() {

    }

    public void changeFacetsMetadataBlock() {
        if (facetMetadataBlockId == null) {
            facets.setSource(datasetFieldService.findAllFacetableFieldTypes());
        } else {
            facets.setSource(datasetFieldService.findFacetableFieldTypesByMetadataBlock(facetMetadataBlockId));
        }

        facets.getSource().removeAll(facets.getTarget());
    }

    public void onFacetTransfer(TransferEvent event) {
        for (Object item : event.getItems()) {
            DatasetFieldType facet = (DatasetFieldType) item;
            if (facetMetadataBlockId != null && !facetMetadataBlockId.equals(facet.getMetadataBlock().getId())) {
                facets.getSource().remove(facet);
            }
        }
    }

    public void updateInclude(Long mdbId, long dsftId) {
        List<DatasetFieldType> childDSFT = new ArrayList<>();

        for (MetadataBlock mdb : allMetadataBlocks) {
            if (mdb.getId().equals(mdbId)) {
                for (DatasetFieldType dsftTest : mdb.getDatasetFieldTypes()) {
                    if (dsftTest.getId().equals(dsftId)) {
                        dsftTest.setOptionSelectItems(resetSelectItems(dsftTest));
                        if ((dsftTest.isHasParent() && !dsftTest.getParentDatasetFieldType().isInclude()) || (!dsftTest.isHasParent() && !dsftTest.isInclude())) {
                            dsftTest.setRequiredDV(false);
                        }
                        if (dsftTest.isHasChildren()) {
                            childDSFT.addAll(dsftTest.getChildDatasetFieldTypes());
                        }
                    }
                }
            }
        }
        if (!childDSFT.isEmpty()) {
            for (DatasetFieldType dsftUpdate : childDSFT) {
                for (MetadataBlock mdb : allMetadataBlocks) {
                    if (mdb.getId().equals(mdbId)) {
                        for (DatasetFieldType dsftTest : mdb.getDatasetFieldTypes()) {
                            if (dsftTest.getId().equals(dsftUpdate.getId())) {
                                dsftTest.setOptionSelectItems(resetSelectItems(dsftTest));
                            }
                        }
                    }
                }
            }
        }
        PrimeFaces.current().executeScript("scrollAfterUpdate();");
    }

    public String returnRedirect() {
        return "/dataverse.xhtml?alias=" + dataverse.getAlias() + "&faces-redirect=true";
    }

    // -------------------- SETTERS --------------------

    public void setDataverse(Dataverse dataverse) {
        this.dataverse = dataverse;
    }

    public void setDataverseId(Long dataverseId) {
        this.dataverseId = dataverseId;
    }

    public void setFacetMetadataBlockId(Long facetMetadataBlockId) {
        this.facetMetadataBlockId = facetMetadataBlockId;
    }

    public void setDataverseSubjectControlledVocabularyValues(List<ControlledVocabularyValue> dataverseSubjectControlledVocabularyValues) {
        this.dataverseSubjectControlledVocabularyValues = dataverseSubjectControlledVocabularyValues;
    }

    public void setAllMetadataBlocks(List<MetadataBlock> allMetadataBlocks) {
        this.allMetadataBlocks = allMetadataBlocks;
    }

    public void setEditInputLevel(boolean editInputLevel) {
        this.editInputLevel = editInputLevel;
    }

    public void setInheritMetadataBlockFromParent(boolean inheritMetadataBlockFromParent) {
        dataverse.setMetadataBlockRoot(!inheritMetadataBlockFromParent);
    }

    public void setFacets(DualListModel<DatasetFieldType> facets) {
        this.facets = facets;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public void setInheritFacetFromParent(boolean inheritFacetFromParent) {
        dataverse.setFacetRoot(!inheritFacetFromParent);
    }

    // -------------------- PRIVATE --------------------

    private List<MetadataBlock> getSelectedMetadataBlocks() {
        List<MetadataBlock> selectedBlocks = new ArrayList<>();

        for (MetadataBlock mdb : this.allMetadataBlocks) {
            if (dataverse.isMetadataBlockRoot() && (mdb.isSelected() || mdb.isCitationMetaBlock())) {
                selectedBlocks.add(mdb);
            }
        }

        return selectedBlocks;
    }

    private List<DataverseFieldTypeInputLevel> getSelectedMetadataFields(List<MetadataBlock> selectedMetadataBlocks) {
        List<DataverseFieldTypeInputLevel> listDFTIL = new ArrayList<>();

        for (MetadataBlock selectedMetadataBlock : selectedMetadataBlocks) {
            for (DatasetFieldType dsft : selectedMetadataBlock.getDatasetFieldTypes()) {

                if (isDatasetFieldChildOrParentRequired(dsft)) {
                    listDFTIL.add(createDataverseFieldTypeInputLevel(dsft, dataverse, true, true));
                }

                if (isDatasetFieldChildOrParentNotIncluded(dsft)) {
                    listDFTIL.add(createDataverseFieldTypeInputLevel(dsft, dataverse, false, false));
                }
            }

        }

        return listDFTIL;
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

    private boolean isDatasetFieldChildOrParentNotIncluded(DatasetFieldType dsft) {
        return (!dsft.isHasParent() && !dsft.isInclude())
                || (dsft.isHasParent() && !dsft.getParentDatasetFieldType().isInclude());
    }

    private boolean isDatasetFieldChildOrParentRequired(DatasetFieldType dsft) {
        return dsft.isRequiredDV() && !dsft.isRequired()
                && ((!dsft.isHasParent() && dsft.isInclude())
                || (dsft.isHasParent() && dsft.getParentDatasetFieldType().isInclude()));
    }

    private void setupForGeneralInfoEdit() {
        updateDataverseSubjectSelectItems();
        initFacets();
        refreshAllMetadataBlocks();
    }

    private void updateDataverseSubjectSelectItems() {
        DatasetFieldType subjectDatasetField = datasetFieldService.findByName(DatasetFieldConstant.subject);
        setDataverseSubjectControlledVocabularyValues(controlledVocabularyValueServiceBean.findByDatasetFieldTypeId(subjectDatasetField.getId()));
    }

    private void initFacets() {
        List<DatasetFieldType> facetsTarget = new ArrayList<>();
        List<DatasetFieldType> facetsSource = new ArrayList<>(datasetFieldService.findAllFacetableFieldTypes());
        List<DataverseFacet> facetsList = dataverseFacetService.findByDataverseId(dataverse.getFacetRootId());
        for (DataverseFacet dvFacet : facetsList) {
            DatasetFieldType dsfType = dvFacet.getDatasetFieldType();
            facetsTarget.add(dsfType);
            facetsSource.remove(dsfType);
        }
        facets = new DualListModel<>(facetsSource, facetsTarget);
        facetMetadataBlockId = null;
    }

    private void refreshAllMetadataBlocks() {
        Long dataverseIdForInputLevel = dataverse.getId();
        List<MetadataBlock> retList = new ArrayList<>();

        //Add System level blocks
        List<MetadataBlock> availableBlocks = new ArrayList<>(dataverseService.findSystemMetadataBlocks());

        Dataverse testDV = dataverse;
        //Add blocks associated with DV
        availableBlocks.addAll(dataverseService.findMetadataBlocksByDataverseId(dataverse.getId()));

        //Add blocks associated with dv going up inheritance tree
        while (testDV.getOwner() != null) {
            availableBlocks.addAll(dataverseService.findMetadataBlocksByDataverseId(testDV.getOwner().getId()));
            testDV = testDV.getOwner();
        }

        for (MetadataBlock mdb : availableBlocks) {
            mdb.setSelected(false);
            mdb.setShowDatasetFieldTypes(false);
            if (!dataverse.isMetadataBlockRoot() && dataverse.getOwner() != null) {
                dataverseIdForInputLevel = dataverse.getMetadataRootId();
                for (MetadataBlock mdbTest : dataverse.getOwner().getOwnersMetadataBlocks()) {
                    if (mdb.equals(mdbTest)) {
                        mdb.setSelected(true);
                    }
                }
            } else {
                for (MetadataBlock mdbTest : dataverse.getMetadataBlocks()) {
                    if (mdb.equals(mdbTest)) {
                        mdb.setSelected(true);
                    }
                }
            }

            for (DatasetFieldType dsft : mdb.getDatasetFieldTypes()) {
                if (!dsft.isChild()) {
                    DataverseFieldTypeInputLevel dsfIl = dataverseFieldTypeInputLevelService.findByDataverseIdDatasetFieldTypeId(dataverseIdForInputLevel, dsft.getId());
                    if (dsfIl != null) {
                        dsft.setRequiredDV(dsfIl.isRequired());
                        dsft.setInclude(dsfIl.isInclude());
                    } else {
                        dsft.setRequiredDV(dsft.isRequired());
                        dsft.setInclude(true);
                    }
                    dsft.setOptionSelectItems(resetSelectItems(dsft));
                    if (dsft.isHasChildren()) {
                        for (DatasetFieldType child : dsft.getChildDatasetFieldTypes()) {
                            DataverseFieldTypeInputLevel dsfIlChild = dataverseFieldTypeInputLevelService.findByDataverseIdDatasetFieldTypeId(dataverseIdForInputLevel, child.getId());
                            if (dsfIlChild != null) {
                                child.setRequiredDV(dsfIlChild.isRequired());
                                child.setInclude(dsfIlChild.isInclude());
                            } else {
                                child.setRequiredDV(child.isRequired());
                                child.setInclude(true);
                            }
                            child.setOptionSelectItems(resetSelectItems(child));
                        }
                    }
                }
            }
            retList.add(mdb);
        }
        setAllMetadataBlocks(retList);
    }

    private List<SelectItem> resetSelectItems(DatasetFieldType typeIn) {
        List<SelectItem> retList = new ArrayList<>();
        if ((typeIn.isHasParent() && typeIn.getParentDatasetFieldType().isInclude()) || (!typeIn.isHasParent() && typeIn.isInclude())) {
            SelectItem requiredItem = new SelectItem();
            requiredItem.setLabel(BundleUtil.getStringFromBundle("dataverse.item.required"));
            requiredItem.setValue(true);
            retList.add(requiredItem);
            SelectItem optional = new SelectItem();
            optional.setLabel(BundleUtil.getStringFromBundle("dataverse.item.optional"));
            optional.setValue(false);
            retList.add(optional);
        } else {
            SelectItem hidden = new SelectItem();
            hidden.setLabel(BundleUtil.getStringFromBundle("dataverse.item.hidden"));
            hidden.setValue(false);
            hidden.setDisabled(true);
            retList.add(hidden);
        }
        return retList;
    }
}
