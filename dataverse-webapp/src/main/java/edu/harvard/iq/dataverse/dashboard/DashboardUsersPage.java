package edu.harvard.iq.dataverse.dashboard;

import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.PermissionsWrapper;
import edu.harvard.iq.dataverse.UserServiceBean;
import edu.harvard.iq.dataverse.api.Admin;
import edu.harvard.iq.dataverse.authorization.AuthenticationProvider;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.mydata.Pager;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.userdata.UserListMaker;
import edu.harvard.iq.dataverse.userdata.UserListResult;
import edu.harvard.iq.dataverse.util.JsfHelper;

import javax.ejb.EJB;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@ViewScoped
@Named("DashboardUsersPage")
public class DashboardUsersPage implements java.io.Serializable {

    @EJB
    UserServiceBean userService;
    @Inject
    DataverseSession session;
    @Inject
    PermissionsWrapper permissionsWrapper;
    @Inject
    private DashboardUsersService dashboardUsersService;

    private static final Logger logger = Logger.getLogger(DashboardUsersPage.class.getCanonicalName());

    private Integer selectedPage = 1;
    private UserListMaker userListMaker = null;

    private Pager pager;
    private List<AuthenticatedUser> userList;

    private String searchTerm;

    public String init() {

        if ((session.getUser() != null) && (session.getUser().isAuthenticated()) && (session.getUser().isSuperuser())) {
            userListMaker = new UserListMaker(userService);
            runUserSearch();
        } else {
            return permissionsWrapper.notAuthorized();
        }

        return null;
    }

    public boolean runUserSearchWithPage(Integer pageNumber) {
        System.err.println("runUserSearchWithPage");
        setSelectedPage(pageNumber);
        runUserSearch();
        return true;
    }

    public boolean runUserSearch() {

        logger.fine("Run the search!");


        /**
         * (1) Determine the number of users returned by the count        
         */
        UserListResult userListResult = userListMaker.runUserSearch(searchTerm, UserListMaker.ITEMS_PER_PAGE, getSelectedPage(), null);
        if (userListResult == null) {
            try {
                throw new Exception("userListResult should not be null!");
            } catch (Exception ex) {
                Logger.getLogger(DashboardUsersPage.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        setSelectedPage(userListResult.getSelectedPageNumber());

        this.userList = userListResult.getUserList();
        this.pager = userListResult.getPager();

        return true;

    }


    public String getListUsersAPIPath() {
        //return "ok";
        return Admin.listUsersFullAPIPath;
    }

    /**
     * Number of total users
     *
     * @return
     */
    public String getUserCount() {

        return NumberFormat.getInstance().format(userService.getTotalUserCount());
    }

    /**
     * Number of total Superusers
     *
     * @return
     */
    public Long getSuperUserCount() {

        return userService.getSuperUserCount();
    }

    public List<AuthenticatedUser> getUserList() {
        return this.userList;
    }

    /**
     * Pager for when user list exceeds the number of display rows
     * (default: UserListMaker.ITEMS_PER_PAGE)
     *
     * @return
     */
    public Pager getPager() {
        return this.pager;
    }

    public void setSelectedPage(Integer pgNum) {
        if ((pgNum == null) || (pgNum < 1)) {
            this.selectedPage = 1;
        }
        selectedPage = pgNum;
    }

    public Integer getSelectedPage() {
        if ((selectedPage == null) || (selectedPage < 1)) {
            setSelectedPage(null);
        }
        return selectedPage;
    }

    public String getSearchTerm() {
        return searchTerm;
    }

    public void setSearchTerm(String searchTerm) {
        this.searchTerm = searchTerm;
    }

    public AuthenticatedUser getSelectedUser() {
        return selectedUser;
    }

    private AuthenticatedUser selectedUser = null;


    public void setSelectedUser(AuthenticatedUser user) {
        selectedUser = user;
    }

    public void saveSuperuserStatus() {

        // Retrieve the persistent version for saving to db
        logger.fine("Get persisent AuthenticatedUser for id: " + selectedUser.getId());
//        selectedUserPersistent = userService.find(selectedUser.getId());

        selectedUser = dashboardUsersService.changeSuperuserStatus(selectedUser);
    }

    public void cancelSuperuserStatusChange() {
        if(selectedUser != null) {
            selectedUser.setSuperuser(!selectedUser.isSuperuser());
            selectedUser = null;
        }
    }

    // Methods for the removeAllRoles for a user : 

    public void removeUserRoles() {
        logger.fine("Get persisent AuthenticatedUser for id: " + selectedUser.getId());

        try {
            selectedUser = dashboardUsersService.revokeAllRolesForUser(selectedUser);
        } catch (Exception ex) {
            JsfHelper.addFlashErrorMessage(BundleUtil.getStringFromBundle("dashboard.list_users.removeAll.message.failure", Collections.singletonList(selectedUser.getUserIdentifier())));
            return;
        }
        JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("dashboard.list_users.removeAll.message.success", Collections.singletonList(selectedUser.getUserIdentifier())));
    }

    public String getConfirmRemoveRolesMessage() {
        if (selectedUser != null) {
            return BundleUtil.getStringFromBundle("dashboard.list_users.tbl_header.roles.removeAll.confirmationText", Arrays.asList(selectedUser.getUserIdentifier()));
        }
        return BundleUtil.getStringFromBundle("dashboard.list_users.tbl_header.roles.removeAll.confirmationText");
    }

    public String getAuthProviderFriendlyName(String authProviderId) {

        return AuthenticationProvider.getFriendlyName(authProviderId);
    }
}
