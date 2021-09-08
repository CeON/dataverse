package edu.harvard.iq.dataverse.api;

import edu.harvard.iq.dataverse.license.dto.ActiveLicenseDto;
import edu.harvard.iq.dataverse.persistence.datafile.license.License;
import edu.harvard.iq.dataverse.persistence.datafile.license.LicenseRepository;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.util.SystemConfig;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.json.Json;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Path("info")
public class Info extends AbstractApiBean {

    @Inject
    SettingsServiceBean settingsService;

    @Inject
    private LicenseRepository licenseRepository;

    @EJB
    SystemConfig systemConfig;

    @GET
    @Path("settings/:DatasetPublishPopupCustomText")
    public Response getDatasetPublishPopupCustomText() {
        String setting = settingsService.getValueForKey(SettingsServiceBean.Key.DatasetPublishPopupCustomText);
        if (setting != null) {
            return ok(Json.createObjectBuilder().add("message", setting));
        } else {
            return notFound("Setting " + SettingsServiceBean.Key.DatasetPublishPopupCustomText + " not found");
        }
    }

    @GET
    @Path("version")
    public Response getInfo() {
        String versionStr = systemConfig.getVersionWithBuild();

        return allowCors(response(req -> ok(Json.createObjectBuilder().add("version", versionStr))));
    }

    @GET
    @Path("server")
    public Response getServer() {
        return response(req -> ok(systemConfig.getDataverseServer()));
    }

    @GET
    @Path("apiTermsOfUse")
    public Response getTermsOfUse() {
        return allowCors(response(req -> ok(systemConfig.getApiTermsOfUse())));
    }

    @GET
    @Path("activeLicenses")
    public Response getActiveLicenses() {
        List<ActiveLicenseDto> activeLicenses = licenseRepository.findActiveOrderedByPosition().stream()
                .map(License::getName)
                .map(ActiveLicenseDto::new)
                .collect(Collectors.toList());

        return allowCors(response(r -> ok(new ActiveLicensesResponse(STATUS_OK, activeLicenses))));
    }

    // -------------------- INNER CLASSES --------------------

    static class ActiveLicensesResponse {
        private String status;
        private List<ActiveLicenseDto> data = new ArrayList<>();

        // -------------------- CONSTRUCTORS --------------------

        public ActiveLicensesResponse(String status, List<ActiveLicenseDto> data) {
            this.status = status;
            this.data.addAll(data);
        }

        // -------------------- GETTERS --------------------

        public String getStatus() {
            return status;
        }

        public List<ActiveLicenseDto> getData() {
            return data;
        }
    }
}
