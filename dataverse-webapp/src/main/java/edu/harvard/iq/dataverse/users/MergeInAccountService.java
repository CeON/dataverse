package edu.harvard.iq.dataverse.users;

import edu.harvard.iq.dataverse.DatasetDao;
import edu.harvard.iq.dataverse.DvObjectServiceBean;
import edu.harvard.iq.dataverse.RoleAssigneeServiceBean;
import edu.harvard.iq.dataverse.authorization.AuthenticationServiceBean;
import edu.harvard.iq.dataverse.authorization.providers.builtin.BuiltinUserServiceBean;
import edu.harvard.iq.dataverse.guestbook.GuestbookResponseServiceBean;
import edu.harvard.iq.dataverse.interceptors.LoggedCall;
import edu.harvard.iq.dataverse.mail.confirmemail.ConfirmEmailServiceBean;
import edu.harvard.iq.dataverse.persistence.DvObject;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetLock;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersionUser;
import edu.harvard.iq.dataverse.persistence.dataverse.link.SavedSearch;
import edu.harvard.iq.dataverse.persistence.guestbook.GuestbookResponse;
import edu.harvard.iq.dataverse.persistence.user.ApiToken;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUserLookup;
import edu.harvard.iq.dataverse.persistence.user.BuiltinUser;
import edu.harvard.iq.dataverse.persistence.user.ConfirmEmailData;
import edu.harvard.iq.dataverse.persistence.user.RoleAssignment;
import edu.harvard.iq.dataverse.persistence.user.User;
import edu.harvard.iq.dataverse.persistence.user.UserNotification;
import edu.harvard.iq.dataverse.persistence.user.UserNotificationDao;
import edu.harvard.iq.dataverse.persistence.workflow.WorkflowComment;
import edu.harvard.iq.dataverse.search.index.IndexServiceBean;
import edu.harvard.iq.dataverse.search.index.SolrIndexServiceBean;
import edu.harvard.iq.dataverse.search.savedsearch.SavedSearchServiceBean;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

@Stateless
public class MergeInAccountService {

    @PersistenceContext(unitName = "VDCNet-ejbPU")
    private EntityManager entityManager;

    @EJB private AuthenticationServiceBean authenticationService;
    @EJB private RoleAssigneeServiceBean roleAssigneeService;
    @EJB private SolrIndexServiceBean solrIndexService;
    @EJB private IndexServiceBean indexService;
    @EJB private DatasetDao datasetDao;
    @EJB private DvObjectServiceBean dvObjectService;
    @EJB private GuestbookResponseServiceBean guestbookResponseService;
    @EJB private UserNotificationDao userNotificationDao;
    @EJB private SavedSearchServiceBean savedSearchService;
    @EJB private ConfirmEmailServiceBean confirmEmailService;
    @EJB private BuiltinUserServiceBean builtinUserService;

    // -------------------- LOGIC --------------------

