package edu.harvard.iq.dataverse.dataset.deaccession;

import edu.harvard.iq.dataverse.DatasetPage;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.persistence.config.URLValidator;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.util.JsfHelper;
import io.vavr.control.Try;
import org.apache.commons.lang.StringUtils;
import org.omnifaces.cdi.ViewScoped;

import javax.faces.application.FacesMessage;
import javax.faces.component.UIComponent;
import javax.faces.component.UIInput;
import javax.faces.context.FacesContext;
import javax.faces.validator.ValidatorException;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Backing bean responsible for handling dataset deacession
 */
@ViewScoped
@Named("datasetDeaccessionDialog")
public class DatasetDeaccessionDialog implements Serializable {

    private static final Logger logger = Logger.getLogger(DatasetPage.class.getCanonicalName());

    private DatasetDeaccessionService datasetDeaccessionService;

    private Dataset dataset;

    private boolean renderDeaccessionDialog = false;
    private List<DatasetVersion> selectedDeaccessionVersions;
    private Integer deaccessionReasonRadio = 0;
    private String deaccessionReasonText = "";
    private String deaccessionForwardURLFor = "";
    private List<DatasetVersion> releasedVersions = new ArrayList<>();

    // -------------------- CONSTRUCTORS --------------------
    @Deprecated
    public DatasetDeaccessionDialog() {
    }

    @Inject
    public DatasetDeaccessionDialog(DatasetDeaccessionService datasetDeaccessionService) {
        this.datasetDeaccessionService = datasetDeaccessionService;
    }

    // -------------------- GETTERS --------------------

    public boolean isRenderDeaccessionDialog() {
        return renderDeaccessionDialog;
    }

    public List<DatasetVersion> getReleasedVersions() {
        return releasedVersions;
    }

    public List<DatasetVersion> getSelectedDeaccessionVersions() {
        return selectedDeaccessionVersions;
    }

    public Integer getDeaccessionReasonRadio() {
        return deaccessionReasonRadio;
    }

    public String getDeaccessionReasonText() {
        return deaccessionReasonText;
    }

    public String getDeaccessionForwardURLFor() {
        return deaccessionForwardURLFor;
    }

    // -------------------- LOGIC --------------------

    /**
     * Method that must be executed before any
     * further operations can be done on this dialog.
     * <p>
     * Note that it not initialize dataset versions
     * which is done in {@link #reloadAndRenderDialog()} to
     * enable lazy loading of them.
     */
    public void init(Dataset dataset) {
        this.dataset = dataset;
    }

    /**
     * Loads dataset versions and causes deaccession dialog to render
     */
    public void reloadAndRenderDialog() {
        releasedVersions = loadReleasedDatasetVersions();
        renderDeaccessionDialog = true;
    }

