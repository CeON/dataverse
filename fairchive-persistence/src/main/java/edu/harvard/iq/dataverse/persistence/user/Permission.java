package edu.harvard.iq.dataverse.persistence.user;

import static edu.harvard.iq.dataverse.common.BundleUtil.getStringFromBundle;
import static java.util.Arrays.stream;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;

import edu.harvard.iq.dataverse.persistence.DvObject;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;

/**
 * All the permissions in the system are implemented as enum values in this
 * class. For performance, the permissions are stored internally in a bit field
 * (in effect, a {@code long}). This brings database fetches to a single action
 * rather than a join, and in-memory permission set unions to a bitwise or
 * rather than a tree merge. But some caution must be practiced when making
 * changes to this class.
 * <p>
 * =========================================================
 * IMPORTANT NOTES, READ BEFORE MAKING CHANGES TO THIS FILE
 * =========================================================
 * <p>
 * 1. Number of permissions must be kept under 64. If more
 * than 64 permissions are needed, storage must be updated
 * to include two {@code long}s, rather then the current one.
 * 2. Do not change the order of the enum values, and add new values only
 * after the last enum value. If you wish to change the order or add a
 * permission in between existing ones (or at the beginning), ALSO PROVIDE
 * A MIGRATION SCRIPT FOR THE DATABASE. Otherwise, permissions in the
 * database will be mis-assigned. This may be a major security issue.
 *
 * @author michael
 */
public enum Permission implements java.io.Serializable {

    // Create
    //1
    AddDataverse(true, true, Dataverse.class),
    //2
    AddDataset(true, true, Dataverse.class),
    // Read
    //4
    ViewUnpublishedDataverse(false, false, Dataverse.class),
    //8
    ViewUnpublishedDataset(false, false, Dataset.class),
    //16
    DownloadFile(false, false, DataFile.class),
    // Update
    //32
    EditDataverse(true, true, Dataverse.class),
    //64
    EditDataset(true, true, Dataset.class),
    //128
    ManageDataversePermissions(true, true, Dataverse.class),
    //256
    ManageDatasetPermissions(true, true, Dataset.class),
    //512
    PublishDataverse(true, true, Dataverse.class),
    //1024
    PublishDataset(true, true, Dataset.class, Dataverse.class),
    // Delete
    //2048
    DeleteDataverse(true, true, Dataverse.class),
    //4096
    DeleteDatasetDraft(true, true, Dataset.class),
    //8192
    ManageMinorDatasetPermissions(true, true, Dataset.class);
	
    // lets's cache this for performance reasons
    private final static Permission[] values = values();

    /**
     * Which types of {@link DvObject}s this permission applies to.
     */
    private final Class<? extends DvObject>[] appliesTo;

    /**
     * Can this permission be applied only to {@link AuthenticatedUser}s, or to any user?
     */
    private final boolean requiresAuthenticatedUser;

    /**
     * Is permission have write nature. That is when user performs some
     * action using this permission - can it modify database or storage state?
     * When true then yes.
     */
    private final boolean requiresWrite;
    
    @SafeVarargs
    Permission(final boolean requiresAuthenticatedUser, final boolean requiresWrite, 
    		final Class<? extends DvObject>... appliesTo) {
        this.appliesTo = appliesTo;
        this.requiresAuthenticatedUser = requiresAuthenticatedUser;
        this.requiresWrite = requiresWrite;
    }

    public String getHumanName() {
        return getStringFromBundle("permission." + name() + ".desc");
    }

    public String getDisplayName() {
        return getStringFromBundle("permission." + name() + ".label");
    }

    public boolean appliesTo(Class<? extends DvObject> aClass) {
        for (final Class<? extends DvObject> c : this.appliesTo) {
            if (c.isAssignableFrom(aClass)) {
                return true;
            }
        }
        return false;
    }

    public boolean requiresAuthenticatedUser() {
        return this.requiresAuthenticatedUser;
    }

    public boolean requiresWrite() {
        return this.requiresWrite;
    }
    
    public long bitValue() {
    	return 1L << ordinal();
    }
    
    public boolean isIn(final long bits) {
    	return (bits & bitValue()) > 0;
    }
    
    public static Stream<Permission> streamFrom(final long bits) {
    	return stream(values).filter(p -> p.isIn(bits));
    }
    
    public static Set<Permission> all() {
    	return EnumSet.allOf(Permission.class);
    }
    
    public static Set<Permission> all(final boolean readOnly) {
    	return readOnly ? readOnly() : all();
    }
    
    public static Set<Permission> empty() {
    	return EnumSet.noneOf(Permission.class);
    }
    
    public static Set<Permission> readOnly() {
    	final Set<Permission> result = all();
        result.removeIf(Permission::requiresWrite);
        return result;
    }
    
    public static Set<Permission> setOf(final Permission p) {
    	return EnumSet.of(p);
    }
    
    public static boolean requiresWrite(final Set<Permission> set) {
    	return set.stream().anyMatch(Permission::requiresWrite);
    }
    
    public static boolean requiresAuthenticatedUser(final Set<Permission> set) {
    	return set.stream().anyMatch(Permission::requiresAuthenticatedUser);
    }
}
