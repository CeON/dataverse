package edu.harvard.iq.dataverse.dataverse;

import edu.harvard.iq.dataverse.ControlledVocabularyValue;
import edu.harvard.iq.dataverse.ControlledVocabularyValueServiceBean;
import edu.harvard.iq.dataverse.DatasetFieldConstant;
import edu.harvard.iq.dataverse.DatasetFieldServiceBean;
import edu.harvard.iq.dataverse.DatasetFieldType;
import edu.harvard.iq.dataverse.Dataverse;
import edu.harvard.iq.dataverse.DataverseContact;
import edu.harvard.iq.dataverse.DataverseFacet;
import edu.harvard.iq.dataverse.DataverseFacetServiceBean;
import edu.harvard.iq.dataverse.DataverseFieldTypeInputLevel;
import edu.harvard.iq.dataverse.DataverseServiceBean;
import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.MetadataBlock;
import edu.harvard.iq.dataverse.PermissionServiceBean;
import edu.harvard.iq.dataverse.PermissionsWrapper;
import edu.harvard.iq.dataverse.authorization.Permission;
import edu.harvard.iq.dataverse.bannersandmessages.DataverseUtil;
import edu.harvard.iq.dataverse.error.DataverseError;
import edu.harvard.iq.dataverse.util.BundleUtil;
import io.vavr.control.Either;
import org.apache.commons.lang.StringUtils;
import org.primefaces.event.TransferEvent;
import org.primefaces.model.DualListModel;

import javax.ejb.EJB;
import javax.faces.application.FacesMessage;
import javax.faces.component.UIComponent;
import javax.faces.component.UIInput;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

@ViewScoped
@Named("CreateEditDataversePage")
public class CreateEditDataversePage implements Serializable {

    private static final Logger logger = Logger.getLogger(CreateEditDataversePage.class.getCanonicalName());

    @EJB
    private DataverseServiceBean dataverseService;

    @EJB
    private DatasetFieldServiceBean datasetFieldService;

    @EJB
    private PermissionServiceBean permissionService;

    @EJB
    private MetadataBlockService metadataBlockService;

    @Inject
    private PermissionsWrapper permissionsWrapper;

    @Inject
    private ControlledVocabularyValueServiceBean controlledVocabularyValueServiceBean;

    @Inject
    private DataverseFacetServiceBean dataverseFacetService;

    @Inject
    private DataverseSession session;

    private Long dataverseId;
    private Long ownerId;
    private String dataverseOwnerAlias;

    private Dataverse dataverse;
    private Long facetMetadataBlockId;
    private Set<MetadataBlock> allMetadataBlocks;
    private List<ControlledVocabularyValue> dataverseSubjectControlledVocabularyValues;
    private DualListModel<DatasetFieldType> facets = new DualListModel<>(new ArrayList<>(), new ArrayList<>());

    private DataverseMetaBlockOptions mdbOptions = new DataverseMetaBlockOptions();
    private DataverseMetaBlockOptions defaultMdbOptions;

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

    public DualListModel<DatasetFieldType> getFacets() {
        return facets;
    }

    public Set<MetadataBlock> getAllMetadataBlocks() {
        return allMetadataBlocks;
    }

    public DataverseMetaBlockOptions getMdbOptions() {
        return mdbOptions;
    }

    public String getDataverseOwnerAlias() {
        return dataverseOwnerAlias;
    }

    // -------------------- LOGIC --------------------

