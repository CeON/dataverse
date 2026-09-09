package edu.harvard.iq.dataverse;

import static com.google.common.collect.Sets.newHashSet;
import static edu.harvard.iq.dataverse.common.BundleUtil.getStringFromBundle;
import static edu.harvard.iq.dataverse.engine.command.CommandHelper.CH;
import static edu.harvard.iq.dataverse.persistence.dataset.DatasetLock.Reason.DcmUpload;
import static edu.harvard.iq.dataverse.persistence.dataset.DatasetLock.Reason.Ingest;
import static edu.harvard.iq.dataverse.persistence.dataset.DatasetLock.Reason.Workflow;
import static edu.harvard.iq.dataverse.persistence.dataset.DatasetLock.Reason.pidRegister;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ejb.Stateless;
import javax.inject.Inject;

import org.slf4j.Logger;

import edu.harvard.iq.dataverse.authorization.groups.GroupServiceBean;
import edu.harvard.iq.dataverse.engine.command.Command;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.engine.command.exception.IllegalCommandException;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDatasetVersionCommand;
import edu.harvard.iq.dataverse.mail.confirmemail.ConfirmEmailServiceBean;
import edu.harvard.iq.dataverse.persistence.DvObject;
import edu.harvard.iq.dataverse.persistence.DvObjectContainer;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.group.Group;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.GuestUser;
import edu.harvard.iq.dataverse.persistence.user.Permission;
import edu.harvard.iq.dataverse.persistence.user.RoleAssignee;
import edu.harvard.iq.dataverse.persistence.user.RoleAssignment;
import edu.harvard.iq.dataverse.persistence.user.RoleAssignmentRepository;
import edu.harvard.iq.dataverse.persistence.user.User;
import edu.harvard.iq.dataverse.util.SystemConfig;

/**
 * Your one-stop-shop for deciding which user can do what action on which
 * objects (TM). Note that this bean accesses the permissions/user assignment on
 * a read-only basis. Changing the permissions a user has is done via roles and
 * ras, over at {@link DataverseRoleServiceBean}.
 *
 * @author michael
 */
@Stateless
public class PermissionServiceBean {
    private static final Logger logger = getLogger(PermissionServiceBean.class);

    private DataverseRoleServiceBean roleService;
    private RoleAssigneeServiceBean roleAssigneeService;
    private DataverseDao dataverseDao;
    private DvObjectServiceBean dvObjectServiceBean;
    private GroupServiceBean groupService;
    private SystemConfig systemConfig;
    private ConfirmEmailServiceBean confirmEmailService;
    private RoleAssignmentRepository roleAssignmentRepository;

    // -------------------- CONSTRUCTORS --------------------

    public PermissionServiceBean() { }

    @Inject
    public PermissionServiceBean(final DataverseRoleServiceBean roleService, 
            final RoleAssigneeServiceBean roleAssigneeService,
            final DataverseDao dataverseDao, 
            final DvObjectServiceBean dvObjectServiceBean,
            final GroupServiceBean groupService, 
            final SystemConfig systemConfig,
            final ConfirmEmailServiceBean confirmEmailService, 
            final RoleAssignmentRepository roleAssignmentRepository) {
    	
        this.roleService = roleService;
        this.roleAssigneeService = roleAssigneeService;
        this.dataverseDao = dataverseDao;
        this.dvObjectServiceBean = dvObjectServiceBean;
        this.groupService = groupService;
        this.systemConfig = systemConfig;
        this.confirmEmailService = confirmEmailService;
        this.roleAssignmentRepository = roleAssignmentRepository;
    }

    // -------------------- LOGIC --------------------

    public List<RoleAssignment> assignmentsOn(DvObject dvObject) {
        return roleAssignmentRepository.findByDefinitionPointId(dvObject.getId());
    }

