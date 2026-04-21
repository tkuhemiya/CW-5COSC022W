package com.smartcampus.resource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Map;

@Path("/")
public class DiscoveryResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> discover() {
        return Map.of(
            "version", "1.0",
            "api", "/api/v1",
            "contact", "smartcampus@westminster.ac.uk",
            "resources", Map.of(
                "rooms", "/api/v1/rooms",
                "sensors", "/api/v1/sensors"
            )
        );
    }
}
