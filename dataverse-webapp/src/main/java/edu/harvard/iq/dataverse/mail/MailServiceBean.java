/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.harvard.iq.dataverse.mail;

import com.google.common.collect.Lists;
import edu.harvard.iq.dataverse.DataFile;
import edu.harvard.iq.dataverse.DataFileServiceBean;
import edu.harvard.iq.dataverse.Dataset;
import edu.harvard.iq.dataverse.DatasetServiceBean;
import edu.harvard.iq.dataverse.DatasetVersion;
import edu.harvard.iq.dataverse.DatasetVersionServiceBean;
import edu.harvard.iq.dataverse.Dataverse;
import edu.harvard.iq.dataverse.DataverseServiceBean;
import edu.harvard.iq.dataverse.DvObject;
import edu.harvard.iq.dataverse.FileMetadata;
import edu.harvard.iq.dataverse.PermissionServiceBean;
import edu.harvard.iq.dataverse.RoleAssignment;
import edu.harvard.iq.dataverse.UserNotification;
import edu.harvard.iq.dataverse.authorization.groups.Group;
import edu.harvard.iq.dataverse.authorization.groups.GroupServiceBean;
import edu.harvard.iq.dataverse.authorization.users.AuthenticatedUser;
import edu.harvard.iq.dataverse.branding.BrandingUtil;
import edu.harvard.iq.dataverse.confirmemail.ConfirmEmailServiceBean;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean.Key;
import edu.harvard.iq.dataverse.util.BundleUtil;
import edu.harvard.iq.dataverse.util.MailUtil;
import edu.harvard.iq.dataverse.util.SystemConfig;
import io.vavr.control.Try;
import org.apache.commons.lang.StringUtils;
import org.simplejavamail.email.Email;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.mailer.Mailer;
import org.simplejavamail.mailer.MailerBuilder;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * original author: roberttreacy
 */
@Stateless
public class MailServiceBean implements java.io.Serializable {

    @EJB
    DataverseServiceBean dataverseService;
    @EJB
    DataFileServiceBean dataFileService;
    @EJB
    DatasetServiceBean datasetService;
    @EJB
    DatasetVersionServiceBean versionService;
    @EJB
    SystemConfig systemConfig;
    @EJB
    SettingsServiceBean settingsService;
    @EJB
    PermissionServiceBean permissionService;
    @EJB
    GroupServiceBean groupService;
    @EJB
    ConfirmEmailServiceBean confirmEmailService;

    @Inject
    private MailMessageCreator mailMessageCreator;

    private Mailer mailSender;

    private static final Logger logger = Logger.getLogger(MailServiceBean.class.getCanonicalName());

    private static final String charset = "UTF-8";

    /**
     * Creates a new instance of MailServiceBean
     */
    public MailServiceBean() {
    }

    @Resource(name = "mail/notifyMailSession")
    private Session session;

    // -------------------- CONSTRUCTORS --------------------

    @PostConstruct
    public void prepareMailSession() {
        mailSender = MailerBuilder
                .usingSession(session)
                .withDebugLogging(true)
                .buildMailer();
    }

    // -------------------- LOGIC --------------------

    public Boolean sendNotificationEmail(UserNotification notification, String comment, AuthenticatedUser requestor) {

        boolean retval = false;
        String emailAddress = getUserEmailAddress(notification);

        Object objectOfNotification = getObjectOfNotification(notification);

        if (objectOfNotification != null) {
            String messageText = getMessageTextBasedOnNotification(notification, objectOfNotification, comment, requestor);
            String rootDataverseName = dataverseService.findRootDataverse().getName();
            String subjectText = MailUtil.getSubjectTextBasedOnNotification(notification, rootDataverseName, objectOfNotification);
            if (!(messageText.isEmpty() || subjectText.isEmpty())) {
                retval = sendSystemEmail(emailAddress, subjectText, messageText);
            } else {
                logger.warning("Skipping " + notification.getType() + " notification, because couldn't get valid message");
            }
        } else {
            logger.warning("Skipping " + notification.getType() + " notification, because no valid Object was found");
        }

        return retval;
    }