    @LoggedCall
    public void mergeAccounts(User user, String consumedIdentifier, String baseIdentifier)
        throws IllegalArgumentException, SecurityException {

        if(!user.isSuperuser()) {
            throw new SecurityException("Only superusers can merge accounts.");
        }

        if(null == baseIdentifier || baseIdentifier.isEmpty()) {
            throw new IllegalArgumentException("Base identifier provided to change is empty.");
        } else if(null == consumedIdentifier || consumedIdentifier.isEmpty()) {
            throw new IllegalArgumentException("Identifier to merge in is empty.");
        } else if(baseIdentifier.equals(consumedIdentifier)) {
            throw new IllegalArgumentException("You cannot merge account to itself.");
        }

        AuthenticatedUser baseAuthenticatedUser = authenticationService.getAuthenticatedUser(baseIdentifier);
        if (baseAuthenticatedUser == null) {
            throw new IllegalArgumentException("User " + baseIdentifier + " not found in AuthenticatedUser");
        }

        AuthenticatedUser consumedAuthenticatedUser = authenticationService.getAuthenticatedUser(consumedIdentifier);
        if (consumedAuthenticatedUser == null) {
            throw new IllegalArgumentException("User " + consumedIdentifier + " not found in AuthenticatedUser");
        }

        updateRoleAssignments(consumedAuthenticatedUser, baseAuthenticatedUser);
        updateDatasetVersionUser(consumedAuthenticatedUser, baseAuthenticatedUser);
        updateDatasetLocks(consumedAuthenticatedUser, baseAuthenticatedUser);
        updateDvObject(consumedAuthenticatedUser, baseAuthenticatedUser);
        updateGuestbookResponse(consumedAuthenticatedUser, baseAuthenticatedUser);
        updateUserNotification(consumedAuthenticatedUser, baseAuthenticatedUser);
        updateUserNotificationRequestor(consumedAuthenticatedUser, baseAuthenticatedUser);
        updateSavedSearch(consumedAuthenticatedUser, baseAuthenticatedUser);
        updateWorkflowComment(consumedAuthenticatedUser, baseAuthenticatedUser);
        updateFileAccessRequest(consumedAuthenticatedUser, baseAuthenticatedUser);
        updateExplicitGroup_AuthenticatedUser(consumedAuthenticatedUser, baseAuthenticatedUser);
        updateAcceptedConsents(consumedAuthenticatedUser, baseAuthenticatedUser);

        removeConfirmEmailData(consumedAuthenticatedUser);
        removeOAuth2TokenData(consumedAuthenticatedUser);
        removeApiToken(consumedAuthenticatedUser);
        removeUser(consumedAuthenticatedUser);
    }

    // -------------------- PRIVATE --------------------

    private void updateRoleAssignments(AuthenticatedUser consumedAU, AuthenticatedUser baseAU) {
        List<RoleAssignment> baseRAList = roleAssigneeService.getAssignmentsFor(baseAU.getIdentifier());
        List<RoleAssignment> consumedRAList = roleAssigneeService.getAssignmentsFor(consumedAU.getIdentifier());

        for(RoleAssignment consumedRA : consumedRAList) {
            if(consumedRA.getAssigneeIdentifier().charAt(0) == '@') {

                boolean willDelete = false;
                for(RoleAssignment baseRA : baseRAList) {
                    //Matching on the id not the whole DVObject as I'm suspicious of dvobject equality
                    if (baseRA.getDefinitionPoint().getId().equals(consumedRA.getDefinitionPoint().getId())
                            && baseRA.getRole().equals(consumedRA.getRole())) {
                        willDelete = true; //more or less a skip, as we run a delete query afterwards
                        break;
                    }
                }
                if(!willDelete) {
                    consumedRA.setAssigneeIdentifier(baseAU.getIdentifier());
                    entityManager.merge(consumedRA);
                    solrIndexService.indexPermissionsForOneDvObject(consumedRA.getDefinitionPoint());
                    indexService.indexDvObject(consumedRA.getDefinitionPoint());
                } // no else here because the any willDelete == true will happen in the named query below.
            } else {
                throw new IllegalArgumentException("Original userIdentifier provided does not seem to be an AuthenticatedUser");
            }
        }

        //Delete not merged role assignments for consumedIdentifier, e.g. duplicates
        entityManager.createNamedQuery("RoleAssignment.deleteAllByAssigneeIdentifier", RoleAssignment.class)
                .setParameter("assigneeIdentifier", consumedAU.getIdentifier())
                .executeUpdate();
    }

    private void updateDatasetVersionUser(AuthenticatedUser consumedAU, AuthenticatedUser baseAU) {
        for (DatasetVersionUser user : datasetDao.getDatasetVersionUsersByAuthenticatedUser(consumedAU)) {
            user.setAuthenticatedUser(baseAU);
            entityManager.merge(user);
        }
    }

    private void updateDatasetLocks(AuthenticatedUser consumedAU, AuthenticatedUser baseAU) {
        for (DatasetLock lock : datasetDao.getDatasetLocksByUser(consumedAU)) {
            lock.setUser(baseAU);
            entityManager.merge(lock);
        }
    }