    public void validateDeaccessionReason(FacesContext context, UIComponent toValidate, Object value) throws ValidatorException {

        UIInput reasonRadio = (UIInput) toValidate.getAttributes().get("reasonRadio");
        Object reasonRadioValue = reasonRadio.getValue();
        Integer radioVal = new Integer(reasonRadioValue.toString());

        if (radioVal == 7 && StringUtils.isEmpty((String)value)) {
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR, StringUtils.EMPTY, BundleUtil.getStringFromBundle("file.deaccessionDialog.dialog.textForReason.error"));
            throw new ValidatorException(message);
        }
        if (StringUtils.length((String)value) > DatasetVersion.VERSION_NOTE_MAX_LENGTH) {
            String lengthString = String.valueOf(DatasetVersion.VERSION_NOTE_MAX_LENGTH);
            String userMsg = BundleUtil.getStringFromBundle("file.deaccessionDialog.dialog.limitChar.error", lengthString);
            throw new ValidatorException(new FacesMessage(FacesMessage.SEVERITY_ERROR, StringUtils.EMPTY, userMsg));
        }
    }

    public void validateForwardURL(FacesContext context, UIComponent toValidate, Object value) throws ValidatorException {

        String valueStr = (String)value;

        if (StringUtils.isEmpty(valueStr)) {
            return;
        }

        if (!URLValidator.isURLValid(valueStr)) {
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR, StringUtils.EMPTY,
                    BundleUtil.getStringFromBundle("file.deaccessionDialog.dialog.url.error"));

            throw new ValidatorException(message);
        }
        if (valueStr.length() > DatasetVersion.ARCHIVE_NOTE_MAX_LENGTH) {
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR, StringUtils.EMPTY,
                    BundleUtil.getStringFromBundle("file.deaccessionDialog.dialog.url.error"));

            throw new ValidatorException(message);
        }

    }

    public String deaccessVersions() {
        Try<List<DatasetVersion>> deaccessionOperation;
        String deaccessionReason = buildDeaccessionReason();

        if (selectedDeaccessionVersions == null) {
            deaccessionOperation = Try.of(
                    () -> datasetDeaccessionService.deaccessReleasedVersions(dataset.getVersions(),
                            deaccessionReason, deaccessionForwardURLFor));
        } else {
            deaccessionOperation = Try.of(
                    () -> datasetDeaccessionService.deaccessVersions(selectedDeaccessionVersions,
                            deaccessionReason, deaccessionForwardURLFor));
        }

        if(deaccessionOperation.isSuccess()) {
            JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("datasetVersion.message.deaccessionSuccess"));
        } else {
            logger.log(Level.SEVERE, "Deaccession has failed.", deaccessionOperation.getCause());
            JsfHelper.addFlashErrorMessage(BundleUtil.getStringFromBundle("dataset.message.deaccessionFailure"));
        }

        return returnToDataset();
    }

    // -------------------- PRIVATE --------------------

    private List<DatasetVersion> loadReleasedDatasetVersions() {
        List<DatasetVersion> retList = new ArrayList<>();
        for (DatasetVersion version : dataset.getVersions()) {
            if (version.isReleased() || version.isArchived()) {
                retList.add(version);
            }
        }
        return retList;
    }

    private String buildDeaccessionReason() {

        String deaccessionReason = StringUtils.EMPTY;

        switch (deaccessionReasonRadio) {
            case 1:
                deaccessionReason = BundleUtil.getStringFromBundle("file.deaccessionDialog.reason.selectItem.identifiable");
                break;
            case 2:
                deaccessionReason = BundleUtil.getStringFromBundle("file.deaccessionDialog.reason.selectItem.beRetracted");
                break;
            case 3:
                deaccessionReason = BundleUtil.getStringFromBundle("file.deaccessionDialog.reason.selectItem.beTransferred");
                break;
            case 4:
                deaccessionReason = BundleUtil.getStringFromBundle("file.deaccessionDialog.reason.selectItem.IRB");
                break;
            case 5:
                deaccessionReason = BundleUtil.getStringFromBundle("file.deaccessionDialog.reason.selectItem.legalIssue");
                break;
            case 6:
                deaccessionReason = BundleUtil.getStringFromBundle("file.deaccessionDialog.reason.selectItem.notValid");
                break;
            case 7:
                break;
        }

        String deacessionReasonDetail = StringUtils.trimToEmpty(deaccessionReasonText);
        if (!deacessionReasonDetail.isEmpty()) {
            deaccessionReason = deaccessionReason + ' ' + deacessionReasonDetail;
        }

        return deaccessionReason.trim();
    }

    private String returnToDataset() {
        return "/dataset.xhtml?persistentId=" + dataset.getGlobalId().asString() + "&faces-redirect=true";
    }

    // -------------------- SETTERS --------------------

    public void setRenderDeaccessionDialog(boolean renderDeaccessionPopup) {
        this.renderDeaccessionDialog = renderDeaccessionPopup;
    }

    public void setReleasedVersions(List<DatasetVersion> releasedVersionTabList) {
        this.releasedVersions = releasedVersionTabList;
    }

    public void setSelectedDeaccessionVersions(List<DatasetVersion> selectedDeaccessionVersions) {
        this.selectedDeaccessionVersions = selectedDeaccessionVersions;
    }

    public void setDeaccessionReasonText(String deaccessionReasonText) {
        this.deaccessionReasonText = deaccessionReasonText;
    }

    public void setDeaccessionForwardURLFor(String deaccessionForwardURLFor) {
        this.deaccessionForwardURLFor = deaccessionForwardURLFor;
    }

    public void setDeaccessionReasonRadio(Integer deaccessionReasonRadio) {
        this.deaccessionReasonRadio = deaccessionReasonRadio;
    }

}
