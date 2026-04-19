package com.smartcampus;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import java.io.IOException;
import java.net.URI;

@ApplicationPath("/api/v1")
public class SmartCampusApplication extends Application {
    public static final String BASE_URI = "http://localhost:8080/";

    public static void main(String[] args) throws IOException {
        final ResourceConfig config = ResourceConfig.forApplication(new SmartCampusApplication());
        final HttpServer server = GrizzlyHttpServerFactory.createHttpServer(URI.create(BASE_URI), config);

        System.out.println("API at " + BASE_URI + "api/v1/");
        System.out.println("Press Enter to stop...");

        System.in.read();
        server.shutdownNow();
        System.out.println("Server stopped.");
    }
}
