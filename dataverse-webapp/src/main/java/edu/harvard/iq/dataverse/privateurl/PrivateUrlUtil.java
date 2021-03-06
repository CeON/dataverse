package edu.harvard.iq.dataverse.privateurl;

import edu.harvard.iq.dataverse.engine.command.Command;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.persistence.DvObject;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.user.Permission;
import edu.harvard.iq.dataverse.persistence.user.PrivateUrlUser;
import edu.harvard.iq.dataverse.persistence.user.RoleAssignee;
import edu.harvard.iq.dataverse.persistence.user.RoleAssignment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Static, testable methods with no runtime dependencies.
 */
public class PrivateUrlUtil {

    private static final Logger logger = Logger.getLogger(PrivateUrlUtil.class.getCanonicalName());

    /**
     * Use of this method should be limited to
     * RoleAssigneeServiceBean.getRoleAssignee. If you have the
     * {@link RoleAssignment} in your hand, just instantiate a
     * {@link PrivateUrlUser} using the definitionPoint.
     *
     * @param identifier For example, "#42". The identifier is expected to start
     *                   with "#" (the namespace for a PrivateUrlUser and its corresponding
     *                   RoleAssignment) and end with the dataset id.
     * @return A valid PrivateUrlUser (which like any User or Group is a
     * RoleAssignee) if a valid identifier is provided or null.
     */
    public static RoleAssignee identifier2roleAssignee(String identifier) {
        String[] parts = identifier.split(PrivateUrlUser.PREFIX);
        long datasetId;
        try {
            datasetId = new Long(parts[1]);
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException ex) {
            logger.fine("Could not find dataset id in '" + identifier + "': " + ex);
            return null;
        }
        return new PrivateUrlUser(datasetId);
    }

    /**
     * @todo If there is a use case for this outside the context of Private URL,
     * move this method to somewhere more centralized.
     */
    static Dataset getDatasetFromRoleAssignment(RoleAssignment roleAssignment) {
        if (roleAssignment == null) {
            return null;
        }
        DvObject dvObject = roleAssignment.getDefinitionPoint();
        if (dvObject == null) {
            return null;
        }
        if (dvObject instanceof Dataset) {
            return (Dataset) roleAssignment.getDefinitionPoint();
        } else {
            return null;
        }
    }

    /**
     * @return DatasetVersion if a draft or null.
     * @todo If there is a use case for this outside the context of Private URL,
     * move this method to somewhere more centralized.
     */
    static public DatasetVersion getLatestDatasetVersionFromRoleAssignment(RoleAssignment roleAssignment) {
        if (roleAssignment == null) {
            return null;
        }
        Dataset dataset = getDatasetFromRoleAssignment(roleAssignment);
        if (dataset != null) {
            return dataset.getLatestVersion();
        }
        logger.fine("Couldn't find latest version, returning null");
        return null;
    }

    static public PrivateUrlUser getPrivateUrlUserFromRoleAssignment(RoleAssignment roleAssignment) {
        if (roleAssignment == null) {
            return null;
        }
        Dataset dataset = getDatasetFromRoleAssignment(roleAssignment);
        if (dataset != null) {
            PrivateUrlUser privateUrlUser = new PrivateUrlUser(dataset.getId());
            return privateUrlUser;
        }
        return null;
    }

    /**
     * @param roleAssignment
     * @return PrivateUrlRedirectData or null.
     * @todo Show the Exception to the user?
     */
    public static PrivateUrlRedirectData getPrivateUrlRedirectData(RoleAssignment roleAssignment) {
        PrivateUrlUser privateUrlUser = PrivateUrlUtil.getPrivateUrlUserFromRoleAssignment(roleAssignment);
        String datasetPageToBeRedirectedTo = PrivateUrlUtil.getLatestVersionDatasetPageToBeRedirectedTo(roleAssignment);
        try {
            return new PrivateUrlRedirectData(privateUrlUser, datasetPageToBeRedirectedTo);
        } catch (Exception ex) {
            logger.log(Level.INFO, "Exception caught trying to instantiate PrivateUrlRedirectData: " + ex.getMessage(), ex);
            return null;
        }
    }

    /**
     * Returns a relative URL or "UNKNOWN."
     */
    static String getLatestVersionDatasetPageToBeRedirectedTo(RoleAssignment roleAssignment) {
        DatasetVersion datasetVersion = getLatestDatasetVersionFromRoleAssignment(roleAssignment);
        return getLatestVersionUrl(datasetVersion);
    }

    /**
     * Returns a relative URL or "UNKNOWN."
     */
    static String getLatestVersionUrl(DatasetVersion datasetVersion) {
        if (datasetVersion != null) {
            Dataset dataset = datasetVersion.getDataset();
            if (dataset != null) {
                if (dataset.getGlobalId().isComplete()) {
                    String relativeUrl = "/dataset.xhtml?persistentId=" + dataset.getGlobalId().toString() + "&version=" + datasetVersion.getFriendlyVersionNumber();
                    return relativeUrl;
                }
            }
        }
        return "UNKNOWN";
    }

    static PrivateUrl getPrivateUrlFromRoleAssignment(RoleAssignment roleAssignment, String dataverseSiteUrl) {
        if (dataverseSiteUrl == null) {
            logger.info("dataverseSiteUrl was null. Can not instantiate a PrivateUrl object.");
            return null;
        }
        Dataset dataset = PrivateUrlUtil.getDatasetFromRoleAssignment(roleAssignment);
        if (dataset != null) {
            PrivateUrl privateUrl = new PrivateUrl(roleAssignment, dataset, dataverseSiteUrl);
            return privateUrl;
        } else {
            return null;
        }
    }

    static PrivateUrlUser getPrivateUrlUserFromRoleAssignment(RoleAssignment roleAssignment, RoleAssignee roleAssignee) {
        if (roleAssignment != null) {
            if (roleAssignee instanceof PrivateUrlUser) {
                return (PrivateUrlUser) roleAssignee;
            }
        }
        return null;
    }

    /**
     * @return A list of the CamelCase "names" of required permissions, not the
     * human-readable equivalents.
     * @todo Move this to somewhere more central.
     */
    public static List<String> getRequiredPermissions(CommandException ex) {
        Map<String, Set<Permission>> permissions = Optional.ofNullable(ex.getFailedCommand())
                .map(Command::getRequiredPermissions)
                .orElse(Collections.emptyMap());
        return permissions.values()
                .stream()
                .flatMap(Set::stream)
                .map(Permission::name)
                .collect(Collectors.toList());
    }

}
