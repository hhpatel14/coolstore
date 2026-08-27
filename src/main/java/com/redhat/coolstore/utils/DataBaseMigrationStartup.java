package com.redhat.coolstore.utils;

import jakarta.inject.Inject;
import java.util.logging.Logger;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Created by tqvarnst on 2017-04-04.
 * Updated for Quarkus - Flyway migration is now handled automatically via quarkus.flyway.migrate-at-start=true
 */
@ApplicationScoped
public class DataBaseMigrationStartup {

    @Inject
    Logger logger;

    void onStart(@Observes StartupEvent ev) {
        logger.info("Database migration handled by Quarkus Flyway extension");
    }
}