    public boolean sendSystemEmail(String to, String subject, String messageText) {

        String message = mailMessageCreator.createMailBodyMessage(messageText, dataverseService.findRootDataverse().getName(), getSystemAddress());

        Email email = EmailBuilder.startingBlank()
                .from(getSystemAddress())
                .withRecipients(mailMessageCreator.createRecipients(to, ""))
                .withSubject(subject)
                .appendText(message)
                .buildEmail();

        return Try.run(() -> mailSender.sendMail(email))
                .map(emailSent -> true)
                .onFailure(Throwable::printStackTrace)
                .getOrElse(false);
    }

    public boolean sendMail(String reply, String to, String subject, String messageText) {

        Email email = EmailBuilder.startingBlank()
                .from(getSystemAddress())
                .withRecipients(mailMessageCreator.createRecipients(to, mailMessageCreator.createRecipientName(reply, getSystemAddress())))
                .withSubject(subject)
                .withReplyTo(reply)
                .appendText(messageText)
                .buildEmail();

        return Try.run(() -> mailSender.sendMail(email))
                .map(emailSent -> true)
                .onFailure(Throwable::printStackTrace)
                .getOrElse(false);
    }

    private InternetAddress getSystemAddress() {
        String systemEmail = settingsService.getValueForKey(Key.SystemEmail);
        return MailUtil.parseSystemAddress(systemEmail);
    }

    private String getDatasetManageFileAccessLink(DataFile datafile) {
        return systemConfig.getDataverseSiteUrl() + "/permissions-manage-files.xhtml?id=" + datafile.getOwner().getId();
    }

    private String getDatasetLink(Dataset dataset) {
        return systemConfig.getDataverseSiteUrl() + "/dataset.xhtml?persistentId=" + dataset.getGlobalIdString();
    }

    private String getDatasetDraftLink(Dataset dataset) {
        return systemConfig.getDataverseSiteUrl() + "/dataset.xhtml?persistentId=" + dataset.getGlobalIdString() + "&version=DRAFT" + "&faces-redirect=true";
    }

    private String getDataverseLink(Dataverse dataverse) {
        return systemConfig.getDataverseSiteUrl() + "/dataverse/" + dataverse.getAlias();
    }

    /**
     * Returns a '/'-separated string of roles that are effective for {@code au}
     * over {@code dvObj}. Traverses the containment hierarchy of the {@code d}.
     * Takes into consideration all groups that {@code au} is part of.
     *
     * @param au    The authenticated user whose role assignments we look for.
     * @param dvObj The Dataverse object over which the roles are assigned
     * @return A set of all the role assignments for {@code ra} over {@code d}.
     */
    private String getRoleStringFromUser(AuthenticatedUser au, DvObject dvObj) {
        // Find user's role(s) for given dataverse/dataset
        Set<RoleAssignment> roles = permissionService.assignmentsFor(au, dvObj);
        List<String> roleNames = new ArrayList<>();

        // Include roles derived from a user's groups
        Set<Group> groupsUserBelongsTo = groupService.groupsFor(au, dvObj);
        for (Group g : groupsUserBelongsTo) {
            roles.addAll(permissionService.assignmentsFor(g, dvObj));
        }

        for (RoleAssignment ra : roles) {
            roleNames.add(ra.getRole().getName());
        }
        return StringUtils.join(roleNames, "/");
    }

    /**
     * Returns the URL to a given {@code DvObject} {@code d}. If {@code d} is a
     * {@code DataFile}, return a link to its {@code DataSet}.
     *
     * @param d The Dataverse object to get a link for.
     * @return A string with a URL to the given Dataverse object.
     */
    private String getDvObjectLink(DvObject d) {
        if (d instanceof Dataverse) {
            return getDataverseLink((Dataverse) d);
        } else if (d instanceof Dataset) {
            return getDatasetLink((Dataset) d);
        } else if (d instanceof DataFile) {
            return getDatasetLink(((DataFile) d).getOwner());
        }
        return "";
    }

    /**
     * Returns string representation of the type of {@code DvObject} {@code d}.
     *
     * @param d The Dataverse object to get the string for
     * @return A string that represents the type of a given Dataverse object.
     */
    private String getDvObjectTypeString(DvObject d) {
        if (d instanceof Dataverse) {
            return "dataverse";
        } else if (d instanceof Dataset) {
            return "dataset";
        } else if (d instanceof DataFile) {
            return "data file";
        }
        return "";
    }

