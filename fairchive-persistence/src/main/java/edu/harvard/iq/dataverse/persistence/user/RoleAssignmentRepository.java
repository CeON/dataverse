package edu.harvard.iq.dataverse.persistence.user;

import edu.harvard.iq.dataverse.persistence.JpaRepository;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;

import javax.ejb.Singleton;
import javax.persistence.NoResultException;
import javax.persistence.NonUniqueResultException;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;

import java.util.List;
import java.util.Optional;

@Singleton
public class RoleAssignmentRepository extends JpaRepository<Long, RoleAssignment> {

    // -------------------- CONSTRUCTORS --------------------

    public RoleAssignmentRepository() {
        super(RoleAssignment.class);
    }

    // -------------------- LOGIC --------------------

    public List<RoleAssignment> findByDefinitionPointId(final Long id) {
        return createQuery("SELECT r FROM RoleAssignment r " +
                           "WHERE r.definitionPoint.id=:id")
                .setParameter("id", id)
                .getResultList();
    }
    
    public List<RoleAssignment> findByDefinitionPointIdAndRoleAlias(final Long id, 
    		final String alias) {
        return createQuery("SELECT r FROM RoleAssignment r " +
        		"WHERE r.definitionPoint.id = :id AND r.role.alias = :alias")
                .setParameter("id", id)
                .setParameter("alias", alias)
                .getResultList();
    }

    public List<RoleAssignment> findByDefinitionPointIds(final List<Long> ids) {
        return createQuery("SELECT r FROM RoleAssignment r WHERE r.definitionPoint.id IN :ids")
                .setParameter("ids", ids)
                .getResultList();
    }

    public List<RoleAssignment> findByAssigneeIdentifier(final String identifier) {
        return createQuery("SELECT r FROM RoleAssignment r " +
                           "WHERE r.assigneeIdentifier=:identifier")
                .setParameter("identifier", identifier)
                .getResultList();
    }

    public List<RoleAssignment> findByRoleId(final Long id) {
        return createQuery("SELECT r FROM RoleAssignment r WHERE r.role.id=:id")
                .setParameter("id", id)
                .getResultList();
    }

    public List<RoleAssignment> findByAssigneeIdentifiersAndDefinitionPointIds(
    		final List<String> assigneeIdentifiers,
            final List<Long> definitionPointIds) {
        return createQuery("SELECT r FROM RoleAssignment r WHERE "
                                + " r.assigneeIdentifier in :assigneeIdentifiers AND "
                                + " r.definitionPoint.id in :definitionPointIds")
                .setParameter("assigneeIdentifiers", assigneeIdentifiers)
                .setParameter("definitionPointIds", definitionPointIds)
                .getResultList();
    }

    public int deleteAllByAssigneeIdentifier(final String identifier) {
        return createQuery("DELETE FROM RoleAssignment r " +
                           "WHERE r.assigneeIdentifier=:assigneeIdentifier")
                .setParameter("assigneeIdentifier", identifier)
                .executeUpdate();
    }

    @SuppressWarnings("unchecked")
    public List<Integer> findDataversesWithUserPermitted(final List<String> identifiers) {
        if (identifiers == null || identifiers.isEmpty()) {
            return emptyList();
        } else {
	        String query = "SELECT id FROM dvobject WHERE dtype = 'Dataverse' " +
	                "and id in (select definitionpoint_id from roleassignment " +
	                "where assigneeidentifier in ("
	                + identifiers.stream().map(i -> "'" + i + "'").collect(joining(",")) + "));";
	        return (List<Integer>) this.em.createNativeQuery(query).getResultList();
        }
    }
    
    /**
     * @return A RoleAssignment or null.
     * @todo This might be a good place for Optional.
     */
    public RoleAssignment getRoleAssignmentFromPrivateUrlToken(final String token) {
        if (token == null) {
            return null;
        } else {
            try {
                return createQuery("SELECT r FROM RoleAssignment r " + 
                                   "WHERE r.privateUrlToken=:token")
                        .setParameter("token", token)
                        .getSingleResult();
            } catch (final NoResultException | NonUniqueResultException ex) {
                return null;
            }
        }
    }

    /**
     * @param dataset A non-null dataset;
     * @return A role assignment for a Private URL, if found, or null.
     * @todo This might be a good place for Optional.
     */
    public RoleAssignment getPrivateUrlRoleAssignmentFromDataset(
            final Dataset dataset, final boolean anonymized) {
        if (dataset == null) {
            return null;
        } else {
            try {
                return createQuery(
                		"SELECT r FROM RoleAssignment r " +
                        "WHERE r.assigneeIdentifier=:assigneeIdentifier " + 
                			"AND r.definitionPoint.id=:definitionPointId " +
                        	"AND r.anonymized = :anonymized")
                        .setParameter("assigneeIdentifier",
                                new PrivateUrlUser(dataset.getId()).getIdentifier())
                        .setParameter("definitionPointId", dataset.getId())
                        .setParameter("anonymized", anonymized)
                        .getSingleResult();
            } catch (final NoResultException | NonUniqueResultException ex) {
                return null;
            }
        }
    }
    
    public Optional<RoleAssignment> findAssignmentFor(final String roleAssigneeIdentifier, 
            final Long definitionPointId, final Long roleId) {
        return getSingleResult(createQuery(
        		"SELECT r FROM RoleAssignment r WHERE" +
                " r.assigneeIdentifier=:assigneeIdentifier" + 
        		" AND r.definitionPoint.id=:definitionPointId AND r.role.id=:roleId")
            .setParameter("assigneeIdentifier", roleAssigneeIdentifier)
            .setParameter("definitionPointId", definitionPointId)
            .setParameter("roleId", roleId));
    }
}
