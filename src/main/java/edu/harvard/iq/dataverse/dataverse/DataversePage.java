package edu.harvard.iq.dataverse.dataverse;

import edu.harvard.iq.dataverse.ControlledVocabularyValue;
import edu.harvard.iq.dataverse.ControlledVocabularyValueServiceBean;
import edu.harvard.iq.dataverse.DatasetFieldConstant;
import edu.harvard.iq.dataverse.DatasetFieldServiceBean;
import edu.harvard.iq.dataverse.DatasetFieldType;
import edu.harvard.iq.dataverse.DatasetServiceBean;
import edu.harvard.iq.dataverse.Dataverse;
import edu.harvard.iq.dataverse.DataverseContact;
import edu.harvard.iq.dataverse.DataverseFacet;
import edu.harvard.iq.dataverse.DataverseFacetServiceBean;
import edu.harvard.iq.dataverse.DataverseFeaturedDataverse;
import edu.harvard.iq.dataverse.DataverseFieldTypeInputLevel;
import edu.harvard.iq.dataverse.DataverseFieldTypeInputLevelServiceBean;
import edu.harvard.iq.dataverse.DataverseLinkingServiceBean;
import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.DataverseServiceBean;
import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.FeaturedDataverseServiceBean;
import edu.harvard.iq.dataverse.MetadataBlock;
import edu.harvard.iq.dataverse.PermissionServiceBean;
import edu.harvard.iq.dataverse.PermissionsWrapper;
import edu.harvard.iq.dataverse.UserNotification.Type;
import edu.harvard.iq.dataverse.UserNotificationServiceBean;
import edu.harvard.iq.dataverse.authorization.Permission;
import edu.harvard.iq.dataverse.authorization.users.AuthenticatedUser;
import edu.harvard.iq.dataverse.authorization.users.User;
import edu.harvard.iq.dataverse.bannersandmessages.DataverseUtil;
import edu.harvard.iq.dataverse.engine.command.Command;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.engine.command.impl.CreateDataverseCommand;
import edu.harvard.iq.dataverse.engine.command.impl.CreateSavedSearchCommand;
import edu.harvard.iq.dataverse.engine.command.impl.DeleteDataverseCommand;
import edu.harvard.iq.dataverse.engine.command.impl.LinkDataverseCommand;
import edu.harvard.iq.dataverse.engine.command.impl.PublishDataverseCommand;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDataverseCommand;
import edu.harvard.iq.dataverse.search.SearchIncludeFragment;
import edu.harvard.iq.dataverse.search.savedsearch.SavedSearch;
import edu.harvard.iq.dataverse.search.savedsearch.SavedSearchFilterQuery;
import edu.harvard.iq.dataverse.settings.SettingsWrapper;
import edu.harvard.iq.dataverse.util.BundleUtil;
import edu.harvard.iq.dataverse.util.JsfHelper;
import edu.harvard.iq.dataverse.util.SystemConfig;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.primefaces.event.TransferEvent;
import org.primefaces.model.DualListModel;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.faces.application.FacesMessage;
import javax.faces.component.UIComponent;
import javax.faces.component.UIInput;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;
import javax.faces.model.SelectItem;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static edu.harvard.iq.dataverse.util.JsfHelper.JH;


/**
 * @author gdurand
 */
@SuppressWarnings("Duplicates")
@ViewScoped
@Named("DataversePage")
public class DataversePage implements java.io.Serializable {

    private static final Logger logger = Logger.getLogger(DataversePage.class.getCanonicalName());

    public enum EditMode {
        CREATE, FEATURED
    }

    public enum LinkMode {
        SAVEDSEARCH, LINKDATAVERSE
    }

    @EJB
    DataverseServiceBean dataverseService;
    @EJB
    DatasetServiceBean datasetService;
    @Inject
    DataverseSession session;
    @EJB
    EjbDataverseEngine commandEngine;
    @EJB
    DatasetFieldServiceBean datasetFieldService;
    @EJB
    DataverseFacetServiceBean dataverseFacetService;
    @EJB
    UserNotificationServiceBean userNotificationService;
    @EJB
    FeaturedDataverseServiceBean featuredDataverseService;
    @EJB
    DataverseFieldTypeInputLevelServiceBean dataverseFieldTypeInputLevelService;
    @EJB
    PermissionServiceBean permissionService;
    @EJB
    ControlledVocabularyValueServiceBean controlledVocabularyValueServiceBean;
    @EJB
    SystemConfig systemConfig;
    @Inject
    SearchIncludeFragment searchIncludeFragment;
    @Inject
    DataverseRequestServiceBean dvRequestService;
    @Inject
    SettingsWrapper settingsWrapper;
    @EJB
    DataverseLinkingServiceBean linkingService;
    @Inject
    PermissionsWrapper permissionsWrapper;