    private String getMessageTextBasedOnNotification(UserNotification userNotification, Object targetObject, String comment, AuthenticatedUser requestor) {

        String messageText = BundleUtil.getStringFromBundle("notification.email.greeting");
        DatasetVersion version;
        Dataset dataset;
        DvObject dvObj;
        String dvObjURL;
        String dvObjTypeStr;
        String pattern;

        switch (userNotification.getType()) {
            case ASSIGNROLE:
                AuthenticatedUser au = userNotification.getUser();
                dvObj = (DvObject) targetObject;

                String joinedRoleNames = getRoleStringFromUser(au, dvObj);

                dvObjURL = getDvObjectLink(dvObj);
                dvObjTypeStr = getDvObjectTypeString(dvObj);

                pattern = BundleUtil.getStringFromBundle("notification.email.assignRole");
                List<String> paramArrayAssignRole = Lists.newArrayList(joinedRoleNames, dvObjTypeStr, dvObj.getDisplayName(), dvObjURL);
                messageText += MessageFormat.format(pattern, paramArrayAssignRole);
                if (joinedRoleNames.contains("File Downloader")) {
                    if (dvObjTypeStr.equals("dataset")) {
                        pattern = BundleUtil.getStringFromBundle("notification.access.granted.fileDownloader.additionalDataset");
                        String[] paramArrayAssignRoleDS = {" "};
                        messageText += MessageFormat.format(pattern, paramArrayAssignRoleDS);
                    }
                    if (dvObjTypeStr.equals("dataverse")) {
                        pattern = BundleUtil.getStringFromBundle("notification.access.granted.fileDownloader.additionalDataverse");
                        String[] paramArrayAssignRoleDV = {" "};
                        messageText += MessageFormat.format(pattern, paramArrayAssignRoleDV);
                    }
                }
                return messageText;
            case REVOKEROLE:
                dvObj = (DvObject) targetObject;

                dvObjURL = getDvObjectLink(dvObj);
                dvObjTypeStr = getDvObjectTypeString(dvObj);

                pattern = BundleUtil.getStringFromBundle("notification.email.revokeRole");
                String[] paramArrayRevokeRole = {dvObjTypeStr, dvObj.getDisplayName(), dvObjURL};
                messageText += MessageFormat.format(pattern, paramArrayRevokeRole);
                return messageText;
            case CREATEDV:
                Dataverse dataverse = (Dataverse) targetObject;
                Dataverse parentDataverse = dataverse.getOwner();
                // initialize to empty string in the rare case that there is no parent dataverse (i.e. root dataverse just created)
                String parentDataverseDisplayName = "";
                String parentDataverseUrl = "";
                if (parentDataverse != null) {
                    parentDataverseDisplayName = parentDataverse.getDisplayName();
                    parentDataverseUrl = getDataverseLink(parentDataverse);
                }
                String dataverseCreatedMessage = BundleUtil.getStringFromBundle("notification.email.createDataverse", Arrays.asList(
                        dataverse.getDisplayName(),
                        getDataverseLink(dataverse),
                        parentDataverseDisplayName,
                        parentDataverseUrl,
                        systemConfig.getGuidesBaseUrl(),
                        systemConfig.getGuidesVersion()));
                logger.fine(dataverseCreatedMessage);
                return messageText += dataverseCreatedMessage;
            case REQUESTFILEACCESS:
                DataFile datafile = (DataFile) targetObject;
                pattern = BundleUtil.getStringFromBundle("notification.email.requestFileAccess");
                String requestorName = (requestor.getLastName() != null && requestor.getLastName() != null) ? requestor.getFirstName() + " " + requestor.getLastName() : BundleUtil.getStringFromBundle("notification.email.info.unavailable");
                String requestorEmail = requestor.getEmail() != null ? requestor.getEmail() : BundleUtil.getStringFromBundle("notification.email.info.unavailable");
                String[] paramArrayRequestFileAccess = {datafile.getOwner().getDisplayName(), requestorName, requestorEmail, getDatasetManageFileAccessLink(datafile)};
                messageText += MessageFormat.format(pattern, paramArrayRequestFileAccess);
                return messageText;
            case GRANTFILEACCESS:
                dataset = (Dataset) targetObject;
                pattern = BundleUtil.getStringFromBundle("notification.email.grantFileAccess");
                String[] paramArrayGrantFileAccess = {dataset.getDisplayName(), getDatasetLink(dataset)};
                messageText += MessageFormat.format(pattern, paramArrayGrantFileAccess);
                return messageText;
            case REJECTFILEACCESS:
                dataset = (Dataset) targetObject;
                pattern = BundleUtil.getStringFromBundle("notification.email.rejectFileAccess");
                String[] paramArrayRejectFileAccess = {dataset.getDisplayName(), getDatasetLink(dataset)};
                messageText += MessageFormat.format(pattern, paramArrayRejectFileAccess);
                return messageText;
            case CREATEDS:
                version = (DatasetVersion) targetObject;
                String datasetCreatedMessage = BundleUtil.getStringFromBundle("notification.email.createDataset", Arrays.asList(
                        version.getDataset().getDisplayName(),
                        getDatasetLink(version.getDataset()),
                        version.getDataset().getOwner().getDisplayName(),
                        getDataverseLink(version.getDataset().getOwner()),
                        systemConfig.getGuidesBaseUrl(),
                        systemConfig.getGuidesVersion()
                ));
                logger.fine(datasetCreatedMessage);
                return messageText += datasetCreatedMessage;
            case MAPLAYERUPDATED:
                version = (DatasetVersion) targetObject;
                pattern = BundleUtil.getStringFromBundle("notification.email.worldMap.added");
                String[] paramArrayMapLayer = {version.getDataset().getDisplayName(), getDatasetLink(version.getDataset())};
                messageText += MessageFormat.format(pattern, paramArrayMapLayer);
                return messageText;
            case MAPLAYERDELETEFAILED:
                FileMetadata targetFileMetadata = (FileMetadata) targetObject;
                version = targetFileMetadata.getDatasetVersion();
                pattern = BundleUtil.getStringFromBundle("notification.email.maplayer.deletefailed.text");
                String[] paramArrayMapLayerDelete = {targetFileMetadata.getLabel(), getDatasetLink(version.getDataset())};
                messageText += MessageFormat.format(pattern, paramArrayMapLayerDelete);
                return messageText;
            case SUBMITTEDDS:
                version = (DatasetVersion) targetObject;
                String mightHaveSubmissionComment = "";              
                /*
                FIXME
                Setting up to add single comment when design completed
                "submissionComment" needs to be added to Bundle
                mightHaveSubmissionComment = ".";
                if (comment != null && !comment.isEmpty()) {
                    mightHaveSubmissionComment = ".\n\n" + BundleUtil.getStringFromBundle("submissionComment") + "\n\n" + comment;
                }
                */
                requestorName = (requestor.getLastName() != null && requestor.getLastName() != null) ? requestor.getFirstName() + " " + requestor.getLastName() : BundleUtil.getStringFromBundle("notification.email.info.unavailable");
                requestorEmail = requestor.getEmail() != null ? requestor.getEmail() : BundleUtil.getStringFromBundle("notification.email.info.unavailable");
                pattern = BundleUtil.getStringFromBundle("notification.email.wasSubmittedForReview");

                String[] paramArraySubmittedDataset = {version.getDataset().getDisplayName(), getDatasetDraftLink(version.getDataset()),
                        version.getDataset().getOwner().getDisplayName(), getDataverseLink(version.getDataset().getOwner()),
                        requestorName, requestorEmail};
                messageText += MessageFormat.format(pattern, paramArraySubmittedDataset);
                return messageText;
            case PUBLISHEDDS:
                version = (DatasetVersion) targetObject;
                pattern = BundleUtil.getStringFromBundle("notification.email.wasPublished");
                String[] paramArrayPublishedDataset = {version.getDataset().getDisplayName(), getDatasetLink(version.getDataset()),
                        version.getDataset().getOwner().getDisplayName(), getDataverseLink(version.getDataset().getOwner())};
                messageText += MessageFormat.format(pattern, paramArrayPublishedDataset);
                return messageText;
            case RETURNEDDS:
                version = (DatasetVersion) targetObject;
                pattern = BundleUtil.getStringFromBundle("notification.email.wasReturnedByReviewer");

                String optionalReturnReason = "";
                /*
                FIXME
                Setting up to add single comment when design completed
                optionalReturnReason = ".";
                if (comment != null && !comment.isEmpty()) {
                    optionalReturnReason = ".\n\n" + BundleUtil.getStringFromBundle("wasReturnedReason") + "\n\n" + comment;
                }
                */
                String[] paramArrayReturnedDataset = {version.getDataset().getDisplayName(), getDatasetDraftLink(version.getDataset()),
                        version.getDataset().getOwner().getDisplayName(), getDataverseLink(version.getDataset().getOwner()), optionalReturnReason};
                messageText += MessageFormat.format(pattern, paramArrayReturnedDataset);
                return messageText;
            case CREATEACC:
                String rootDataverseName = dataverseService.findRootDataverse().getName();
                InternetAddress systemAddress = getSystemAddress();
                String accountCreatedMessage = BundleUtil.getStringFromBundle("notification.email.welcome", Arrays.asList(
                        BrandingUtil.getInstallationBrandName(rootDataverseName),
                        systemConfig.getGuidesBaseUrl(),
                        systemConfig.getGuidesVersion(),
                        BrandingUtil.getSupportTeamName(systemAddress, rootDataverseName),
                        BrandingUtil.getSupportTeamEmailAddress(systemAddress)
                ));
                String optionalConfirmEmailAddon = confirmEmailService.optionalConfirmEmailAddonMsg(userNotification.getUser());
                accountCreatedMessage += optionalConfirmEmailAddon;
                logger.fine("accountCreatedMessage: " + accountCreatedMessage);
                return messageText += accountCreatedMessage;

            case CHECKSUMFAIL:
                dataset = (Dataset) targetObject;
                String checksumFailMsg = BundleUtil.getStringFromBundle("notification.checksumfail", Arrays.asList(
                        dataset.getGlobalIdString()
                ));
                logger.fine("checksumFailMsg: " + checksumFailMsg);
                return messageText += checksumFailMsg;

            case FILESYSTEMIMPORT:
                version = (DatasetVersion) targetObject;
                String fileImportMsg = BundleUtil.getStringFromBundle("notification.mail.import.filesystem", Arrays.asList(
                        systemConfig.getDataverseSiteUrl(),
                        version.getDataset().getGlobalIdString(),
                        version.getDataset().getDisplayName()
                ));
                logger.fine("fileImportMsg: " + fileImportMsg);
                return messageText += fileImportMsg;

            case CHECKSUMIMPORT:
                version = (DatasetVersion) targetObject;
                String checksumImportMsg = BundleUtil.getStringFromBundle("notification.import.checksum", Arrays.asList(
                        version.getDataset().getGlobalIdString(),
                        version.getDataset().getDisplayName()
                ));
                logger.fine("checksumImportMsg: " + checksumImportMsg);
                return messageText += checksumImportMsg;

        }

        return "";
    }

