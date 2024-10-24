package edu.harvard.iq.dataverse.api;

import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;

import javax.ws.rs.ApplicationPath;

@ApplicationPath("api/v1")
public class ApiConfiguration extends ResourceConfig {

    public ApiConfiguration() {
        packages("edu.harvard.iq.dataverse.api");
        register(MultiPartFeature.class);
    }
}