    public List<DvObject> whichChildrenHasPermissionsForOrReleased(DataverseRequest request, 
            DvObjectContainer objectContainer, Set<Permission> required) {
        return whichChildrenHasPermissionsFor(request, objectContainer, required, true);
    }

    /**
     * Finds all the permissions the {@link User} in {@code request} has over
     * {@code dvObject}, in the context of {@code request}.
     *
     * @return Permissions of {@code request.getUser()} over {@code dvObject}.
     */
    public Set<Permission> permissionsFor(final DataverseRequest request, 
    		final DvObject object) {
    	
        final User user = request.getUser();
        
        if (user.isSuperuser()) {
            return Permission.all(this.systemConfig.isReadonlyMode());
        } else {
	        final Set<Permission> permissions = getInferredPermissions(object);
	
	        // Add permissions gained from ras
	        final Set<RoleAssignee> assignees = new HashSet<>(this.groupService.groupsFor(request, object));
	        assignees.add(user);
	        for (RoleAssignment assignment : assignmentsFor(assignees, object)) {
	            permissions.addAll(assignment.getRole().permissions());
	        }
	
	        if (this.systemConfig.isReadonlyMode() || 
	        		this.confirmEmailService.hasEffectivelyUnconfirmedMail(user)) {
	            permissions.removeIf(Permission::requiresWrite);
	        }
	        if (!user.isAuthenticated()) {
	            permissions.removeIf(Permission::requiresAuthenticatedUser);
	        }
	        return permissions;
        }
    }

    public boolean isUserAllowedOn(User user, Command<?> command, DvObject dvObject) {
        Map<String, Set<Permission>> required = command.getRequiredPermissions();
        return isUserAllowedOn(user, required, dvObject);
    }

    public StaticPermissionQuery userOn(RoleAssignee assignee, DvObject dvObject) {
        if (assignee == null) {
            // get guest user for dataverse dvObject
            assignee = GuestUser.get();
        }
        return new StaticPermissionQuery(assignee, dvObject);
    }

    public RequestPermissionQuery requestOn(DataverseRequest request, DvObject dvObject) {
        if (dvObject.getId() == null) {
            throw new IllegalArgumentException("Cannot query permissions on a DvObject with a null id.");
        }
        return new RequestPermissionQuery(dvObject, request);
    }

    /**
     * Go from (User, Permission) to a list of Dataverse objects that the user
     * has the permission on.
     *
     * @return The list of dataverses {@code user} has permission
     * {@code permission} on.
     */
    public List<Dataverse> getDataversesUserHasPermissionOn(AuthenticatedUser user, 
            Permission permission) {
        Set<Group> groups = groupService.groupsFor(user);
        List<String> identifiers = new ArrayList<>();
        if (user != null) {
            identifiers.add(user.getIdentifier());
            identifiers.addAll(groups.stream().map(Group::getIdentifier).collect(toSet()));
        }
        List<Integer> dataverseIdsToCheck = roleAssignmentRepository.findDataversesWithUserPermitted(identifiers);
        List<Dataverse> dataversesUserHasPermissionOn = new LinkedList<>();
        for (int dvIdAsInt : dataverseIdsToCheck) {
            Dataverse dataverse = dataverseDao.find((long) dvIdAsInt);
            if (userOn(user, dataverse).has(permission)) {
                dataversesUserHasPermissionOn.add(dataverse);
            }
        }
        return dataversesUserHasPermissionOn;
    }

    public List<AuthenticatedUser> getUsersWithPermissionOn(Permission permission, 
            DvObject dvObject) {
        List<AuthenticatedUser> usersHasPermissionOn = new LinkedList<>();
        Set<RoleAssignment> roleAssignments = roleService.rolesAssignments(dvObject);
        for (RoleAssignment assignment : roleAssignments) {
            if (assignment.has(permission)) {
                RoleAssignee assignee = roleAssigneeService.getRoleAssignee(assignment.getAssigneeIdentifier());
                usersHasPermissionOn.addAll(roleAssigneeService.getExplicitUsers(assignee));
            }
        }
        return usersHasPermissionOn;
    }

