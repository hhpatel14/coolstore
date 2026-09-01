package com.redhat.coolstore.utils;

import io.quarkus.runtime.Startup;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

/**
 * Created by tqvarnst on 2017-04-04.
 * 
 * Modified for Quarkus - Flyway is now auto-configured via application.properties
 * quarkus.flyway.migrate-at-start=true handles database migration automatically
 */
@ApplicationScoped
@Startup
public class DataBaseMigrationStartup {

    @Inject
    Logger logger;

    // Quarkus Flyway auto-migration enabled via application.properties
    // This class can be simplified as Quarkus handles migration automatically
    public DataBaseMigrationStartup() {
        // Constructor for CDI
    }
}
