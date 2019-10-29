package edu.harvard.iq.dataverse.guestbook;

import edu.harvard.iq.dataverse.DataverseServiceBean;
import edu.harvard.iq.dataverse.GuestbookResponseServiceBean;
import edu.harvard.iq.dataverse.GuestbookServiceBean;
import edu.harvard.iq.dataverse.PermissionsWrapper;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.dataverse.DataversePage;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDataverseCommand;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.guestbook.Guestbook;
import edu.harvard.iq.dataverse.util.JsfHelper;
import io.vavr.control.Try;

import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static edu.harvard.iq.dataverse.util.JsfHelper.JH;

/**
 * @author skraffmiller
 */
@ViewScoped
@Named
public class ManageGuestbooksPage implements java.io.Serializable {
    private static final Logger logger = Logger.getLogger(ManageGuestbooksPage.class.getCanonicalName());


    private DataverseServiceBean dvService;
    private GuestbookResponseServiceBean guestbookResponseService;
    private GuestbookServiceBean guestbookService;
    private DataversePage dvpage;
    private PermissionsWrapper permissionsWrapper;
    private ManageGuestbooksCRUDService manageGuestbooksService;

    private List<Guestbook> guestbooks;
    private Dataverse dataverse;
    private Long dataverseId;
    private boolean inheritGuestbooksValue;
    private boolean displayDownloadAll = false;
    private Guestbook selectedGuestbook = null;

    // -------------------- CONSTRUCTORS --------------------
    @Deprecated
    public ManageGuestbooksPage() {
    }

    @Inject
    public ManageGuestbooksPage(DataverseServiceBean dvService, GuestbookResponseServiceBean guestbookResponseService,
                                GuestbookServiceBean guestbookService, DataversePage dvpage,
                                PermissionsWrapper permissionsWrapper,
                                ManageGuestbooksCRUDService manageGuestbooksService) {
        this.dvService = dvService;
        this.guestbookResponseService = guestbookResponseService;
        this.guestbookService = guestbookService;
        this.dvpage = dvpage;
        this.permissionsWrapper = permissionsWrapper;
        this.manageGuestbooksService = manageGuestbooksService;
    }

    public String init() {
        dataverse = dvService.find(dataverseId);

        if (dataverse == null) {
            return permissionsWrapper.notFound();
        }
        if (!permissionsWrapper.canIssueCommand(dataverse, UpdateDataverseCommand.class)) {
            return permissionsWrapper.notAuthorized();
        }

        Long totalResponses = guestbookResponseService.findCountAll(dataverseId);
        if (totalResponses.intValue() > 0) {
            displayDownloadAll = true;
            FacesContext.getCurrentInstance().addMessage(null,
                                                         new FacesMessage(FacesMessage.SEVERITY_INFO,
                                                                          BundleUtil.getStringFromBundle("dataset.manageGuestbooks.tip.title"),
                                                                          BundleUtil.getStringFromBundle("dataset.manageGuestbooks.tip.downloadascsv")));

        }

        dvpage.setDataverse(dataverse);

        guestbooks = new LinkedList<>();
        setInheritGuestbooksValue(!dataverse.isGuestbookRoot());
        if (inheritGuestbooksValue && dataverse.getOwner() != null) {
            for (Guestbook pg : dataverse.getParentGuestbooks()) {
                pg.setUsageCount(guestbookService.findCountUsages(pg.getId(), dataverseId));
                pg.setResponseCount(guestbookResponseService.findCountByGuestbookId(pg.getId(), dataverseId));
                guestbooks.add(pg);
            }
        }
        for (Guestbook cg : dataverse.getGuestbooks()) {
            cg.setDeletable(true);
            cg.setUsageCount(guestbookService.findCountUsages(cg.getId(), dataverseId));
            if (!(guestbookService.findCountUsages(cg.getId(), null) == 0)) {
                cg.setDeletable(false);
            }
            cg.setResponseCount(guestbookResponseService.findCountByGuestbookId(cg.getId(), dataverseId));
            if (!(guestbookResponseService.findCountByGuestbookId(cg.getId(), null) == 0)) {
                cg.setDeletable(false);
            }
            cg.setDataverse(dataverse);
            guestbooks.add(cg);
        }
        return null;
    }

