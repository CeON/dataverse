package edu.harvard.iq.dataverse.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.harvard.iq.dataverse.license.dto.ActiveLicenseDto;
import edu.harvard.iq.dataverse.persistence.datafile.license.License;
import edu.harvard.iq.dataverse.persistence.datafile.license.LicenseRepository;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.util.SystemConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonArray;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.io.StringReader;
import java.util.List;
import java.util.stream.Collectors;

@Path("info")
public class Info extends AbstractApiBean {

    private static final Logger logger = LoggerFactory.getLogger(Info.class);

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
        try {
            String licenses = new ObjectMapper().writeValueAsString(activeLicenses);
            JsonArray licensesArray = Json.createReader(new StringReader(licenses)).readArray();
            return allowCors(response(r -> ok(licensesArray)));
        } catch (JsonProcessingException jpe) {
            logger.warn("Error while creating response", jpe);
            return error(Response.Status.INTERNAL_SERVER_ERROR, "Error while creating response");
        }
    }
}
