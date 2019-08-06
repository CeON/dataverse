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
import edu.harvard.iq.dataverse.authorization.groups.Group;
import edu.harvard.iq.dataverse.authorization.groups.GroupServiceBean;
import edu.harvard.iq.dataverse.authorization.users.AuthenticatedUser;
import edu.harvard.iq.dataverse.branding.BrandingUtil;
import edu.harvard.iq.dataverse.mail.confirmemail.ConfirmEmailServiceBean;
import edu.harvard.iq.dataverse.notification.NotificationObjectType;
import edu.harvard.iq.dataverse.notification.NotificationType;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.util.BundleUtil;
import edu.harvard.iq.dataverse.util.MailUtil;
import edu.harvard.iq.dataverse.util.SystemConfig;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.apache.commons.lang.StringUtils;
import org.simplejavamail.email.Recipient;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.mail.Message;
import javax.mail.internet.InternetAddress;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static edu.harvard.iq.dataverse.notification.NotificationType.FILESYSTEMIMPORT;

@Stateless
public class MailMessageCreator {

    private SettingsServiceBean settingsService;

    private SystemConfig systemConfig;

    private PermissionServiceBean permissionService;

    private GroupServiceBean groupService;

    private DataverseServiceBean dataverseService;

    private DataFileServiceBean dataFileService;

    private DatasetServiceBean datasetService;

    private DatasetVersionServiceBean versionService;

    private ConfirmEmailServiceBean confirmEmailService;

    private static final Logger logger = Logger.getLogger(MailMessageCreator.class.getCanonicalName());

    @Inject
    public MailMessageCreator(SettingsServiceBean settingsService, SystemConfig systemConfig, PermissionServiceBean permissionService,
                              GroupServiceBean groupService, DataverseServiceBean dataverseService, DataFileServiceBean dataFileService,
                              DatasetServiceBean datasetService, DatasetVersionServiceBean versionService, ConfirmEmailServiceBean confirmEmailService) {
        this.settingsService = settingsService;
        this.systemConfig = systemConfig;
        this.permissionService = permissionService;
        this.groupService = groupService;
        this.dataverseService = dataverseService;
        this.dataFileService = dataFileService;
        this.datasetService = datasetService;
        this.versionService = versionService;
        this.confirmEmailService = confirmEmailService;
    }

    String createMailBodyMessage(String messageText, String rootDataverseName, InternetAddress systemAddress) {

        return messageText + BundleUtil.getStringFromBundle("notification.email.closing",
                                                            Arrays.asList(BrandingUtil.getSupportTeamEmailAddress(systemAddress),
                                                                          BrandingUtil.getSupportTeamName(systemAddress, rootDataverseName)));
    }

    String createRecipientName(String reply, InternetAddress systemAddress) {
        return BundleUtil.getStringFromBundle("contact.delegation", Arrays.asList(
                systemAddress.getPersonal(), reply));
    }

    List<Recipient> createRecipients(String to, String recipientName) {
        return Arrays.stream(to.split(","))
                .map(recipient -> new Recipient(recipientName, recipient, Message.RecipientType.TO))
                .collect(Collectors.toList());
    }

    public Tuple2<String, String> getMessageAndSubject(EmailNotificationDto notificationDto, AuthenticatedUser requestor) {

        if (notificationDto.getNotificationObjectType() == NotificationObjectType.DATAVERSE) {
            Dataverse dataverse = dataverseService.find(notificationDto.getDvObjectId());
            String message = dataverseMessage(notificationDto, dataverse);

        }

        if (notificationDto.getNotificationObjectType() == NotificationObjectType.DATASET) {
            Dataset dataset = datasetService.find(notificationDto.getDvObjectId());
            String message = datasetMessage(notificationDto, dataset);
        }

        if (notificationDto.getNotificationObjectType() == NotificationObjectType.DATASET_VERSION) {
            DatasetVersion datasetVersion = versionService.find(notificationDto.getDvObjectId());
            String message = datasetVersionMessage(notificationDto, datasetVersion, requestor);
        }

        if (notificationDto.getNotificationObjectType() == NotificationObjectType.DATAFILE) {
            DataFile dataFile = dataFileService.find(notificationDto.getDvObjectId());
            String message = dataFileMessage(notificationDto, dataFile, requestor);

        }

        if (notificationDto.getNotificationObjectType() == NotificationObjectType.AUTHENTICATED_USER) {
            AuthenticatedUser user = notificationDto.getUser();
            InternetAddress systemEmail = MailUtil.parseSystemAddress(settingsService.getValueForKey(SettingsServiceBean.Key.SystemEmail));
            String rootDataverseName = dataverseService.findRootDataverse().getName();

            String message = authenticatedUserMessage(notificationDto, rootDataverseName, systemEmail);
        }

        if (notificationDto.getNotificationObjectType() == NotificationObjectType.FILEMETADATA) {
            FileMetadata fileMetadata = dataFileService.findFileMetadata(notificationDto.getDvObjectId());
            String message = fileMetadataMessage(notificationDto, fileMetadata);
        }

        return Tuple.of(StringUtils.EMPTY, StringUtils.EMPTY);
    }

