package edu.harvard.iq.dataverse.dashboard;

import edu.harvard.iq.dataverse.DatasetDao;
import edu.harvard.iq.dataverse.DataverseDao;
import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.PermissionsWrapper;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.engine.command.exception.MoveDatasetException;
import edu.harvard.iq.dataverse.engine.command.exception.MoveDatasetException.AdditionalStatus;
import edu.harvard.iq.dataverse.engine.command.impl.MoveDatasetCommand;
import edu.harvard.iq.dataverse.persistence.GlobalId;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.User;
import edu.harvard.iq.dataverse.settings.SettingsWrapper;
import edu.harvard.iq.dataverse.util.JsfHelper;
import org.apache.commons.lang.StringUtils;

import javax.ejb.EJB;
import javax.faces.application.FacesMessage;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static edu.harvard.iq.dataverse.common.BundleUtil.getStringFromBundle;

@ViewScoped
@Named("DashboardDatamovePage")
public class DashboardDatamovePage implements Serializable {
    private static final Logger logger = Logger.getLogger(DashboardDatamovePage.class.getCanonicalName());

    @Inject
    HttpServletRequest request;

    @EJB
    private DatasetDao datasetDao;

    @EJB
    private DataverseDao dataverseDao;

    @Inject
    private DataverseSession session;

    @Inject
    private PermissionsWrapper permissionsWrapper;

    @EJB
    private EjbDataverseEngine commandEngine;

    @Inject
    private SettingsWrapper settings;

    private boolean forceMove = false;

    private AuthenticatedUser authenticatedUser;

    private Dataset sourceDataset;

    private Dataverse targetDataverse;

    // -------------------- GETTERS --------------------

    public boolean isForceMove() {
        return forceMove;
    }

    public Dataset getSourceDataset() {
        return sourceDataset;
    }

    public Dataverse getTargetDataverse() {
        return targetDataverse;
    }

    // -------------------- LOGIC --------------------

    public String init() {
        User user = session.getUser();
        if (user != null && user.isAuthenticated() && user.isSuperuser()) {
            authenticatedUser = (AuthenticatedUser) user;
        } else {
            return permissionsWrapper.notAuthorized();
        }

        JsfHelper.addMessage(FacesMessage.SEVERITY_INFO,  getStringFromBundle("dashboard.datamove.manage"),
                getStringFromBundle("dashboard.datamove.message", settings.getGuidesBaseUrl(), settings.getGuidesVersion()));
        return StringUtils.EMPTY;
    }

    public List<Dataset> completeSourceDataset(String query) {
        if (query.contains("/")) {
            Dataset ds = datasetDao.findByGlobalId(query);
            return ds != null ? Collections.singletonList(ds) : Collections.emptyList();
        }
        return Collections.emptyList();
    }

    public List<Dataverse> completeTargetDataverse(String query) {
        return dataverseDao.filterByAliasQuery(query);
    }

    public void move() {
        if (sourceDataset == null || targetDataverse == null) {
            // We should never get here, but in case of unexpected validation fail we should be prepared
            JsfHelper.addFlashErrorMessage(getStringFromBundle("dashboard.datamove.empty.fields"));
            return;
        }

        Summary summary = new Summary().add(sourceDataset.getDisplayName())
                .add(extractSourcePersistentId())
                .add(targetDataverse.getName());

        try {
            DataverseRequest dataverseRequest = new DataverseRequest(authenticatedUser, request);
            commandEngine.submit(new MoveDatasetCommand(dataverseRequest, sourceDataset, targetDataverse, forceMove));

            logger.info(createMessageWithMoveInfo("Moved"));
            JsfHelper.addFlashSuccessMessage(getStringFromBundle("dashboard.datamove.message.success", summary.getSummaryParams()));
        } catch (MoveDatasetException mde) {
            logger.log(Level.WARNING, createMessageWithMoveInfo("Unable to move"), mde);
            summary.add(mde)
                    .add(createForceInfoIfApplicable(mde));
            JsfHelper.addErrorMessage(null,
                    getStringFromBundle("dashboard.datamove.message.failure.summary"),
                    getStringFromBundle("dashboard.datamove.message.failure.details", summary.getSummaryParams()));
        } catch (CommandException ce) {
            logger.log(Level.WARNING, createMessageWithMoveInfo("Unable to move"), ce);
            JsfHelper.addErrorMessage(null,
                    getStringFromBundle("dashboard.datamove.message.failure.summary"), StringUtils.EMPTY);
        }
    }

    // -------------------- PRIVATE --------------------

    private String extractSourcePersistentId() {
        return Optional.ofNullable(sourceDataset)
                .map(Dataset::getGlobalId)
                .map(GlobalId::asString)
                .orElse(StringUtils.EMPTY);
    }

    private String extractSourceAlias() {
        return Optional.ofNullable(sourceDataset)
                .map(Dataset::getOwner)
                .map(Dataverse::getAlias)
                .orElse(StringUtils.EMPTY);
    }

    private String extractTargetAlias() {
        return Optional.ofNullable(targetDataverse)
                .map(Dataverse::getAlias)
                .orElse(StringUtils.EMPTY);
    }

    private String createMessageWithMoveInfo(String message) {
        return message + " " +
                extractSourcePersistentId() +
                " from " + extractSourceAlias() +
                " to " + extractTargetAlias();
    }

    private String createForceInfoIfApplicable(MoveDatasetException mde) {
        return isForcingPossible(mde)
                ? getStringFromBundle("dashboard.datamove.dataset.command.suggestForce",
                settings.getGuidesBaseUrl(), settings.getGuidesVersion())
                : StringUtils.EMPTY;
    }

    private boolean isForcingPossible(MoveDatasetException mde) {
        return mde.getDetails().stream()
                .allMatch(AdditionalStatus::isPassByForcePossible);
    }

    // -------------------- SETTERS --------------------

    public void setForceMove(boolean forceMove) {
        this.forceMove = forceMove;
    }

    public void setSourceDataset(Dataset sourceDataset) {
        this.sourceDataset = sourceDataset;
    }

    public void setTargetDataverse(Dataverse targetDataverse) {
        this.targetDataverse = targetDataverse;
    }

    // -------------------- INNER CLASSES ---------------------

    private static class Summary {
        private final List<String> summaryParams = new ArrayList<>();

        public Summary add(String param) {
            summaryParams.add(param != null ? param : StringUtils.EMPTY);
            return this;
        }

        public Summary add(MoveDatasetException mde) {
            summaryParams.add(mde.getDetails().stream()
                    .map(AdditionalStatus::getMessageKey)
                    .map(BundleUtil::getStringFromBundle)
                    .collect(Collectors.joining(" ")));
            return this;
        }

        public List<String> getSummaryParams() {
            return summaryParams;
        }
    }
}
