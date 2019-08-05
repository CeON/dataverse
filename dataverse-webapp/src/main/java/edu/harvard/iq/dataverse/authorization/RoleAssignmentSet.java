package edu.harvard.iq.dataverse.authorization;

import edu.harvard.iq.dataverse.common.BitSet;
import edu.harvard.iq.dataverse.persistence.user.Permission;
import edu.harvard.iq.dataverse.persistence.user.RoleAssignee;
import edu.harvard.iq.dataverse.persistence.user.RoleAssignment;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * A set of assignment for a given {@link RoleAssignee} user has.
 * <p>
 * LATER: we could probably refactor this class out.
 *
 * @author michael
 * <p>
 * We definitely should factor this out.
 * Oscar
 */
public class RoleAssignmentSet implements Iterable<RoleAssignment> {

    private final RoleAssignee roas;
    private final Set<RoleAssignment> assignments = new HashSet<>();

    public RoleAssignmentSet(RoleAssignee aRoleAssignee) {
        roas = aRoleAssignee;
    }

    public void add(Iterable<RoleAssignment> ras) {
        for (RoleAssignment ra : ras) {
            assignments.add(ra);
        }
    }

    public void add(RoleAssignment ra) {
        assignments.add(ra);
    }

    public Set<Permission> getPermissions() {
        BitSet acc = new BitSet();
        for (RoleAssignment ra : assignments) {
            acc.union(new BitSet(ra.getRole().getPermissionsBits()));
        }
        return acc.asSetOf(Permission.class);
    }

    public RoleAssignee getRoleAssignee() {
        return roas;
    }

    public Set<RoleAssignment> getAssignments() {
        return assignments;
    }

    @Override
    public Iterator<RoleAssignment> iterator() {
        return assignments.iterator();
    }

    public boolean isEmpty() {
        return assignments.isEmpty();
    }
}