    private String dataverseMessage(EmailNotificationDto notificationDto, Dataverse dataverse) {

        String messageText = BundleUtil.getStringFromBundle("notification.email.greeting");
        String objectType = notificationDto.getNotificationObjectType().toString().toLowerCase();

        switch (notificationDto.getNotificationType()) {
            case ASSIGNROLE:

                String joinedRoleNames = getRoleStringFromUser(notificationDto.getUser(), dataverse);
                String pattern = BundleUtil.getStringFromBundle("notification.email.assignRole");

                messageText += MessageFormat.format(pattern,
                                                    Lists.newArrayList(joinedRoleNames, objectType, dataverse.getDisplayName(), getDataverseLink(dataverse)));

                if (joinedRoleNames.contains("File Downloader")) {
                    pattern = BundleUtil.getStringFromBundle("notification.access.granted.fileDownloader.additionalDataverse");
                    messageText += MessageFormat.format(pattern, " ");
                }

                return messageText;
            case REVOKEROLE:
                messageText += MessageFormat.format(BundleUtil.getStringFromBundle("notification.email.revokeRole"),
                                                    Lists.newArrayList(objectType, dataverse.getDisplayName(), getDataverseLink(dataverse)));
                return messageText;
            case CREATEDV:
                Dataverse parentDataverse = dataverse.getOwner();

                String dataverseCreatedMessage = BundleUtil.getStringFromBundle("notification.email.createDataverse", Arrays.asList(
                        dataverse.getDisplayName(),
                        getDataverseLink(dataverse),
                        parentDataverse != null ? parentDataverse.getDisplayName() : "",
                        parentDataverse != null ? getDataverseLink(parentDataverse) : "",
                        systemConfig.getGuidesBaseUrl(),
                        systemConfig.getGuidesVersion()));

                logger.fine(dataverseCreatedMessage);
                return messageText + dataverseCreatedMessage;
        }

        return StringUtils.EMPTY;
    }

    private String datasetMessage(EmailNotificationDto notificationDto, Dataset dataset) {

        String messageText = BundleUtil.getStringFromBundle("notification.email.greeting");
        String objectType = notificationDto.getNotificationObjectType().toString().toLowerCase();
        String pattern;

        switch (notificationDto.getNotificationType()) {
            case ASSIGNROLE:

                String joinedRoleNames = getRoleStringFromUser(notificationDto.getUser(), dataset);
                pattern = BundleUtil.getStringFromBundle("notification.email.assignRole");

                messageText += MessageFormat.format(pattern,
                                                    Lists.newArrayList(joinedRoleNames, objectType, dataset.getDisplayName(), getDatasetLink(dataset)));

                if (joinedRoleNames.contains("File Downloader")) {
                    pattern = BundleUtil.getStringFromBundle("notification.access.granted.fileDownloader.additionalDataverse");
                    messageText += MessageFormat.format(pattern, " ");
                }

                return messageText;
            case GRANTFILEACCESS:
                pattern = BundleUtil.getStringFromBundle("notification.email.grantFileAccess");
                messageText += MessageFormat.format(pattern,
                                                    Lists.newArrayList(dataset.getDisplayName(), getDatasetLink(dataset)));
                return messageText;
            case REJECTFILEACCESS:
                pattern = BundleUtil.getStringFromBundle("notification.email.rejectFileAccess");
                messageText += MessageFormat.format(pattern,
                                                    Lists.newArrayList(dataset.getDisplayName(), getDatasetLink(dataset)));
                return messageText;
            case CHECKSUMFAIL:
                String checksumFailMsg = BundleUtil.getStringFromBundle("notification.checksumfail", Arrays.asList(
                        dataset.getGlobalIdString()
                ));
                logger.fine("checksumFailMsg: " + checksumFailMsg);
                return messageText + checksumFailMsg;
        }

        return StringUtils.EMPTY;
    }

