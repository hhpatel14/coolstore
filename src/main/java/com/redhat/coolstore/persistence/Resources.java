package com.redhat.coolstore.persistence;

import jakarta.enterprise.context.Dependent;

@Dependent
public class Resources {
    // EntityManager producer removed - Quarkus provides EntityManager beans automatically via CDI
    // Services can now directly @Inject EntityManager
}
