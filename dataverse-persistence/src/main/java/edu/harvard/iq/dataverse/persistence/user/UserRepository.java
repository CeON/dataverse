package edu.harvard.iq.dataverse.persistence.user;

import edu.harvard.iq.dataverse.persistence.JpaRepository;
import org.apache.commons.lang3.StringUtils;

import javax.ejb.Stateless;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Stateless
public class UserRepository extends JpaRepository<Long, AuthenticatedUser> {

    private static final Logger logger = Logger.getLogger(UserRepository.class.getCanonicalName());

    // -------------------- CONSTRUCTORS --------------------

    public UserRepository(Class<AuthenticatedUser> entityClass) {
        super(entityClass);
    }

    public UserRepository() {
        super(AuthenticatedUser.class);
    }


    // -------------------- LOGIC --------------------

    /**
     * Results of this query are used to build Authenticated User records.
     */
    public List<Object[]> findSearchedAuthenticatedUsers(String sortKey, int resultLimit, int offset, String searchTerm, boolean isSortAscending) {

        String qstr = "SELECT u.id, u.useridentifier,";
        qstr += " u.lastname, u.firstname, u.email, u.emailconfirmed, ";
        qstr += " u.affiliation, u.superuser,";
        qstr += " u.position, u.notificationslanguage, ";
        qstr += " u.createdtime, u.lastlogintime, u.lastapiusetime, ";
        qstr += " prov.id, prov.factoryalias";
        qstr += " FROM authenticateduser u,";
        qstr += " authenticateduserlookup prov_lookup,";
        qstr += " authenticationproviderrow prov";
        qstr += " WHERE";
        qstr += " u.id = prov_lookup.authenticateduser_id";
        qstr += " AND prov_lookup.authenticationproviderid = prov.id";
        qstr += getSharedSearchClause(searchTerm);
        qstr += " ORDER BY " + sortKey + (isSortAscending ? " ASC" : " DESC");
        qstr += " LIMIT " + resultLimit;
        qstr += " OFFSET " + offset;
        qstr += ";";

        logger.log(Level.FINE, "getUserCount: {0}", qstr);

        return em.createNativeQuery(qstr)
                .setFirstResult(0)
                .setMaxResults(resultLimit)
                .getResultList();
    }


    /**
     * Retrieves number of authenticatedUsers for a search term.
     *
     * @return number of results for given search term
     */
    public Long countSearchedAuthenticatedUsers(String searchTerm) {

        String qstr = "SELECT count(u)";
        qstr += " FROM authenticateduser AS u,";
        qstr += " authenticateduserlookup prov_lookup,";
        qstr += " authenticationproviderrow prov";
        qstr += " WHERE";
        qstr += " u.id = prov_lookup.authenticateduser_id";
        qstr += " AND prov_lookup.authenticationproviderid = prov.id";
        qstr += getSharedSearchClause(searchTerm);
        qstr += ";";

        return (Long) em.createNativeQuery(qstr).getSingleResult();
    }

    // -------------------- PRIVATE --------------------

    /**
     * The search clause needs to be consistent between the searches that:
     * (1) get a user count
     * (2) get a list of users
     *
     * @return
     */
    private String getSharedSearchClause(String searchTerm) {
        String searchClause = StringUtils.EMPTY;
        if (!searchTerm.isEmpty()) {
            searchClause = " AND (u.useridentifier ILIKE '" + searchTerm + "%'";
            searchClause += " OR u.firstname ILIKE '" + searchTerm + "%'";
            searchClause += " OR u.lastname ILIKE '" + searchTerm + "%'";
            searchClause += " OR u.affiliation ILIKE '" + searchTerm + "%'";
            searchClause += " OR u.email ILIKE '" + searchTerm + "%')";
        }

        return searchClause;
    }
}
