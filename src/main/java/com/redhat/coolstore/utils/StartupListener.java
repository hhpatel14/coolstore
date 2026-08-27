package com.redhat.coolstore.utils;

import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.logging.Logger;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.ShutdownEvent;

@ApplicationScoped
public class StartupListener {

    @Inject
    Logger log;

    void onStart(@Observes StartupEvent evt) {
        log.info("AppListener(postStart)");
    }

    void onShutdown(@Observes ShutdownEvent evt) {
        log.info("AppListener(preStop)");
    }

}