    public Map<String, AuthenticatedUser> getDistinctUsersWithPermissionOn(Permission permission, 
            DvObject dvObject) {

        List<AuthenticatedUser> users = getUsersWithPermissionOn(permission, dvObject);
        Map<String, AuthenticatedUser> distinctUsers = new HashMap<>();
        users.forEach(u -> distinctUsers.put(u.getIdentifier(), u));

        return distinctUsers;
    }

    public boolean checkEditDatasetLock(Dataset dataset, 
            DataverseRequest dataverseRequest, Command<?> command)
            throws IllegalCommandException {
        if (checkEditDatasetLockNonThrowing(dataset, dataverseRequest)) {
            final String key = dataset.isInReview() 
                    ? "dataset.message.locked.editNotAllowedInReview"
                    : "dataset.message.locked.editNotAllowed";
            throw new IllegalCommandException(getStringFromBundle(key), command);
        }
        return false;
    }

    public boolean checkEditDatasetLockNonThrowing(final Dataset dataset, 
            final DataverseRequest dataverseRequest) {
        if (!dataset.isLocked()) {
            return false;
        }
        if (dataset.isInReview()) {
            // The "InReview" lock is not really a lock for curators. They can still make edits.
            if (!hasPermissionsFor(dataverseRequest, dataset, Permission.setOf(Permission.PublishDataset))) {
                return true;
            }
        }
        return dataset.isLockedForAny(Ingest, pidRegister, Workflow, DcmUpload);
    }

    public void checkDownloadFileLock(Dataset dataset, DataverseRequest dataverseRequest, 
            Command<?> command) throws IllegalCommandException {
        if (!dataset.isLocked()) {
            return;
        }
        if (dataset.isInReview()) {
            // The "InReview" lock is not really a lock for curators or contributors. They can still download.
            if (!isUserAllowedOn(dataverseRequest.getUser(), new UpdateDatasetVersionCommand(dataset, dataverseRequest), dataset)) {
                throw new IllegalCommandException(getStringFromBundle("dataset.message.locked.downloadNotAllowedInReview"), command);
            }
        }
        if (dataset.isLockedForAny(Ingest, pidRegister, Workflow, DcmUpload)) {
            throw new IllegalCommandException(getStringFromBundle("dataset.message.locked.downloadNotAllowed"), command);
        }
    }

    public boolean isUserAbleToEditDataverse(User user, Dataverse dataverse) {
        return hasPermissionsFor(user, dataverse, newHashSet(Permission.EditDataverse));
    }

    public boolean isUserCanEditDataverseTextMessagesAndBanners(User user, Long dataverseId) {

        if (dataverseId == null) {
            return false;
        }
        if (systemConfig.isReadonlyMode()) {
            return false;
        }
        Dataverse dataverse = dataverseDao.find(dataverseId);

        if (dataverse == null) {
            return false;
        }

        return (isUserAbleToEditDataverse(user, dataverse) || user.isSuperuser()) 
                && dataverse.isAllowMessagesBanners();
    }

    /**
     * Returns roles that are effective for {@code authenticatedUser}
     * over {@code dvObject}. Traverses the containment hierarchy of the {@code d}.
     * Takes into consideration all groups that {@code authenticatedUser} is part of.
     *
     * @param authenticatedUser    The authenticated user whose role assignments we look for.
     * @param dvObject The Dataverse object over which the roles are assigned
     * @return A set of all the role assignments for {@code ra} over {@code d}.
     */
    public Set<RoleAssignment> getRolesOfUser(AuthenticatedUser authenticatedUser, DvObject dvObject) {

        Set<RoleAssignment> roleAssignments = assignmentsFor(authenticatedUser, dvObject);

        Set<Group> groupsUserBelongsTo = groupService.groupsFor(authenticatedUser, dvObject);
        for (Group g : groupsUserBelongsTo) {
            roleAssignments.addAll(assignmentsFor(g, dvObject));
        }

        return roleAssignments;
    }