    private Dataverse dataverse = new Dataverse();
    private EditMode editMode;
    private LinkMode linkMode;

    private Long ownerId;
    private DualListModel<DatasetFieldType> facets = new DualListModel<>(new ArrayList<>(), new ArrayList<>());
    private DualListModel<Dataverse> featuredDataverses = new DualListModel<>(new ArrayList<>(), new ArrayList<>());
    private List<Dataverse> dataversesForLinking;
    private Long linkingDataverseId;
    private List<SelectItem> linkingDVSelectItems;
    private List<ControlledVocabularyValue> dataverseSubjectControlledVocabularyValues;
    private Dataverse linkingDataverse;
    private Long facetMetadataBlockId;
    private List<Dataverse> carouselFeaturedDataverses = null;
    private Set<MetadataBlock> allMetadataBlocks;

    private DataverseMetaBlockOptions mdbOptions = new DataverseMetaBlockOptions();

    // -------------------- GETTERS --------------------

    public DataverseMetaBlockOptions getMdbOptions() {
        return mdbOptions;
    }

    public Dataverse getLinkingDataverse() {
        return linkingDataverse;
    }

    public List<SelectItem> getLinkingDVSelectItems() {
        return linkingDVSelectItems;
    }

    public Long getLinkingDataverseId() {
        return linkingDataverseId;
    }

    public List<Dataverse> getDataversesForLinking() {
        return dataversesForLinking;
    }

    public List<ControlledVocabularyValue> getDataverseSubjectControlledVocabularyValues() {
        return dataverseSubjectControlledVocabularyValues;
    }

    public Dataverse getDataverse() {
        return dataverse;
    }

    public LinkMode getLinkMode() {
        return linkMode;
    }