    private void updateDvObject(AuthenticatedUser consumedAU, AuthenticatedUser baseAU) {
        for (DvObject dvo : dvObjectService.findByAuthenticatedUserId(consumedAU)) {
            if (dvo.getCreator().equals(consumedAU)){
                dvo.setCreator(baseAU);
            }
            if (dvo.getReleaseUser() != null &&  dvo.getReleaseUser().equals(consumedAU)){
                dvo.setReleaseUser(baseAU);
            }
            entityManager.merge(dvo);
        }
    }

    private void updateGuestbookResponse(AuthenticatedUser consumedAU, AuthenticatedUser baseAU) {
        for (GuestbookResponse gbr : guestbookResponseService.findByAuthenticatedUserId(consumedAU)) {
            gbr.setAuthenticatedUser(baseAU);
            entityManager.merge(gbr);
        }
    }

    private void updateUserNotification(AuthenticatedUser consumedAU, AuthenticatedUser baseAU) {
        for (UserNotification note : userNotificationDao.findByUser(consumedAU.getId())) {
            note.setUser(baseAU);
            entityManager.merge(note);
        }
    }

    private void updateUserNotificationRequestor(AuthenticatedUser consumedAU, AuthenticatedUser baseAU) {
        for (UserNotification note : userNotificationDao.findByRequestor(consumedAU.getId())) {
            note.setRequestor(baseAU);
            entityManager.merge(note);
        }
    }

    private void updateSavedSearch(AuthenticatedUser consumedAU, AuthenticatedUser baseAU) {
        for (SavedSearch search : savedSearchService.findByAuthenticatedUser(consumedAU)) {
            search.setCreator(baseAU);
            entityManager.merge(search);
        }
    }

    private void updateWorkflowComment(AuthenticatedUser consumedAU, AuthenticatedUser baseAU) {
        for (WorkflowComment wc : authenticationService.getWorkflowCommentsByAuthenticatedUser(consumedAU)) {
            wc.setAuthenticatedUser(baseAU);
            entityManager.merge(wc);
        }
    }

    private void updateFileAccessRequest(AuthenticatedUser consumedAU, AuthenticatedUser baseAU) {
        entityManager.createNativeQuery("UPDATE fileaccessrequests SET authenticated_user_id=" + baseAU.getId() +
                " WHERE authenticated_user_id=" + consumedAU.getId()).executeUpdate();
    }

    private void updateExplicitGroup_AuthenticatedUser(AuthenticatedUser consumedAU, AuthenticatedUser baseAU) {
        entityManager.createNativeQuery("UPDATE explicitgroup_authenticateduser SET containedauthenticatedusers_id=" +
                baseAU.getId() + " WHERE containedauthenticatedusers_id=" + consumedAU.getId()).executeUpdate();
    }

    private void updateAcceptedConsents(AuthenticatedUser consumedAU, AuthenticatedUser baseAU) {
        entityManager.createNativeQuery("UPDATE acceptedconsent SET user_id=" +
                baseAU.getId() + " WHERE user_id=" + consumedAU.getId()).executeUpdate();
    }

    private void removeConfirmEmailData(AuthenticatedUser consumedAU) {
        ConfirmEmailData confirmEmailData = confirmEmailService.findSingleConfirmEmailDataByUser(consumedAU);
        if (confirmEmailData != null){
            entityManager.remove(confirmEmailData);
        }
    }

    private void removeOAuth2TokenData(AuthenticatedUser consumedAU) {
        entityManager.createNativeQuery("Delete from OAuth2TokenData where user_id =" +
                consumedAU.getId()).executeUpdate();
    }

    private void removeApiToken(AuthenticatedUser consumedAU) {
        ApiToken toRemove = authenticationService.findApiTokenByUser(consumedAU);
        if(null != toRemove) { //not all users have apiTokens
            entityManager.remove(toRemove);
        }
    }

    private void removeUser(AuthenticatedUser consumedAU) {
        AuthenticatedUserLookup consumedAUL = consumedAU.getAuthenticatedUserLookup();
        entityManager.remove(consumedAUL);
        entityManager.remove(consumedAU);
        BuiltinUser consumedBuiltinUser = builtinUserService.findByUserName(consumedAU.getUserIdentifier());
        if (consumedBuiltinUser != null){
            entityManager.remove(consumedBuiltinUser);
        }
    }
}
