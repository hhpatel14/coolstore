package com.redhat.coolstore.rest;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

// JAX-RS activation is automatic in Quarkus. This class can be removed, or kept for explicit path configuration.
@ApplicationPath("/services")
public class RestApplication extends Application {

}