    private String datasetVersionMessage(EmailNotificationDto notificationDto, DatasetVersion version, AuthenticatedUser requestor) {

        String messageText = BundleUtil.getStringFromBundle("notification.email.greeting");
        String pattern;

        switch (notificationDto.getNotificationType()) {
            case CREATEDS:
                String datasetCreatedMessage = BundleUtil.getStringFromBundle("notification.email.createDataset", Arrays.asList(
                        version.getDataset().getDisplayName(),
                        getDatasetLink(version.getDataset()),
                        version.getDataset().getOwner().getDisplayName(),
                        getDataverseLink(version.getDataset().getOwner()),
                        systemConfig.getGuidesBaseUrl(),
                        systemConfig.getGuidesVersion()
                ));

                return messageText + datasetCreatedMessage;
            case MAPLAYERUPDATED:
                pattern = BundleUtil.getStringFromBundle("notification.email.worldMap.added");

                messageText += MessageFormat.format(pattern, Lists.newArrayList(version.getDataset().getDisplayName(), getDatasetLink(version.getDataset())));
                return messageText;
            case SUBMITTEDDS:

                String requestorName = (requestor.getLastName() != null && requestor.getLastName() != null) ? requestor.getFirstName() + " " + requestor.getLastName() : BundleUtil.getStringFromBundle("notification.email.info.unavailable");
                String requestorEmail = requestor.getEmail() != null ? requestor.getEmail() : BundleUtil.getStringFromBundle("notification.email.info.unavailable");
                pattern = BundleUtil.getStringFromBundle("notification.email.wasSubmittedForReview");

                messageText += MessageFormat.format(pattern,
                                                    Lists.newArrayList(version.getDataset().getDisplayName(), getDatasetDraftLink(version.getDataset()),
                                                                       version.getDataset().getOwner().getDisplayName(), getDataverseLink(version.getDataset().getOwner()),
                                                                       requestorName, requestorEmail));
                return messageText;
            case PUBLISHEDDS:
                pattern = BundleUtil.getStringFromBundle("notification.email.wasPublished");

                messageText += MessageFormat.format(pattern,
                                                    Lists.newArrayList(version.getDataset().getDisplayName(), getDatasetLink(version.getDataset()),
                                                                       version.getDataset().getOwner().getDisplayName(), getDataverseLink(version.getDataset().getOwner())));
                return messageText;
            case RETURNEDDS:
                pattern = BundleUtil.getStringFromBundle("notification.email.wasReturnedByReviewer");

                messageText += MessageFormat.format(pattern,
                                                    Lists.newArrayList(version.getDataset().getDisplayName(), getDatasetDraftLink(version.getDataset()),
                                                                       version.getDataset().getOwner().getDisplayName(), getDataverseLink(version.getDataset().getOwner()), ""));
                return messageText;
            case FILESYSTEMIMPORT:

                String fileImportMsg = BundleUtil.getStringFromBundle("notification.mail.import.filesystem", Arrays.asList(
                        systemConfig.getDataverseSiteUrl(),
                        version.getDataset().getGlobalIdString(),
                        version.getDataset().getDisplayName()
                ));

                return messageText + fileImportMsg;

            case CHECKSUMIMPORT:

                String checksumImportMsg = BundleUtil.getStringFromBundle("notification.import.checksum", Arrays.asList(
                        version.getDataset().getGlobalIdString(),
                        version.getDataset().getDisplayName()
                ));

                return messageText + checksumImportMsg;
        }
        return StringUtils.EMPTY;
    }

    private String dataFileMessage(EmailNotificationDto notificationDto, DataFile dataFile, AuthenticatedUser requestor) {
        String messageText = BundleUtil.getStringFromBundle("notification.email.greeting");

        if (notificationDto.getNotificationType() == NotificationType.REQUESTFILEACCESS) {

            String pattern = BundleUtil.getStringFromBundle("notification.email.requestFileAccess");
            String requestorName = (requestor.getLastName() != null && requestor.getLastName() != null) ? requestor.getFirstName() + " " + requestor.getLastName() : BundleUtil.getStringFromBundle("notification.email.info.unavailable");
            String requestorEmail = requestor.getEmail() != null ? requestor.getEmail() : BundleUtil.getStringFromBundle("notification.email.info.unavailable");

            messageText += MessageFormat.format(pattern,
                                                Lists.newArrayList(dataFile.getOwner().getDisplayName(), requestorName,
                                                                   requestorEmail, getDatasetManageFileAccessLink(dataFile)));
            return messageText;
        }
        return StringUtils.EMPTY;
    }

