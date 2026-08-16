package com.redhat.coolstore.utils;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import io.quarkus.runtime.StartupEvent;
import java.util.logging.Logger;

/**
 * Created by tqvarnst on 2017-04-04.
 * 
 * NOTE: In Quarkus, Flyway migration is handled automatically by the quarkus-flyway extension
 * when quarkus.flyway.migrate-at-start=true is set in application.properties.
 * This class is kept for logging purposes only.
 */
@ApplicationScoped
public class DataBaseMigrationStartup {

    @Inject
    Logger logger;

    void onStart(@Observes StartupEvent event) {
        logger.info("Database migration will be handled automatically by Quarkus Flyway extension");
    }
}