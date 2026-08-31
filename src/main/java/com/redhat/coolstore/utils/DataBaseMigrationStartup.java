package com.redhat.coolstore.utils;

import jakarta.inject.Inject;
import java.util.logging.Logger;

/**
 * Database migration is now handled automatically by Quarkus Flyway extension
 * configured in application.properties with quarkus.flyway.migrate-at-start=true
 * This class is kept for compatibility but no longer performs manual migration
 */
public class DataBaseMigrationStartup {

    @Inject
    Logger logger;

    // Quarkus Flyway handles database migration automatically on startup
    // No manual Flyway initialization needed

}