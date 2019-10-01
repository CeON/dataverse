package edu.harvard.iq.dataverse.dataverse.template;

import edu.harvard.iq.dataverse.DataverseServiceBean;
import edu.harvard.iq.dataverse.PermissionsWrapper;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.dataset.DatasetFieldsInitializer;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDataverseCommand;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldUtil;
import edu.harvard.iq.dataverse.persistence.dataset.MetadataBlock;
import edu.harvard.iq.dataverse.persistence.dataset.Template;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.util.JsfHelper;
import io.vavr.control.Try;
import org.apache.commons.lang3.StringUtils;

import javax.faces.application.FacesMessage;
import javax.faces.event.AbortProcessingException;
import javax.faces.event.AjaxBehaviorEvent;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static edu.harvard.iq.dataverse.util.JsfHelper.JH;

/**
 * @author skraffmiller
 */
@ViewScoped
@Named
public class ManageTemplatesPage implements java.io.Serializable {

    private DataverseServiceBean dvService;
    private TemplateDao templateDao;
    private PermissionsWrapper permissionsWrapper;
    private DatasetFieldsInitializer datasetFieldsInitializer;
    private TemplateService templateService;

    private List<Template> templatesForView = new LinkedList<>();
    private Dataverse dataverse;
    private Long dataverseId;
    private boolean inheritTemplatesValue;
    private boolean inheritTemplatesAllowed = false;
    private LocalDateTime currentTime = LocalDateTime.now();

    private Template selectedTemplate = null;
    private Map<MetadataBlock, List<DatasetField>> mdbForView;

    // -------------------- CONSTRUCTORS --------------------
    @Deprecated
    public ManageTemplatesPage() {
    }

    @Inject
    public ManageTemplatesPage(DataverseServiceBean dvService, TemplateDao templateDao,
                               PermissionsWrapper permissionsWrapper,
                               DatasetFieldsInitializer datasetFieldsInitializer, TemplateService templateService) {
        this.dvService = dvService;
        this.templateDao = templateDao;
        this.permissionsWrapper = permissionsWrapper;
        this.datasetFieldsInitializer = datasetFieldsInitializer;
        this.templateService = templateService;
    }

    // -------------------- GETTERS --------------------

    public List<Template> getTemplatesForView() {
        return templatesForView;
    }

    public Dataverse getDataverse() {
        return dataverse;
    }

    public Long getDataverseId() {
        return dataverseId;
    }

    public Template getSelectedTemplate() {
        return selectedTemplate;
    }

    public boolean isInheritTemplatesValue() {
        return inheritTemplatesValue;
    }

    public boolean isInheritTemplatesAllowed() {
        return inheritTemplatesAllowed;
    }

    public Map<MetadataBlock, List<DatasetField>> getMdbForView() {
        return mdbForView;
    }

    // -------------------- LOGIC --------------------

    public String init() {
        dataverse = dvService.find(dataverseId);
        if (dataverse == null) {
            return permissionsWrapper.notFound();
        }
        if (!permissionsWrapper.canIssueCommand(dataverse, UpdateDataverseCommand.class)) {
            return permissionsWrapper.notAuthorized();
        }

        if (dataverse.getOwner() != null && dataverse.getRootMetadataBlocks().equals(dataverse.getOwner().getRootMetadataBlocks())) {
            setInheritTemplatesAllowed(true);
        }

        setInheritTemplatesValue(!dataverse.isTemplateRoot());

        if (inheritTemplatesValue && dataverse.getOwner() != null) {
            templatesForView.addAll(dataverse.getParentTemplates());
        }

        templatesForView.addAll(dataverse.getTemplates());

        if (!templatesForView.isEmpty()) {
            JH.addMessage(FacesMessage.SEVERITY_INFO, BundleUtil.getStringFromBundle("dataset.message.manageTemplates.label"), BundleUtil.getStringFromBundle("dataset.message.manageTemplates.message"));
        }

        return StringUtils.EMPTY;
    }

    public void makeDefault(Template templateIn) {
        dataverse.setDefaultTemplate(templateIn);

        templateService.updateDataverse(dataverse)
                .onFailure(throwable -> JH.addMessage(FacesMessage.SEVERITY_FATAL, BundleUtil.getStringFromBundle("template.makeDefault.error")))
                .onSuccess(dataverse -> JsfHelper.addFlashMessage(BundleUtil.getStringFromBundle("template.makeDefault")));
    }

