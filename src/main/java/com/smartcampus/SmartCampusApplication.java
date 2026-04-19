package com.smartcampus;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

@ApplicationPath("/api/v1")
public class SmartCampusApplication extends Application {
    // Jersey auto scans for @Path and @Provider
}