    private Object getObjectOfNotification(UserNotification userNotification) {
        switch (userNotification.getType()) {
            case ASSIGNROLE:
            case REVOKEROLE:
                // Can either be a dataverse or dataset, so search both
                Dataverse dataverse = dataverseService.find(userNotification.getObjectId());
                if (dataverse != null) {
                    return dataverse;
                }

                return datasetService.find(userNotification.getObjectId());
            case CREATEDV:
                return dataverseService.find(userNotification.getObjectId());
            case REQUESTFILEACCESS:
                return dataFileService.find(userNotification.getObjectId());
            case GRANTFILEACCESS:
            case REJECTFILEACCESS:
            case CHECKSUMFAIL:
                return datasetService.find(userNotification.getObjectId());
            case MAPLAYERDELETEFAILED:
                return dataFileService.findFileMetadata(userNotification.getObjectId());
            case MAPLAYERUPDATED:
            case CREATEDS:
            case SUBMITTEDDS:
            case PUBLISHEDDS:
            case RETURNEDDS:
            case FILESYSTEMIMPORT:
            case CHECKSUMIMPORT:
                return versionService.find(userNotification.getObjectId());
            case CREATEACC:
                return userNotification.getUser();
        }
        return null;
    }

    private String getUserEmailAddress(UserNotification notification) {
        if (notification != null) {
            if (notification.getUser() != null) {
                if (notification.getUser().getDisplayInfo() != null) {
                    if (notification.getUser().getDisplayInfo().getEmailAddress() != null) {
                        logger.fine("Email address: " + notification.getUser().getDisplayInfo().getEmailAddress());
                        return notification.getUser().getDisplayInfo().getEmailAddress();
                    }
                }
            }
        }

        logger.fine("no email address");
        return null;
    }

}
