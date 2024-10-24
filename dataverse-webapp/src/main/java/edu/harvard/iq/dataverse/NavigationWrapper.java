/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.harvard.iq.dataverse;

import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.omnifaces.cdi.ViewScoped;

import javax.faces.context.FacesContext;
import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author gdurand
 */
@ViewScoped
@Named
public class NavigationWrapper implements Serializable {

    /**
     * to regenerate the query string, we need to use the parameter map; however this can contain internal POST parameters
     * that we don't want, so we filter through a list of paramters we do allow
     * @todo verify what needs to be in this list of available parameters (for example do we want to repeat searches when you login?
     */
    private static final Set<String> ACCEPTABLE_REDIRECT_PARAMS = Sets.newHashSet(
            "id", "alias", "version", "ownerId", "persistentId", "versionId", "datasetId",
            "selectedFileIds", "mode", "dataverseId", "fileId", "datasetVersionId", "guestbookId",
            "q", "types", "sort", "order");

    @Inject
    DataverseSession session;

    String redirectPage;


    public String getRedirectPage() {
        return !StringUtils.isEmpty(getPageFromContext()) ? "?redirectPage=" + getPageFromContext() : "";
    }

    public String getPageFromContext() {
        if (redirectPage == null) {
            StringBuilder redirectBuilder = new StringBuilder();

            HttpServletRequest req = (HttpServletRequest) FacesContext.getCurrentInstance().getExternalContext().getRequest();
            redirectBuilder.append(req.getServletPath());

            if (req.getParameterMap() != null) {
                StringBuilder queryString = new StringBuilder();
                for (Map.Entry<String, String[]> entry : req.getParameterMap().entrySet()) {
                    String name = entry.getKey();
                    if (isAcceptableRedirectParam(name)) {
                        String value = entry.getValue()[0];
                        queryString.append(queryString.length() == 0 ? "?" : "&").append(name).append("=").append(value);
                    }
                }
                redirectBuilder.append(queryString);
            }

            try {
                redirectPage = URLEncoder.encode(redirectBuilder.toString(), "UTF-8");

            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(DataverseHeaderFragment.class.getName()).log(Level.SEVERE, null, ex);
                redirectPage = "";
            }
        }

        return redirectPage;
    }

    public String notAuthorized() {
        if (!session.getUser().isAuthenticated()) {
            return "/loginpage.xhtml" + getRedirectPage();
        } else {
            return sendError(HttpServletResponse.SC_FORBIDDEN);
        }
    }

    public String notFound() {
        return sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    // -------------------- PRIVATE --------------------

    private String sendError(int errorCode) {
        FacesContext context = FacesContext.getCurrentInstance();
        try {
            context.getExternalContext().responseSendError(errorCode, null);
        } catch (IOException ex) {
            Logger.getLogger(PermissionsWrapper.class.getName()).log(Level.SEVERE, null, ex);
        }
        context.responseComplete();
        return "";
    }

    private boolean isAcceptableRedirectParam(String paramName) {
        return ACCEPTABLE_REDIRECT_PARAMS.contains(paramName) || isFilterQueryParam(paramName);
    }

    private boolean isFilterQueryParam(String paramName) {
        return paramName.matches("fq[0-9]+");
    }
}