    public void streamResponsesByDataverse() {
        FacesContext ctx = FacesContext.getCurrentInstance();
        HttpServletResponse response = (HttpServletResponse) ctx.getExternalContext().getResponse();
        response.setContentType("text/comma-separated-values");
        String fileNameString = "attachment;filename=" + getFileName();
        response.setHeader("Content-Disposition", fileNameString);
        try {
            ServletOutputStream out = response.getOutputStream();
            guestbookResponseService.streamResponsesByDataverseIdAndGuestbookId(out, dataverseId, null);
            out.flush();
            ctx.responseComplete();
        } catch (Exception e) {
            logger.warning("Failed to stream collected guestbook responses for dataverse " + dataverseId);
        }
    }

    private String getFileName() {
        // The fix below replaces any spaces in the name of the dataverse with underscores;
        // without it, the filename was chopped off (by the browser??), and the user
        // was getting the file name "Foo", instead of "Foo and Bar in Social Sciences.csv". -- L.A.
        return dataverse.getName().replace(' ', '_') + "_GuestbookReponses.csv";
    }

    public void deleteGuestbook() {
        Try.of(() -> manageGuestbooksService.delete(dataverse, selectedGuestbook))
                .onSuccess(dv -> {
                    guestbooks.remove(selectedGuestbook);
                    dataverse.getGuestbooks().remove(selectedGuestbook);
                    JsfHelper.addFlashMessage(BundleUtil.getStringFromBundle("dataset.manageGuestbooks.message.deleteSuccess"));
                })
                .onFailure(throwable -> JH.addMessage(FacesMessage.SEVERITY_FATAL, BundleUtil.getStringFromBundle("dataset.manageGuestbooks.message.deleteFailure")));
    }

    public void saveDataverse(ActionEvent e) {
        saveDataverse("", "");
    }

    public String enableGuestbook(Guestbook selectedGuestbook) {
        selectedGuestbook.setEnabled(true);
        saveDataverse("dataset.manageGuestbooks.message.enableSuccess", "dataset.manageGuestbooks.message.enableFailure");
        return "";
    }

    public String disableGuestbook(Guestbook selectedGuestbook) {
        selectedGuestbook.setEnabled(false);
        saveDataverse("dataset.manageGuestbooks.message.disableSuccess", "dataset.manageGuestbooks.message.disableFailure");
        return "";
    }


    private void saveDataverse(String successMessage, String failureMessage) {
        if (successMessage.isEmpty()) {
            successMessage = "dataset.manageGuestbooks.message.editSuccess";
        }
        if (failureMessage.isEmpty()) {
            failureMessage = "dataset.manageGuestbooks.message.editFailure";
        }

        String finalSuccessMessage = successMessage;
        String finalFailureMessage = failureMessage;
        Try.of(() -> manageGuestbooksService.createOrUpdate(dataverse))
                .onSuccess(dv -> JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle(finalSuccessMessage)))
                .onFailure(throwable -> JH.addMessage(FacesMessage.SEVERITY_FATAL, BundleUtil.getStringFromBundle(finalFailureMessage)));
    }

    public List<Guestbook> getGuestbooks() {
        return guestbooks;
    }

    public void setGuestbooks(List<Guestbook> guestbooks) {
        this.guestbooks = guestbooks;
    }


    public Dataverse getDataverse() {
        return dataverse;
    }

    public void setDataverse(Dataverse dataverse) {
        this.dataverse = dataverse;
    }

    public Long getDataverseId() {
        return dataverseId;
    }

    public void setDataverseId(Long dataverseId) {
        this.dataverseId = dataverseId;
    }

    public boolean isInheritGuestbooksValue() {
        return inheritGuestbooksValue;
    }

    public void setInheritGuestbooksValue(boolean inheritGuestbooksValue) {
        this.inheritGuestbooksValue = inheritGuestbooksValue;
    }

    public Guestbook getSelectedGuestbook() {
        return selectedGuestbook;
    }

    public void setSelectedGuestbook(Guestbook selectedGuestbook) {
        this.selectedGuestbook = selectedGuestbook;
    }

    public void viewSelectedGuestbook(Guestbook selectedGuestbook) {
        this.selectedGuestbook = selectedGuestbook;
    }

    public boolean isDisplayDownloadAll() {
        return displayDownloadAll;
    }

    public void setDisplayDownloadAll(boolean displayDownloadAll) {
        this.displayDownloadAll = displayDownloadAll;
    }

    public void updateGuestbooksRoot(javax.faces.event.AjaxBehaviorEvent event) throws javax.faces.event.AbortProcessingException {
        Try.of(() -> manageGuestbooksService.updateRoot(dataverse))
                .onSuccess(dv -> init())
                .onFailure(throwable -> Logger.getLogger(ManageGuestbooksPage.class.getName()).log(Level.SEVERE, null, throwable));
    }
}
