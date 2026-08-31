package com.redhat.coolstore.utils;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Database migration is now handled by Quarkus Flyway extension
 * Configured in application.properties:
 * quarkus.flyway.migrate-at-start=true
 */
@ApplicationScoped
@Transactional
public class DataBaseMigrationStartup {
    // Migration handled automatically by Quarkus Flyway extension
}