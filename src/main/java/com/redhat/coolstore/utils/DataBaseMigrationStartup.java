package com.redhat.coolstore.utils;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;
import java.util.logging.Logger;

/**
 * Created by tqvarnst on 2017-04-04.
 */
@ApplicationScoped
@Startup
public class DataBaseMigrationStartup {

    @Inject
    Logger logger;

    @PostConstruct
    private void startup() {
        logger.info("Flyway configured to run automatically at startup via quarkus.flyway.migrate-at-start=true");
    }

}