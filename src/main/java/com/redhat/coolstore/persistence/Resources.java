package com.redhat.coolstore.persistence;

import jakarta.enterprise.context.Dependent;

@Dependent
public class Resources {
    // EntityManager is automatically available for injection in Quarkus
    // No need for @PersistenceContext or @Produces
}
