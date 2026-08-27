package com.redhat.coolstore.utils;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.logging.Logger;

/**
 * Created by tqvarnst on 2017-04-04.
 * Note: Flyway migration is now handled automatically by Quarkus (see application.properties)
 */
@Singleton
public class DataBaseMigrationStartup {

    @Inject
    Logger logger;

    @PostConstruct
    private void startup() {
        logger.info("Database migration will be handled by Quarkus Flyway extension");
    }

}