    // -------------------- PRIVATE --------------------

    /**
     * Returns all the children (direct descendants) of {@code objectContainer}, on which the user
     * has all the permissions specified in {@code permissions}. This method takes into
     * account which permissions apply for which object type, so a permission that
     * applies only to {@link Dataset}s will not be considered when looking into
     * the question of whether a {@link Dataverse} should be contained in the output list.
     *
     * @param request             The request whose permissions are queried
     * @param objectContainer             The objects whose children we list
     * @param required        (sub)set of permissions {@code request} has on the objects in the returned list
     * @param includeReleased include released dataverses and datasets without checking permissions
     * @return list of {@code objectContainer} children over which {@code request} has at least {@code required} permissions.
     */
    private List<DvObject> whichChildrenHasPermissionsFor(DataverseRequest request, 
            DvObjectContainer objectContainer, Set<Permission> required, 
            boolean includeReleased) {
        List<DvObject> children = dvObjectServiceBean.findByOwnerId(objectContainer.getId());
        User user = request.getUser();

        if (user.isSuperuser()) {
            return children;
        } else if (!user.isAuthenticated() && Permission.requiresAuthenticatedUser(required)) {
            // At least one of the permissions requires that the user is authenticated, which is not the case.
            return emptyList();
        }

        // Actually look at permissions
        Set<DvObject> parents = getPermissionAncestors(objectContainer);
        Set<RoleAssignee> roleAssignees = new HashSet<>(groupService.groupsFor(request));
        roleAssignees.add(user);
        List<RoleAssignment> parentsAsignments = roleService.directRoleAssignmentsByAssigneesAndDvObjects(roleAssignees, parents);

        boolean unconfirmedMailMode = confirmEmailService.hasEffectivelyUnconfirmedMail(user);

        for (RoleAssignment assignment : parentsAsignments) {
            Set<Permission> permissions = unconfirmedMailMode
                    ? assignment.getRole().permissionsStream()
                    .filter(p -> !p.requiresWrite())
                    .collect(toSet())
                    : assignment.getRole().permissions();
            required.removeAll(permissions);
        }
        if (required.isEmpty()) {
            // All permissions are met by role assignments on the request
            return children;
        }

        // Looking at each child at a time now.
        // 1. Map childs to permissions
        List<RoleAssignment> childrenAssignments = roleService.directRoleAssignmentsByAssigneesAndDvObjects(roleAssignees,
                includeReleased ? children.stream().filter(child -> !child.isReleased()).collect(toList()) : children);

        Map<DvObject, Set<Permission>> roleMap = new HashMap<>();
        for (RoleAssignment assignment : childrenAssignments) {
            DvObject definitionPoint = assignment.getDefinitionPoint();
            if (roleMap.containsKey(definitionPoint)) {
                roleMap.get(definitionPoint).addAll(assignment.getRole().permissions());
            } else {
                roleMap.put(definitionPoint, assignment.getRole().permissions());
            }
        }

        // 2. Filter by permission map created at (1).
        return children.stream()
                .filter(c -> (includeReleased && c.isReleased())
                        || hasPermissions(required, roleMap, c, unconfirmedMailMode))
                .collect(toList());
    }

    private boolean hasPermissions(Set<Permission> required, Map<DvObject, 
            Set<Permission>> roleMap, DvObject child, boolean unconfirmedMailMode) {
        Set<Permission> permissionsApplicableToObject = required.stream()
                .filter(p -> p.appliesTo(child.getClass()))
                .collect(toSet());
        return (roleMap.containsKey(child)
                && roleMap.get(child).containsAll(permissionsApplicableToObject)
                && unconfirmedMailMode)
                ? permissionsApplicableToObject.stream().noneMatch(Permission::requiresWrite)
                : true;
    }

