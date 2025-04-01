package com.redhat.coolstore.utils;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.agroal.api.AgroalDataSource;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Database migration startup class using Flyway
 */
@ApplicationScoped
public class DataBaseMigrationStartup {

    @Inject
    Logger logger;

    @Inject
    AgroalDataSource dataSource;

    @PostConstruct
    private void startup() {
        if (dataSource == null) {
            logger.severe("DataSource is not available");
            return;
        }

        try {
            logger.info("Initializing/migrating the database using Flyway");
            Flyway flyway = new Flyway();
            flyway.setDataSource(dataSource);
            flyway.baseline();
            // Start the db.migration
            flyway.migrate();
            logger.info("Database migration completed successfully");
        } catch (FlywayException e) {
            logger.log(Level.SEVERE, "Failed to initialize the database: " + e.getMessage(), e);
            throw new RuntimeException("Database migration failed", e);
        }
    }
}