    public EditMode getEditMode() {
        return editMode;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public Long getFacetMetadataBlockId() {
        return facetMetadataBlockId;
    }

    public boolean isRootDataverse() {
        return dataverse.getOwner() == null;
    }

    public Dataverse getOwner() {
        return (ownerId != null) ? dataverseService.find(ownerId) : null;
    }

    public Set<MetadataBlock> getAllMetadataBlocks() {
        return this.allMetadataBlocks;
    }

    public DualListModel<DatasetFieldType> getFacets() {
        return facets;
    }

    public DualListModel<Dataverse> getFeaturedDataverses() {
        return featuredDataverses;
    }

    // -------------------- LOGIC --------------------

    public String init() {

        if (dataverse.getAlias() != null || dataverse.getId() != null || ownerId == null) {// view mode for a dataverse
            if (dataverse.getAlias() != null) {
                dataverse = dataverseService.findByAlias(dataverse.getAlias());
            } else if (dataverse.getId() != null) {
                dataverse = dataverseService.find(dataverse.getId());
            } else {
                try {
                    dataverse = dataverseService.findRootDataverse();
                } catch (EJBException e) {
                    // @todo handle case with no root dataverse (a fresh installation) with message about using API to create the root
                    dataverse = null;
                }
            }

            // check if dv exists and user has permission
            if (dataverse == null) {
                return permissionsWrapper.notFound();
            }
            if (!dataverse.isReleased() && !permissionService.on(dataverse).has(Permission.ViewUnpublishedDataverse)) {
                return permissionsWrapper.notAuthorized();
            }
            initFeaturedDataverses();
            ownerId = dataverse.getOwner() != null ? dataverse.getOwner().getId() : null;
        } else { // ownerId != null; create mode for a new child dataverse
            editMode = EditMode.CREATE;
            dataverse.setOwner(dataverseService.find(ownerId));

            if (dataverse.getOwner() == null) {
                return permissionsWrapper.notFound();
            } else if (!permissionService.on(dataverse.getOwner()).has(Permission.AddDataverse)) {
                return permissionsWrapper.notAuthorized();
            }

            // set defaults - contact e-mail and affiliation from user
            dataverse.getDataverseContacts().add(new DataverseContact(dataverse, session.getUser().getDisplayInfo().getEmailAddress()));
            dataverse.setAffiliation(session.getUser().getDisplayInfo().getAffiliation());
            setupForGeneralInfoEdit();
            // FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, "Create New Dataverse", " - Create a new dataverse that will be a child dataverse of the parent you clicked from. Asterisks indicate required fields."));
            if (dataverse.getName() == null) {
                dataverse.setName(DataverseUtil.getSuggestedDataverseNameOnCreate(session.getUser()));
            }
        }

        return null;
    }

    public void changeFacetsMetadataBlock() {
        if (facetMetadataBlockId == null) {
            facets.setSource(datasetFieldService.findAllFacetableFieldTypes());
        } else {
            facets.setSource(datasetFieldService.findFacetableFieldTypesByMetadataBlock(facetMetadataBlockId));
        }

        facets.getSource().removeAll(facets.getTarget());
    }

    public void toggleFacetRoot() {
        if (!dataverse.isFacetRoot()) {
            initFacets();
        }
    }

    public void onFacetTransfer(TransferEvent event) {
        for (Object item : event.getItems()) {
            DatasetFieldType facet = (DatasetFieldType) item;
            if (facetMetadataBlockId != null && !facetMetadataBlockId.equals(facet.getMetadataBlock().getId())) {
                facets.getSource().remove(facet);
            }
        }
    }

    public List<Dataverse> getCarouselFeaturedDataverses() {
        if (carouselFeaturedDataverses != null) {
            return carouselFeaturedDataverses;
        }
        carouselFeaturedDataverses = featuredDataverseService.findByDataverseIdQuick(dataverse.getId());

        return carouselFeaturedDataverses;
    }

    public void refresh() {

    }

    public void showEditableDatasetFieldTypes(Long mdbId) {
        for (MetadataBlock mdb : allMetadataBlocks) {
            if (mdb.getId().equals(mdbId)) {
                mdbOptions.getMdbViewOptions().put(mdb.getId(),
                        MetadataBlockViewOptions.newBuilder()
                                .showDatasetFieldTypes(true)
                                .editableDatasetFieldTypes(true)
                                .build());
            }
        }
    }

    public void showUnEditableDatasetFieldTypes(Long mdbId) {
        for (MetadataBlock mdb : allMetadataBlocks) {
            if (mdb.getId().equals(mdbId)) {
                mdbOptions.getMdbViewOptions().put(mdb.getId(),
                        MetadataBlockViewOptions.newBuilder()
                                .showDatasetFieldTypes(true)
                                .editableDatasetFieldTypes(false)
                                .build());
            }
        }
    }

    public void hideDatasetFieldTypes(Long mdbId) {
        for (MetadataBlock mdb : allMetadataBlocks) {
            if (mdb.getId().equals(mdbId)) {
                mdbOptions.getMdbViewOptions().put(mdb.getId(),
                        MetadataBlockViewOptions.newBuilder()
                                .showDatasetFieldTypes(false)
                                .build());
            }
        }
    }

    public String saveNewDataverse() {
        List<DataverseFieldTypeInputLevel> listDFTIL = new ArrayList<>();

        if (!mdbOptions.isInheritMetaBlocksFromParent()) {
            dataverse.getMetadataBlocks().clear();

            List<MetadataBlock> selectedMetadataBlocks = getSelectedMetadataBlocks();
            dataverse.setMetadataBlocks(selectedMetadataBlocks);
            listDFTIL = getSelectedMetadataFields(selectedMetadataBlocks);
        }

        if (!dataverse.isFacetRoot()) {
            facets.getTarget().clear();
        }

        if (session.getUser().isAuthenticated()) {
            dataverse.setOwner(ownerId != null ? dataverseService.find(ownerId) : null);
            Command<Dataverse> cmd = new CreateDataverseCommand(dataverse, dvRequestService.getDataverseRequest(), facets.getTarget(), listDFTIL);

            try {
                dataverse = commandEngine.submit(cmd);
            } catch (CommandException ex) {
                logger.log(Level.SEVERE, "Unexpected Exception calling dataverse command", ex);
                JH.addMessage(FacesMessage.SEVERITY_FATAL, BundleUtil.getStringFromBundle("dataverse.create.failure"));
                return StringUtils.EMPTY;
            }

            userNotificationService.sendNotification((AuthenticatedUser) session.getUser(), dataverse.getCreateDate(), Type.CREATEDV, dataverse.getId());

            JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("dataverse.create.success",
                    Arrays.asList(settingsWrapper.getGuidesBaseUrl(), systemConfig.getGuidesVersion())));
        } else {
            JH.addMessage(FacesMessage.SEVERITY_FATAL, BundleUtil.getStringFromBundle("dataverse.create.authenticatedUsersOnly"));
            return StringUtils.EMPTY;
        }

        return returnRedirect();
    }