    private boolean hasPermissionsFor(final DataverseRequest request, 
            final DvObject object, final Set<Permission> required) {
    	
    	final User user = request.getUser();
        if ((this.systemConfig.isReadonlyMode() 
                || this.confirmEmailService.hasEffectivelyUnconfirmedMail(user))
                && Permission.requiresWrite(required)) {
            return false;
        }
        if (user.isSuperuser()) {
            return true;
        }
        if (!user.isAuthenticated() && Permission.requiresAuthenticatedUser(required)) {
        	return false;
        }
        final Set<RoleAssignee> ras = new HashSet<>(groupService.groupsFor(request, object));
        ras.add(user);
        return hasGroupPermissionsFor(ras, object, required);
    }

    private boolean hasPermissionsFor(RoleAssignee roleAssignee, 
            DvObject dvObject, Set<Permission> required) {
        boolean unconfirmedEmail = roleAssignee instanceof User
                && confirmEmailService.hasEffectivelyUnconfirmedMail((User) roleAssignee);
        if ((systemConfig.isReadonlyMode() || unconfirmedEmail)
                && Permission.requiresWrite(required)) {
            return false;
        }

        if (roleAssignee instanceof User) {
            User user = (User) roleAssignee;
            if (user.isSuperuser()) {
                return true;
            } else if (!user.isAuthenticated() && Permission.requiresAuthenticatedUser(required)) {
            	return false;
            }
        }
        required.removeAll(getInferredPermissions(dvObject));
        if (required.isEmpty()) {
            return true;
        }

        final Set<RoleAssignee> ras = new HashSet<>(groupService.groupsFor(roleAssignee, dvObject));
        ras.add(roleAssignee);
        return hasGroupPermissionsFor(ras, dvObject, required);
    }

    private boolean hasGroupPermissionsFor(Set<RoleAssignee> roleAssignees, 
            DvObject dvObject, Set<Permission> required) {
        for (RoleAssignment asmnt : assignmentsFor(roleAssignees, dvObject)) {
            required.removeAll(asmnt.getRole().permissions());
        }
        return required.isEmpty();
    }

    /**
     * Calculates permissions based on object state and other context
     */
    private Set<Permission> getInferredPermissions(DvObject dvObject) {
        return isPubliclyDownloadable(dvObject) 
        		? Permission.setOf(Permission.DownloadFile)
        		: Permission.empty();
    }