    private String fileMetadataMessage(EmailNotificationDto notificationDto, FileMetadata fileMetadata) {
        String messageText = BundleUtil.getStringFromBundle("notification.email.greeting");

        if (notificationDto.getNotificationType() == NotificationType.MAPLAYERDELETEFAILED) {

            DatasetVersion version = fileMetadata.getDatasetVersion();
            String pattern = BundleUtil.getStringFromBundle("notification.email.maplayer.deletefailed.text");

            String[] paramArrayMapLayerDelete = {fileMetadata.getLabel(), getDatasetLink(version.getDataset())};
            messageText += MessageFormat.format(pattern, paramArrayMapLayerDelete);
            return messageText;
        }
        return StringUtils.EMPTY;
    }

    private String authenticatedUserMessage(EmailNotificationDto notificationDto, String rootDataverseName, InternetAddress systemAddress) {
        String messageText = BundleUtil.getStringFromBundle("notification.email.greeting");

        if (notificationDto.getNotificationType() == NotificationType.CREATEACC) {

            String accountCreatedMessage = BundleUtil.getStringFromBundle("notification.email.welcome", Arrays.asList(
                    rootDataverseName,
                    systemConfig.getGuidesBaseUrl(),
                    systemConfig.getGuidesVersion(),
                    BrandingUtil.getSupportTeamName(systemAddress, rootDataverseName),
                    BrandingUtil.getSupportTeamEmailAddress(systemAddress)
            ));
            String optionalConfirmEmailAddon = confirmEmailService.optionalConfirmEmailAddonMsg(notificationDto.getUser());
            accountCreatedMessage += optionalConfirmEmailAddon;
            logger.fine("accountCreatedMessage: " + accountCreatedMessage);
            return messageText + accountCreatedMessage;
        }

        return StringUtils.EMPTY;
    }

    private String getSubjectTextForDatasetVersion(NotificationType notificationType, String rootDataverseName, DatasetVersion datasetVersion) {

        if (notificationType == FILESYSTEMIMPORT) {
            try {
                List<String> dsNameAsList = Collections.singletonList(datasetVersion.getDataset().getDisplayName());
                return BundleUtil.getStringFromBundle("notification.email.import.filesystem.subject", dsNameAsList);
            } catch (Exception e) {
                return BundleUtil.getStringFromBundle("notification.email.import.filesystem.subject", Collections.singletonList(rootDataverseName));
            }
        }

        return StringUtils.EMPTY;
    }

    public String getSubjectText(NotificationType notificationType, String rootDataverseName) {
        List<String> rootDvNameAsList = Collections.singletonList(rootDataverseName);
        switch (notificationType) {
            case ASSIGNROLE:
                return BundleUtil.getStringFromBundle("notification.email.assign.role.subject", rootDvNameAsList);
            case REVOKEROLE:
                return BundleUtil.getStringFromBundle("notification.email.revoke.role.subject", rootDvNameAsList);
            case CREATEDV:
                return BundleUtil.getStringFromBundle("notification.email.create.dataverse.subject", rootDvNameAsList);
            case REQUESTFILEACCESS:
                return BundleUtil.getStringFromBundle("notification.email.request.file.access.subject", rootDvNameAsList);
            case GRANTFILEACCESS:
                return BundleUtil.getStringFromBundle("notification.email.grant.file.access.subject", rootDvNameAsList);
            case REJECTFILEACCESS:
                return BundleUtil.getStringFromBundle("notification.email.rejected.file.access.subject", rootDvNameAsList);
            case MAPLAYERUPDATED:
                return BundleUtil.getStringFromBundle("notification.email.update.maplayer", rootDvNameAsList);
            case MAPLAYERDELETEFAILED:
                return BundleUtil.getStringFromBundle("notification.email.maplayer.deletefailed.subject", rootDvNameAsList);
            case CREATEDS:
                return BundleUtil.getStringFromBundle("notification.email.create.dataset.subject", rootDvNameAsList);
            case SUBMITTEDDS:
                return BundleUtil.getStringFromBundle("notification.email.submit.dataset.subject", rootDvNameAsList);
            case PUBLISHEDDS:
                return BundleUtil.getStringFromBundle("notification.email.publish.dataset.subject", rootDvNameAsList);
            case RETURNEDDS:
                return BundleUtil.getStringFromBundle("notification.email.returned.dataset.subject", rootDvNameAsList);
            case CREATEACC:
                return BundleUtil.getStringFromBundle("notification.email.create.account.subject", rootDvNameAsList);
            case CHECKSUMFAIL:
                return BundleUtil.getStringFromBundle("notification.email.checksumfail.subject", rootDvNameAsList);
            case CHECKSUMIMPORT:
                return BundleUtil.getStringFromBundle("notification.email.import.checksum.subject", rootDvNameAsList);
            case CONFIRMEMAIL:
                return BundleUtil.getStringFromBundle("notification.email.verifyEmail.subject", rootDvNameAsList);
        }
        return StringUtils.EMPTY;
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

}
