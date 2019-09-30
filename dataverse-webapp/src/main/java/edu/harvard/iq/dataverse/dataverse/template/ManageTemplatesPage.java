package edu.harvard.iq.dataverse.dataverse.template;

import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.DataverseServiceBean;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.PermissionsWrapper;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.dataset.DatasetFieldsInitializer;
import edu.harvard.iq.dataverse.dataverse.DataversePage;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDataverseCommand;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDataverseTemplateRootCommand;
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
    private EjbDataverseEngine engineService;
    private DataversePage dvpage;
    private DataverseRequestServiceBean dvRequestService;
    private PermissionsWrapper permissionsWrapper;
    private DatasetFieldsInitializer datasetFieldsInitializer;
    private TemplateService templateService;

    private List<Template> templatesForView;
    private Dataverse dataverse;
    private Long dataverseId;
    private boolean inheritTemplatesValue;
    private boolean inheritTemplatesAllowed = false;

    private Template selectedTemplate = null;
    private Map<MetadataBlock, List<DatasetField>> mdbForView;

    // -------------------- CONSTRUCTORS --------------------
    @Deprecated
    public ManageTemplatesPage() {
    }

    @Inject
    public ManageTemplatesPage(DataverseServiceBean dvService, TemplateDao templateDao, EjbDataverseEngine engineService,
                               DataversePage dvpage, DataverseRequestServiceBean dvRequestService, PermissionsWrapper permissionsWrapper,
                               DatasetFieldsInitializer datasetFieldsInitializer, TemplateService templateService) {
        this.dvService = dvService;
        this.templateDao = templateDao;
        this.engineService = engineService;
        this.dvpage = dvpage;
        this.dvRequestService = dvRequestService;
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

        dvpage.setDataverse(dataverse);
        if (dataverse.getOwner() != null && dataverse.getRootMetadataBlocks().equals(dataverse.getOwner().getRootMetadataBlocks())) {
            setInheritTemplatesAllowed(true);
        }

        templatesForView = new LinkedList<>();
        setInheritTemplatesValue(!dataverse.isTemplateRoot());
        if (inheritTemplatesValue && dataverse.getOwner() != null) {
            for (Template pt : dataverse.getParentTemplates()) {
                pt.setDataverse(dataverse.getOwner());
                templatesForView.add(pt);
            }
        }
        for (Template ct : dataverse.getTemplates()) {
            ct.setDataverse(dataverse);
            ct.setDataversesHasAsDefault(templateDao.findDataversesByDefaultTemplateId(ct.getId()));
            ct.setIsDefaultForDataverse(!ct.getDataversesHasAsDefault().isEmpty());
            templatesForView.add(ct);
        }
        if (!templatesForView.isEmpty()) {
            JH.addMessage(FacesMessage.SEVERITY_INFO, BundleUtil.getStringFromBundle("dataset.message.manageTemplates.label"), BundleUtil.getStringFromBundle("dataset.message.manageTemplates.message"));
        }
        return null;
    }

    public void makeDefault(Template templateIn) {
        dataverse.setDefaultTemplate(templateIn);

        templateService.saveDataverse(dataverse)
                .onFailure(throwable -> JH.addMessage(FacesMessage.SEVERITY_FATAL, BundleUtil.getStringFromBundle("template.makeDefault.error")))
                .onSuccess(dataverse -> JsfHelper.addFlashMessage(BundleUtil.getStringFromBundle("template.makeDefault")));
    }

    public void unselectDefault() {
        dataverse.setDefaultTemplate(null);

        templateService.saveDataverse(dataverse)
                .onFailure(throwable -> JH.addMessage(FacesMessage.SEVERITY_FATAL, BundleUtil.getStringFromBundle("template.update.error")))
                .onSuccess(dataverse -> JsfHelper.addFlashMessage(BundleUtil.getStringFromBundle("template.unselectDefault")));
    }

    public String cloneTemplate(Template templateIn) {

        boolean isCloneOperationFail = templateService.cloneTemplate(templateIn, dataverse);

        if (isCloneOperationFail) {
            JH.addMessage(FacesMessage.SEVERITY_FATAL, BundleUtil.getStringFromBundle("template.clone.error"));
            return StringUtils.EMPTY;
        }

        saveDataverseWithDefaultMessage();

        templatesForView.add(templateIn);

        String msg = BundleUtil.getStringFromBundle("template.clone");
        JsfHelper.addFlashMessage(msg);

        return "/template.xhtml?id=" + templateIn.getId() + "&ownerId=" + dataverse.getId() + "&faces-redirect=true";
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

    public String updateTemplatesRoot(AjaxBehaviorEvent event) throws AbortProcessingException {
        try {
            if (dataverse.getOwner() != null) {
                if (isInheritTemplatesValue() && dataverse.getDefaultTemplate() == null && dataverse.getOwner().getDefaultTemplate() != null) {
                    dataverse.setDefaultTemplate(dataverse.getOwner().getDefaultTemplate());
                }
                if (!isInheritTemplatesValue()) {
                    if (dataverse.getDefaultTemplate() != null) {
                        for (Template test : dataverse.getParentTemplates()) {
                            if (test.equals(dataverse.getDefaultTemplate())) {
                                dataverse.setDefaultTemplate(null);
                            }
                        }
                    }
                }
            }

            dataverse = engineService.submit(new UpdateDataverseTemplateRootCommand(!isInheritTemplatesValue(), dvRequestService.getDataverseRequest(), getDataverse()));
            init();
            return "";
        } catch (CommandException ex) {
            Logger.getLogger(ManageTemplatesPage.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "";
    }

    // -------------------- PRIVATE --------------------

    private Try<Dataverse> saveDataverseWithDefaultMessage() {

        return templateService.saveDataverse(dataverse)
                .onFailure(throwable -> JH.addMessage(FacesMessage.SEVERITY_FATAL, BundleUtil.getStringFromBundle("template.update.error")))
                .onSuccess(dataverse -> JsfHelper.addFlashMessage(BundleUtil.getStringFromBundle("template.update")));
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
}