    /**
     * unrestricted files that are part of a release dataset automatically get
     * download permission for everybody:
     */
    private boolean isPubliclyDownloadable(DvObject dvObject) {
        if (dvObject instanceof DataFile) {
            // unrestricted files that are part of a release dataset
            // automatically get download permission for everybody:
            //      -- L.A. 4.0 beta12

            DataFile df = (DataFile) dvObject;
            DatasetVersion realeasedDatasetVersion = df.getOwner().getReleasedVersion();

            if (realeasedDatasetVersion != null) {
                for (FileMetadata fm : realeasedDatasetVersion.getFileMetadatas()) {
                    if (df.equals(fm.getDataFile())) {
                        return !fm.isFileUseRestricted();
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns all the role assignments that are effective for {@code assignee} over
     * {@code dvObject}. Traverses the containment hierarchy of the {@code dvObject}.
     *
     * @param assignee The role assignee whose role assignemnts we look for.
     * @param object  The dataverse object over which the roles are assigned
     * @return A set of all the role assignments for {@code assignee} over {@code dvObject}.
     */
    private Set<RoleAssignment> assignmentsFor(final RoleAssignee assignee, 
    		final DvObject object) {
    	
        return new HashSet<>(assignmentsFor(singleton(assignee), object));
    }

    private List<RoleAssignment> assignmentsFor(final Set<RoleAssignee> assignees, 
    		final DvObject object) {
    	
        final Set<DvObject> ancestors = getPermissionAncestors(object);
        return this.roleService.
        		directRoleAssignmentsByAssigneesAndDvObjects(assignees, ancestors);
    }

    private Set<DvObject> getPermissionAncestors(DvObject object) {
    	
        final Set<DvObject> result = new HashSet<>();
        
        while (object != null) {
            result.add(object);
            if (object.isEffectivelyPermissionRoot()) {
                return result;
            }
            object = object.getOwner();
        }
        return result;
    }

    private boolean isUserAllowedOn(User user, Map<String, Set<Permission>> required, 
            DvObject dvObject) {
        if (required.isEmpty() || required.get("") == null) {
            logger.debug("IsUserAllowedOn: empty-true");
            return true;
        } else {
            Set<Permission> requiredPermissionSet = required.get("");
            return hasPermissionsFor(user, dvObject, requiredPermissionSet);
        }
    }

    // -------------------- INNER CLASSES --------------------

    /**
     * A request-level permission query (e.g includes IP ras).
     */
    public class RequestPermissionQuery {

        private final DvObject subject;
        private final DataverseRequest request;

        // -------------------- CONSTRUCTORS --------------------

        private RequestPermissionQuery(DvObject subject, DataverseRequest request) {
            this.subject = subject;
            this.request = request;
        }

        // -------------------- LOGIC --------------------

        public boolean has(Permission permission) {
            return hasPermissionsFor(request, subject, EnumSet.of(permission));
        }

        /*
         * This is a new and optimized method, for making a quick lookup on
         * a SET of permission all at once; it was originally called
         * has(Set<Permission> permissions)... however, while unambiguos in Java,
         * the fact that there were 2 has() methods - has(Permission) and
         * has(Set<Permission>) - was confusing PrimeFaces and resulting in
         * pages failing with "cannot convert "String" to "Set" error messages...
         * so it had to be renamed to hasPermissions(...)
         */
        public boolean hasPermissions(Set<Permission> permissions) {
            if (permissions.isEmpty()) {
                return true;
            }
            return hasPermissionsFor(request, subject, permissions);
        }

        /**
         * Tests whether a command of the passed class can be issued over the
         * {@link DvObject} in the context of the current request. Note that
         * since some commands have dynamic permissions, in some cases it's
         * better to instantiate a command object and pass it to
         * {@link #canIssue(edu.harvard.iq.dataverse.engine.command.Command)}.
         *
         * @return {@code true} iff instances of the command class can be issued
         * in the context of the current request.
         */
        public boolean canIssue(Class<? extends Command<?>> commandClass) {
            Map<String, Set<Permission>> required = CH.permissionsRequired(commandClass);
            if (required.isEmpty() || required.get("") == null) {
                logger.debug("IsUserAllowedOn: empty-true");
                return true;
            } else {
                Set<Permission> requiredPermissionSet = required.get("");
                return hasPermissions(requiredPermissionSet);
            }
        }

        /**
         * Tests whether the command can be issued over the {@link DvObject} in
         * the context of the current request.
         *
         * @return {@code true} iff the command can be issued in the context of
         * the current request.
         */
        public boolean canIssue(Command<?> command) {
            Map<String, Set<Permission>> required = command.getRequiredPermissions();
            if (required.isEmpty() || required.get("") == null) {
                logger.debug("IsUserAllowedOn: empty-true");
                return true;
            } else {
                Set<Permission> requiredPermissionSet = required.get("");
                return hasPermissions(requiredPermissionSet);
            }
        }
    }

    /**
     * A permission query for a given role assignee. Does not cover
     * request-level permissions.
     */
    public class StaticPermissionQuery {

        private final DvObject subject;
        private final RoleAssignee user;

        private StaticPermissionQuery(RoleAssignee user, DvObject subject) {
            this.subject = subject;
            this.user = user;
        }

        public boolean has(Permission permission) {
            return hasPermissionsFor(user, subject, Permission.setOf(permission));
        }

    }
}
