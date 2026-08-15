package com.redhat.coolstore.utils;

import jakarta.inject.Inject;
import java.util.logging.Logger;

/**
 * Flyway migration is now handled by Quarkus configuration.
 * See application.properties:
 *   quarkus.flyway.migrate-at-start=true
 *   quarkus.flyway.locations=db/migration
 * 
 * This class is kept for reference but is no longer needed.
 */
public class DataBaseMigrationStartup {

    @Inject
    Logger logger;

}