    public String saveFeaturedDataverse() {
        UpdateDataverseCommand cmd =
                new UpdateDataverseCommand(dataverse, null, featuredDataverses.getTarget(), dvRequestService.getDataverseRequest(), null);

        try {
            dataverse = commandEngine.submit(cmd);

            JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("dataverse.feature.update"));
        } catch (CommandException ex) {
            logger.log(Level.SEVERE, "Unexpected Exception calling dataverse command", ex);
            JH.addMessage(FacesMessage.SEVERITY_FATAL, BundleUtil.getStringFromBundle("dataverse.update.failure"));
            return StringUtils.EMPTY;
        }

        return returnRedirect();
    }

    public void cancel(ActionEvent e) {
        // reset values
        dataverse = dataverseService.find(dataverse.getId());
        ownerId = dataverse.getOwner() != null ? dataverse.getOwner().getId() : null;
        editMode = null;
    }

    public void editMetadataBlocks(boolean checkVal) {
        mdbOptions.setInheritMetaBlocksFromParent(checkVal);
        if (checkVal) {
            refreshAllMetadataBlocks();
        }
    }

    public String resetToInherit() {

        setInheritMetadataBlockFromParent(true);
        mdbOptions.setInheritMetaBlocksFromParent(false);
        refreshAllMetadataBlocks();

        return StringUtils.EMPTY;
    }

    public String saveLinkedDataverse() {

        if (linkingDataverseId == null) {
            JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("dataverse.link.select"));
            return "";
        }

        AuthenticatedUser savedSearchCreator = getAuthenticatedUser();
        if (savedSearchCreator == null) {
            String msg = BundleUtil.getStringFromBundle("dataverse.link.user");
            logger.severe(msg);
            JsfHelper.addFlashErrorMessage(msg);
            return returnRedirect();
        }

        linkingDataverse = dataverseService.find(linkingDataverseId);

        LinkDataverseCommand cmd = new LinkDataverseCommand(dvRequestService.getDataverseRequest(), linkingDataverse, dataverse);
        //LinkDvObjectCommand cmd = new LinkDvObjectCommand (session.getUser(), linkingDataverse, dataverse);
        try {
            commandEngine.submit(cmd);
        } catch (CommandException ex) {
            List<String> args = Arrays.asList(dataverse.getDisplayName(), linkingDataverse.getDisplayName());
            String msg = BundleUtil.getStringFromBundle("dataverse.link.error", args);
            logger.log(Level.SEVERE, "{0} {1}", new Object[]{msg, ex});
            JsfHelper.addFlashErrorMessage(msg);
            return returnRedirect();
        }

        JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("dataverse.linked.success.wait", getSuccessMessageArguments()));
        return returnRedirect();
    }

    public void setupLinkingPopup(String popupSetting) {
        if (popupSetting.equals("link")) {
            setLinkMode(LinkMode.LINKDATAVERSE);
        } else {
            setLinkMode(LinkMode.SAVEDSEARCH);
        }
        updateLinkableDataverses();
    }

    public String saveSavedSearch() {
        if (linkingDataverseId == null) {
            JsfHelper.addFlashErrorMessage(BundleUtil.getStringFromBundle("dataverse.link.select"));
            return "";
        }
        linkingDataverse = dataverseService.find(linkingDataverseId);

        AuthenticatedUser savedSearchCreator = getAuthenticatedUser();
        if (savedSearchCreator == null) {
            String msg = BundleUtil.getStringFromBundle("dataverse.search.user");
            logger.severe(msg);
            JsfHelper.addFlashErrorMessage(msg);
            return returnRedirect();
        }

        SavedSearch savedSearch = new SavedSearch(searchIncludeFragment.getQuery(), linkingDataverse, savedSearchCreator);
        savedSearch.setSavedSearchFilterQueries(new ArrayList<>());
        for (String filterQuery : searchIncludeFragment.getFilterQueriesDebug()) {
            if (filterQuery != null && !filterQuery.isEmpty()) {
                SavedSearchFilterQuery ssfq = new SavedSearchFilterQuery(filterQuery, savedSearch);
                savedSearch.getSavedSearchFilterQueries().add(ssfq);
            }
        }
        CreateSavedSearchCommand cmd = new CreateSavedSearchCommand(dvRequestService.getDataverseRequest(), linkingDataverse, savedSearch);
        try {
            commandEngine.submit(cmd);

            List<String> arguments = new ArrayList<>();
            String linkString = "<a href=\"/dataverse/" + linkingDataverse.getAlias() + "\">" + StringEscapeUtils.escapeHtml(linkingDataverse.getDisplayName()) + "</a>";
            arguments.add(linkString);
            String successMessageString = BundleUtil.getStringFromBundle("dataverse.saved.search.success", arguments);
            JsfHelper.addFlashSuccessMessage(successMessageString);
            return returnRedirect();
        } catch (CommandException ex) {
            String msg = "There was a problem linking this search to yours: " + ex;
            logger.severe(msg);
            JsfHelper.addFlashErrorMessage(BundleUtil.getStringFromBundle("dataverse.saved.search.failure") + " " + ex);
            return returnRedirect();
        }
    }

    public String releaseDataverse() {
        if (session.getUser() instanceof AuthenticatedUser) {
            PublishDataverseCommand cmd = new PublishDataverseCommand(dvRequestService.getDataverseRequest(), dataverse);
            try {
                commandEngine.submit(cmd);
                JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("dataverse.publish.success"));

            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Unexpected Exception calling  publish dataverse command", ex);
                JsfHelper.addFlashErrorMessage(BundleUtil.getStringFromBundle("dataverse.publish.failure"));

            }
        } else {
            JsfHelper.addFlashErrorMessage(BundleUtil.getStringFromBundle("dataverse.publish.not.authorized"));
        }
        return returnRedirect();

    }

    public String deleteDataverse() {
        DeleteDataverseCommand cmd = new DeleteDataverseCommand(dvRequestService.getDataverseRequest(), dataverse);
        try {
            commandEngine.submit(cmd);
            JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("dataverse.delete.success"));
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Unexpected Exception calling  delete dataverse command", ex);
            JsfHelper.addFlashErrorMessage(BundleUtil.getStringFromBundle("dataverse.delete.failure"));
        }
        return "/dataverse.xhtml?alias=" + dataverse.getOwner().getAlias() + "&faces-redirect=true";
    }

    public Boolean isEmptyDataverse() {
        return !dataverseService.hasData(dataverse);
    }

    public void validateAlias(FacesContext context, UIComponent toValidate, Object value) {
        if (!StringUtils.isEmpty((String) value)) {
            String alias = (String) value;

            boolean aliasFound = false;
            Dataverse dv = dataverseService.findByAlias(alias);
            if (editMode == DataversePage.EditMode.CREATE) {
                if (dv != null) {
                    aliasFound = true;
                }
            } else {
                if (dv != null && !dv.getId().equals(dataverse.getId())) {
                    aliasFound = true;
                }
            }
            if (aliasFound) {
                ((UIInput) toValidate).setValid(false);
                FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR, BundleUtil.getStringFromBundle("dataverse.alias"), BundleUtil.getStringFromBundle("dataverse.alias.taken"));
                context.addMessage(toValidate.getClientId(context), message);
            }
        }
    }

    public void updateInclude(Long mdbId, long dsftId) {
        List<DatasetFieldType> childDSFT = new ArrayList<>();

        for (MetadataBlock mdb : allMetadataBlocks) {
            if (mdb.getId().equals(mdbId)) {
                for (DatasetFieldType dsftTest : mdb.getDatasetFieldTypes()) {
                    if (dsftTest.getId().equals(dsftId)) {
                        DatasetFieldViewOptions dsftViewOptions = mdbOptions.getDatasetFieldViewOptions().get(dsftTest.getId());

                        dsftViewOptions.setSelectedDatasetFields(resetSelectItems(dsftTest));

                        if ((dsftTest.isHasParent() && !mdbOptions.isDsftIncludedField(dsftTest.getParentDatasetFieldType().getId()))
                                || (!dsftTest.isHasParent() && !dsftViewOptions.isIncluded())) {
                            dsftViewOptions.setRequiredField(false);
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
                                DatasetFieldViewOptions dsftViewOptions = mdbOptions.getDatasetFieldViewOptions().get(dsftTest.getId());
                                dsftViewOptions.setSelectedDatasetFields(resetSelectItems(dsftTest));
                            }
                        }
                    }
                }
            }
        }
    }

    public boolean isUserCanChangeAllowMessageAndBanners() {
        return session.getUser().isSuperuser();
    }

    public boolean isUserAdminForCurrentDataverse() {
        return permissionService.isUserAdminForDataverse(session.getUser(), this.dataverse);
    }

    public boolean isInheritMetadataBlockFromParent() {
        return !dataverse.isMetadataBlockRoot();
    }

    public boolean isInheritFacetFromParent() {
        return !dataverse.isFacetRoot();
    }

    // -------------------- PRIVATE --------------------

    private void updateDataverseSubjectSelectItems() {
        DatasetFieldType subjectDatasetField = datasetFieldService.findByName(DatasetFieldConstant.subject);
        setDataverseSubjectControlledVocabularyValues(controlledVocabularyValueServiceBean.findByDatasetFieldTypeId(subjectDatasetField.getId()));
    }

    private void setupForGeneralInfoEdit() {
        updateDataverseSubjectSelectItems();
        initFacets();
        refreshAllMetadataBlocks();
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

    private List<MetadataBlock> getSelectedMetadataBlocks() {
        List<MetadataBlock> selectedBlocks = new ArrayList<>();

        for (MetadataBlock mdb : this.allMetadataBlocks) {
            if (!mdbOptions.isInheritMetaBlocksFromParent() && (mdb.isSelected() || mdb.isRequired())) {
                selectedBlocks.add(mdb);
            }
        }

        return selectedBlocks;
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
        return (!dsft.isHasParent() && !mdbOptions.isDsftIncludedField(dsft.getId()))
                || (dsft.isHasParent() && !mdbOptions.isDsftIncludedField(dsft.getParentDatasetFieldType().getId()));
    }

    private boolean isDatasetFieldChildOrParentRequired(DatasetFieldType dsft) {
        DatasetFieldViewOptions dsftViewOptions = mdbOptions.getDatasetFieldViewOptions().get(dsft.getId());
        DatasetFieldViewOptions parentDsftViewOptions = mdbOptions.getDatasetFieldViewOptions().get(dsft.getParentDatasetFieldType().getId());

        return dsftViewOptions.isRequiredField() && !dsft.isRequired()
                && ((!dsft.isHasParent() && dsftViewOptions.isIncluded())
                || (dsft.isHasParent() && parentDsftViewOptions.isIncluded()));
    }

    private List<String> getSuccessMessageArguments() {
        List<String> arguments = new ArrayList<>();
        arguments.add(StringEscapeUtils.escapeHtml(dataverse.getDisplayName()));
        String linkString = "<a href=\"/dataverse/" + linkingDataverse.getAlias() + "\">" + StringEscapeUtils.escapeHtml(linkingDataverse.getDisplayName()) + "</a>";
        arguments.add(linkString);
        return arguments;
    }

    private AuthenticatedUser getAuthenticatedUser() {
        User user = session.getUser();
        if (user.isAuthenticated()) {
            return (AuthenticatedUser) user;
        } else {
            return null;
        }
    }

    private void refreshAllMetadataBlocks() {
        Dataverse freshDataverse = dataverse;

        Set<MetadataBlock> availableBlocks = new HashSet<>(dataverseService.findSystemMetadataBlocks());
        Set<MetadataBlock> metadataBlocks = retriveAllDataverseParentsMetaBlocks(dataverse);
        metadataBlocks.addAll(freshDataverse.getMetadataBlocks());
        availableBlocks.addAll(metadataBlocks);

        for (MetadataBlock mdb : availableBlocks) {

            mdbOptions.getMdbViewOptions().put(mdb.getId(),
                    MetadataBlockViewOptions.newBuilder()
                            .selected(false)
                            .showDatasetFieldTypes(false)
                            .build());

            mdb.setSelected(false);
            mdb.setShowDatasetFieldTypes(false);

            if (mdbOptions.isInheritMetaBlocksFromParent() && dataverse.getOwner() != null) {
                if (dataverse.getOwner().getMetadataBlocks().contains(mdb)) {
                    setMetaBlockAsSelected(mdb, dataverse.getOwner().getMetadataBlocks());
                }
            } else {
                if (dataverse.getMetadataBlocks(true).contains(mdb)) {
                    setMetaBlockAsSelected(mdb, dataverse.getMetadataBlocks(true));
                }
            }
        }

        Set<DatasetFieldType> datasetFieldTypes = retriveAllDatasetFieldsForMdb(availableBlocks);

        for (DatasetFieldType rootDatasetFieldType : datasetFieldTypes) {
            setViewOptionsForDatasetFieldTypes(freshDataverse.getMetadataRootId(), rootDatasetFieldType);

            if (rootDatasetFieldType.isHasChildren()) {
                for (DatasetFieldType childDatasetFieldType : rootDatasetFieldType.getChildDatasetFieldTypes()) {
                    setViewOptionsForDatasetFieldTypes(freshDataverse.getMetadataRootId(), childDatasetFieldType);
                }

            }
        }



        /*for (DatasetFieldType dsft : mdb.getDatasetFieldTypes()) {
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
        }*/


        setAllMetadataBlocks(availableBlocks);
    }

    private void setViewOptionsForDatasetFieldTypes(Long dataverseId, DatasetFieldType rootDatasetFieldType) {

        DataverseFieldTypeInputLevel dftil = dataverseFieldTypeInputLevelService.findByDataverseIdDatasetFieldTypeId(dataverseId, rootDatasetFieldType.getId());

        if (dftil != null) {
            setDsftilViewOptions(rootDatasetFieldType.getId(), dftil.isRequired(), dftil.isInclude());
        } else {
            setDsftilViewOptions(rootDatasetFieldType.getId(), rootDatasetFieldType.isRequired(), true);
        }

        mdbOptions.getDatasetFieldViewOptions()
                .get(rootDatasetFieldType.getId())
                .setSelectedDatasetFields(resetSelectItems(rootDatasetFieldType));
    }

    private Tuple2<Boolean, Boolean> setDsftilViewOptions(long datasetFieldTypeId, boolean requiredField, boolean included) {
        mdbOptions.getDatasetFieldViewOptions().put(datasetFieldTypeId, new DatasetFieldViewOptions(requiredField, included));
        return Tuple.of(requiredField, included);
    }

    private Optional<DataverseFieldTypeInputLevel> fetchDftilForDataverse(Set<DataverseFieldTypeInputLevel> dataverseFieldTypeInputLevels,
                                                                          long dataverseId,
                                                                          long datasetFieldTypeId) {

        for (DataverseFieldTypeInputLevel dftInputLevel : dataverseFieldTypeInputLevels) {
            if (dftInputLevel.getDataverse().getId().equals(dataverseId) && dftInputLevel.getDatasetFieldType().getId().equals(datasetFieldTypeId)) {
                return Optional.of(dftInputLevel);
            }
        }
        return Optional.empty();
    }

    private void setMetaBlockAsSelected(MetadataBlock mdb, List<MetadataBlock> metadataBlocks) {

        mdbOptions.getMdbViewOptions().put(mdb.getId(), MetadataBlockViewOptions.newBuilder()
                .selected(true)
                .build());
        mdb.setSelected(true);

    }

    private Set<DatasetFieldType> retriveAllDatasetFieldsForMdb(Collection<MetadataBlock> mdb) {
        Set<DatasetFieldType> allFields = new HashSet<>();

        for (MetadataBlock metadataBlock : mdb) {
            allFields.addAll(metadataBlock.getDatasetFieldTypes());
        }

        return allFields;
    }

    private Set<MetadataBlock> retriveDataverseParentsMetaBlocks(Dataverse dataverse, List<Long> includedOwners) {
        Set<MetadataBlock> metadataBlocks = new HashSet<>();

        for (Dataverse owner : dataverse.getOwners()) {
            if (includedOwners.contains(owner.getId())) {
                metadataBlocks.addAll(owner.getMetadataBlocks());
            }
        }
        return metadataBlocks;
    }

    private Set<MetadataBlock> retriveAllDataverseParentsMetaBlocks(Dataverse dataverse) {
        Set<MetadataBlock> metadataBlocks = new HashSet<>();
        for (Dataverse owner : dataverse.getOwners()) {
            metadataBlocks.addAll(owner.getMetadataBlocks(true));
        }

        return metadataBlocks;
    }

    private void initFeaturedDataverses() {
        List<Dataverse> featuredSource = new ArrayList<>();
        List<Dataverse> featuredTarget = new ArrayList<>();
        featuredSource.addAll(dataverseService.findAllPublishedByOwnerId(dataverse.getId()));
        featuredSource.addAll(linkingService.findLinkingDataverses(dataverse.getId()));
        List<DataverseFeaturedDataverse> featuredList = featuredDataverseService.findByDataverseId(dataverse.getId());
        for (DataverseFeaturedDataverse dfd : featuredList) {
            Dataverse fd = dfd.getFeaturedDataverse();
            featuredTarget.add(fd);
            featuredSource.remove(fd);
        }
        featuredDataverses = new DualListModel<>(featuredSource, featuredTarget);

    }

    private void updateLinkableDataverses() {
        dataversesForLinking = new ArrayList<>();
        linkingDVSelectItems = new ArrayList<>();

        //Since only a super user function add all dvs
        dataversesForLinking = dataverseService.findAll();// permissionService.getDataversesUserHasPermissionOn(session.getUser(), Permission.PublishDataverse);


        //for linking - make sure the link hasn't occurred and its not int the tree
        if (this.linkMode.equals(LinkMode.LINKDATAVERSE)) {

            // remove this and it's parent tree
            dataversesForLinking.remove(dataverse);
            Dataverse testDV = dataverse;
            while (testDV.getOwner() != null) {
                dataversesForLinking.remove(testDV.getOwner());
                testDV = testDV.getOwner();
            }

            for (Dataverse removeLinked : linkingService.findLinkingDataverses(dataverse.getId())) {
                dataversesForLinking.remove(removeLinked);
            }
        }


        for (Dataverse selectDV : dataversesForLinking) {
            linkingDVSelectItems.add(new SelectItem(selectDV.getId(), selectDV.getDisplayName()));
        }

        if (dataversesForLinking.size() == 1 && dataversesForLinking.get(0) != null) {
            linkingDataverse = dataversesForLinking.get(0);
            linkingDataverseId = linkingDataverse.getId();
        }
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

    private List<SelectItem> resetSelectItems(DatasetFieldType typeIn) {
        List<SelectItem> selectItems = new ArrayList<>();

        if ((typeIn.isHasParent() && mdbOptions.isDsftIncludedField(typeIn.getParentDatasetFieldType().getId())) ||
                (!typeIn.isHasParent() && mdbOptions.isDsftIncludedField(typeIn.getId()))) {
            selectItems.add(generateSelectedItem("dataverse.item.required", true, false));
            selectItems.add(generateSelectedItem("dataverse.item.optional", false, false));
        } else {
            selectItems.add(generateSelectedItem("dataverse.item.hidden", false, true));
        }
        return selectItems;
    }

    private SelectItem generateSelectedItem(String label, boolean selected, boolean disabled) {
        SelectItem requiredItem = new SelectItem();
        requiredItem.setLabel(BundleUtil.getStringFromBundle(label));
        requiredItem.setValue(selected);
        requiredItem.setDisabled(disabled);
        return requiredItem;
    }

    private String returnRedirect() {
        return "/dataverse.xhtml?alias=" + dataverse.getAlias() + "&faces-redirect=true";
    }

    // -------------------- SETTERS --------------------

    public void setLinkingDataverse(Dataverse linkingDataverse) {
        this.linkingDataverse = linkingDataverse;
    }

    public void setLinkingDVSelectItems(List<SelectItem> linkingDVSelectItems) {
        this.linkingDVSelectItems = linkingDVSelectItems;
    }

    public void setLinkingDataverseId(Long linkingDataverseId) {
        this.linkingDataverseId = linkingDataverseId;
    }

    public void setDataversesForLinking(List<Dataverse> dataversesForLinking) {

        this.dataversesForLinking = dataversesForLinking;
    }

    public void setDataverseSubjectControlledVocabularyValues(List<ControlledVocabularyValue> dataverseSubjectControlledVocabularyValues) {
        this.dataverseSubjectControlledVocabularyValues = dataverseSubjectControlledVocabularyValues;
    }

    public void setDataverse(Dataverse dataverse) {
        this.dataverse = dataverse;
    }

    public void setEditMode(EditMode editMode) {
        this.editMode = editMode;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public void setFacetMetadataBlockId(Long facetMetadataBlockId) {
        this.facetMetadataBlockId = facetMetadataBlockId;
    }

    public void setLinkMode(LinkMode linkMode) {
        this.linkMode = linkMode;
    }

    public void setInheritMetadataBlockFromParent(boolean inheritMetadataBlockFromParent) {
        dataverse.setMetadataBlockRoot(!inheritMetadataBlockFromParent);
    }


    public void setFeaturedDataverses(DualListModel<Dataverse> featuredDataverses) {
        this.featuredDataverses = featuredDataverses;
    }

    public void setFacets(DualListModel<DatasetFieldType> facets) {
        this.facets = facets;
    }


    public void setInheritFacetFromParent(boolean inheritFacetFromParent) {
        dataverse.setFacetRoot(!inheritFacetFromParent);
    }

    public void setAllMetadataBlocks(Set<MetadataBlock> inBlocks) {
        this.allMetadataBlocks = inBlocks;
    }
}
