package edu.harvard.iq.dataverse.search;

import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.engine.command.impl.CreateSavedSearchCommand;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.dataverse.link.SavedSearch;
import edu.harvard.iq.dataverse.persistence.dataverse.link.SavedSearchFilterQuery;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.util.JsfHelper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringEscapeUtils;

import javax.ejb.Stateless;
import java.util.ArrayList;
import java.util.List;

@Stateless
public class DataverseLinkingService {

    private DataverseSession dataverseSession;
    private DataverseRequestServiceBean dvRequestService;
    private EjbDataverseEngine commandEngine;

    // -------------------- CONSTRUCTORS --------------------

    public DataverseLinkingService() {
    }



    // -------------------- LOGIC --------------------

    public SavedSearch saveSavedDataverseSearch(String query, List<String> filteredQueries, Dataverse dataverseToBeLinked){

        SavedSearch savedSearch = new SavedSearch(query, dataverseToBeLinked, (AuthenticatedUser) dataverseSession.getUser());

        filteredQueries.stream()
                .filter(filteredQuery -> CollectionUtils.isNotEmpty(filteredQueries))
                .forEach(filteredQuery -> savedSearch.getSavedSearchFilterQueries().add(new SavedSearchFilterQuery(filteredQuery, savedSearch)));

        CreateSavedSearchCommand cmd = new CreateSavedSearchCommand(dvRequestService.getDataverseRequest(), dataverseToBeLinked, savedSearch);
        try {
            commandEngine.submit(cmd);

            List<String> arguments = new ArrayList<>();
            String linkString = "<a href=\"/dataverse/" + dataverseToBeLinked.getAlias() + "\">" + StringEscapeUtils.escapeHtml(dataverseToBeLinked.getDisplayName()) + "</a>";
            arguments.add(linkString);
            String successMessageString = BundleUtil.getStringFromBundle("dataverse.saved.search.success", arguments);
            JsfHelper.addFlashSuccessMessage(successMessageString);
            return returnRedirect();
        } catch (CommandException ex) {
            String msg = "There was a problem linking this search to yours: " + ex;
            logger.severe(msg);
            JsfHelper.addFlashErrorMessage(BundleUtil.getStringFromBundle("dataverse.saved.search.failure") + " " + ex);
            return returnRedirect();
        }

    }
}
