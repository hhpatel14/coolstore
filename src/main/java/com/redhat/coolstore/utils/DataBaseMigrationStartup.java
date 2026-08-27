package com.redhat.coolstore.utils;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import io.quarkus.runtime.StartupEvent;
import java.util.logging.Logger;

/**
 * Startup observer - Flyway migrations are handled automatically by Quarkus
 * via application.properties configuration
 */
@ApplicationScoped
public class DataBaseMigrationStartup {

    @Inject
    Logger logger;

    void onStart(@Observes StartupEvent ev) {
        logger.info("Database migration managed by Quarkus Flyway extension");
    }
}