    public void unselectDefault() {
        dataverse.setDefaultTemplate(null);

        templateService.updateDataverse(dataverse)
                .onFailure(throwable -> JH.addMessage(FacesMessage.SEVERITY_FATAL, BundleUtil.getStringFromBundle("template.update.error")))
                .onSuccess(dataverse -> JsfHelper.addFlashMessage(BundleUtil.getStringFromBundle("template.unselectDefault")));
    }

    public String cloneTemplate(Template templateIn) {

        Try<Template> cloneTemplate = templateService.cloneTemplate(templateIn, dataverse, currentTime)
                .andThenTry(() -> templateService.updateDataverse(dataverse));

        if (cloneTemplate.isFailure()) {
            JH.addMessage(FacesMessage.SEVERITY_FATAL, BundleUtil.getStringFromBundle("template.clone.error"));
            return StringUtils.EMPTY;
        }

        templatesForView.add(cloneTemplate.get());

        String msg = BundleUtil.getStringFromBundle("template.clone");
        JsfHelper.addFlashMessage(msg);

        return "/template.xhtml?id=" + cloneTemplate.get().getId() + "&ownerId=" + dataverse.getId() + "&faces-redirect=true";
    }

    public void deleteTemplate() {
        templateService.deleteTemplate(dataverse, selectedTemplate)
                .onFailure(throwable -> JH.addMessage(FacesMessage.SEVERITY_FATAL, BundleUtil.getStringFromBundle("template.delete.error")))
                .onSuccess(dataverse -> {
                    JsfHelper.addFlashMessage(BundleUtil.getStringFromBundle("template.delete"));
                    templatesForView.remove(selectedTemplate);
                });

    }

    public void viewSelectedTemplate(Template selectedTemplate) {
        this.selectedTemplate = selectedTemplate;

        List<DatasetField> dsfForView = datasetFieldsInitializer.prepareDatasetFieldsForView(selectedTemplate.getDatasetFields());
        mdbForView = DatasetFieldUtil.groupByBlock(dsfForView);
    }

    /**
     * Updates dataverse regarding which templates it can use, since you can inherit templates from parent.
     */
    public String updateTemplatesRoot(AjaxBehaviorEvent event) throws AbortProcessingException {

        if (dataverse.getOwner() != null) {

            updateDefaultTemplates();

            templateService.updateDataverseTemplate(dataverse, isInheritTemplatesValue())
                    .onFailure(throwable -> Logger.getLogger(ManageTemplatesPage.class.getName()).log(Level.SEVERE, null, throwable))
                    .onSuccess(this::setDataverse);

            if (inheritTemplatesValue && dataverse.getOwner() != null) {
                templatesForView.addAll(dataverse.getParentTemplates());
            } else {
                templatesForView.removeAll(dataverse.getParentTemplates());
            }
        }

        templateDao.flush();
        return StringUtils.EMPTY;
    }

    // -------------------- PRIVATE --------------------

    private void updateDefaultTemplates() {
        if (isInheritTemplatesValue() && !isDataverseHasDefaultTemplate(dataverse) && isDataverseHasDefaultTemplate(dataverse.getOwner())) {
            dataverse.setDefaultTemplate(dataverse.getOwner().getDefaultTemplate());
        }

        if (!isInheritTemplatesValue() && isDataverseHasDefaultTemplate(dataverse)) {
            dataverse.getParentTemplates().stream()
                    .filter(template -> template.equals(dataverse.getDefaultTemplate()))
                    .forEach(template -> dataverse.setDefaultTemplate(null));
        }
    }

    private boolean isDataverseHasDefaultTemplate(Dataverse dataverse) {
        return dataverse.getDefaultTemplate() != null;
    }

    // -------------------- SETTERS --------------------

    public void setTemplatesForView(List<Template> templatesForView) {
        this.templatesForView = templatesForView;
    }

    public void setDataverse(Dataverse dataverse) {
        this.dataverse = dataverse;
    }


    public void setDataverseId(Long dataverseId) {
        this.dataverseId = dataverseId;
    }

    public void setInheritTemplatesValue(boolean inheritTemplatesValue) {
        this.inheritTemplatesValue = inheritTemplatesValue;
    }

    public void setInheritTemplatesAllowed(boolean inheritTemplatesAllowed) {
        this.inheritTemplatesAllowed = inheritTemplatesAllowed;
    }

    public void setSelectedTemplate(Template selectedTemplate) {
        this.selectedTemplate = selectedTemplate;
    }

    public void setCurrentTime(LocalDateTime currentTime) {
        this.currentTime = currentTime;
    }
}