    public String init() {

        if (dataverseId == null) {
            return setupViewForDataverseCreation();
        }

        return setupViewForDataverseEdit();
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

    /**
     * Changes metadata block view options in order to show editable dataset fields for given metadata block.
     */
    public void showEditableDatasetFieldTypes(Long mdbId) {
        for (MetadataBlock mdb : allMetadataBlocks) {
            if (mdb.getId().equals(mdbId)) {
                metadataBlockService.prepareDatasetFieldsToBeEditable(mdbOptions, mdbId);
                break;
            }
        }
    }

    /**
     * Changes metadata block view options in order to show uneditable dataset fields for given metadata block.
     */
    public void showUnEditableDatasetFieldTypes(Long mdbId) {
        for (MetadataBlock mdb : allMetadataBlocks) {
            if (mdb.getId().equals(mdbId)) {
                metadataBlockService.prepareDatasetFieldsToBeUnEditable(mdbOptions, mdbId);
                break;
            }
        }
    }

    /**
     * Changes metadata block view options in order to hide all dataset fields for given metadata block.
     */
    public void hideDatasetFieldTypes(Long mdbId) {
        for (MetadataBlock mdb : allMetadataBlocks) {
            if (mdb.getId().equals(mdbId)) {
                metadataBlockService.prepareDatasetFieldsToBeHidden(mdbOptions, mdbId);
                break;
            }
        }
    }

    /**
     * Resets all view options for metadata blocks, dataset fields and sets metadata blocks to be inherited from parent.
     */
    public String resetToInherit() {
        mdbOptions = defaultMdbOptions.deepCopy();
        dataverse.setMetadataBlockRoot(false);

        return StringUtils.EMPTY;
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

    public String save() {
        List<DataverseFieldTypeInputLevel> dftilForSave =
                metadataBlockService.getDataverseFieldTypeInputLevelsToBeSaved(allMetadataBlocks, mdbOptions, dataverse);

        if (dataverse.getId() == null) {
            return saveNewDataverse(dftilForSave);
        }

        return saveEditedDataverse(dftilForSave);
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

    /**
     * Refreshes dataset fields view options for given dataset field.
     */
    public void refreshDatasetFieldsViewOptions(Long mdbId, long dsftId) {
        metadataBlockService.refreshDatasetFieldsViewOptions(mdbId, dsftId, allMetadataBlocks, mdbOptions);
    }

    /**
     * Makes metadata block selectable/editable since it is no longer inherited from parent dataverse.
     */
    public void makeMetadataBlocksSelectable() {
        mdbOptions.setInheritMetaBlocksFromParent(false);
        dataverse.setMetadataBlockRoot(true);
    }

    /**
     * Returns to dataverse view.
     */
    public String cancel() {
        return returnRedirect();
    }

    public void onFacetTransfer(TransferEvent event) {
        for (Object item : event.getItems()) {
            DatasetFieldType facet = (DatasetFieldType) item;
            if (facetMetadataBlockId != null && !facetMetadataBlockId.equals(facet.getMetadataBlock().getId())) {
                facets.getSource().remove(facet);
            }
        }
    }

    public String returnRedirect() {
        return dataverse.getId() == null ? "/dataverse.xhtml?alias=" + dataverseOwnerAlias + "&faces-redirect=true" :
                "/dataverse.xhtml?alias=" + dataverse.getAlias() + "&faces-redirect=true";
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

    public void setAllMetadataBlocks(Set<MetadataBlock> allMetadataBlocks) {
        this.allMetadataBlocks = allMetadataBlocks;
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

    public void setDataverseOwnerAlias(String dataverseOwnerAlias) {
        this.dataverseOwnerAlias = dataverseOwnerAlias;
    }

    // -------------------- PRIVATE --------------------

    private String saveEditedDataverse(List<DataverseFieldTypeInputLevel> dftilForSave) {
        Either<DataverseError, Dataverse> editResult = metadataBlockService.saveEditedDataverse(dftilForSave, dataverse, facets);
        return editResult.isLeft() ? StringUtils.EMPTY : returnRedirect();
    }

    private String saveNewDataverse(List<DataverseFieldTypeInputLevel> dftilForSave) {
        Either<DataverseError, Dataverse> saveResult = metadataBlockService.saveNewDataverse(dftilForSave, dataverse, facets);
        return saveResult.isLeft() ? StringUtils.EMPTY : "/dataverse.xhtml?alias=" + saveResult.get().getAlias() + "&faces-redirect=true";
    }

    private String setupViewForDataverseEdit() {
        dataverse = dataverseService.find(dataverseId);
        if (!dataverse.isReleased() && !permissionService.on(dataverse).has(Permission.ViewUnpublishedDataverse)) {
            return permissionsWrapper.notAuthorized();
        }

        ownerId = dataverse.getOwner() != null ? dataverse.getOwner().getId() : null;
        setupForGeneralInfoEdit();

        return StringUtils.EMPTY;
    }

    private String setupViewForDataverseCreation() {
        dataverse = new Dataverse();
        dataverse.setOwner(dataverseService.find(ownerId));

        if (dataverse.getOwner() == null) {
            return permissionsWrapper.notFound();
        } else if (!permissionService.on(dataverse.getOwner()).has(Permission.AddDataverse)) {
            return permissionsWrapper.notAuthorized();
        }

        dataverse.getDataverseContacts().add(new DataverseContact(dataverse, session.getUser().getDisplayInfo().getEmailAddress()));
        dataverse.setAffiliation(session.getUser().getDisplayInfo().getAffiliation());
        setupForGeneralInfoEdit();

        dataverse.setName(DataverseUtil.getSuggestedDataverseNameOnCreate(session.getUser()));

        return StringUtils.EMPTY;
    }

    private void setupForGeneralInfoEdit() {
        updateDataverseSubjectSelectItems();
        initFacets();
        allMetadataBlocks = metadataBlockService.prepareMetaBlocksAndDatasetfields(dataverse, mdbOptions);
        defaultMdbOptions = mdbOptions.deepCopy();
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
}
