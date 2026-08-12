# Migration Specification: Java EE 7 to Quarkus 3

**Project:** coolstore-monolith  
**Source:** Java EE 7 (WebLogic/JBoss compatible)  
**Target:** Quarkus 3.x  
**Date:** 2026-08-12

---

## Executive Summary

This specification outlines the migration of a Java EE 7 monolithic e-commerce application (coolstore-monolith) from a traditional application server deployment model (WAR on WebLogic/JBoss) to a modern Quarkus 3 standalone application. The migration will transform the application from a server-dependent WAR to a self-contained JAR with embedded runtime.

**Scope:** Full application migration including build configuration, messaging, EJB components, lifecycle management, and application configuration.

**Effort Estimate:** Medium complexity - approximately 15-20 distinct transformation steps across 27 Java source files.

---

## Current State Analysis

### Application Architecture

The coolstore-monolith is a traditional Java EE 7 web application with the following characteristics:

**Build & Packaging:**
- Maven-based build with WAR packaging
- Java EE 7 API dependencies (javaee-web-api, javaee-api)
- Java 8 source/target compatibility
- Flyway database migrations (version 4.1.2)

**Application Components:**
- **Model Layer** (8 classes): JPA entities with `javax.persistence` annotations
  - CatalogItemEntity, InventoryEntity, Order, OrderItem
  - Product, Promotion, ShoppingCart, ShoppingCartItem
  
- **Service Layer** (10 classes): Business logic with EJB and CDI
  - **Stateless EJBs:** OrderService, ShippingService, ShoppingCartOrderProcessor
  - **Stateful EJB:** ShoppingCartService (with JNDI lookups)
  - **Message-Driven Beans:** OrderServiceMDB, InventoryNotificationMDB
  - **CDI Beans:** CatalogService, ProductService, PromoService

- **REST API Layer** (4 classes): JAX-RS endpoints
  - CartEndpoint (SessionScoped), OrderEndpoint, ProductEndpoint, RestApplication

- **Utilities** (5 classes): Cross-cutting concerns
  - StartupListener (WebLogic ApplicationLifecycleListener)
  - DataBaseMigrationStartup, Producers, Transformers, Resources

**Technology Stack:**
- Java EE 7 APIs (javax.* namespace)
- JMS 2.0 for asynchronous messaging (JMS topics)
- JPA 2.1 with Hibernate provider
- JAX-RS for REST endpoints
- CDI 1.2 for dependency injection
- WebLogic-specific lifecycle hooks

**Configuration Files:**
- `pom.xml` - Maven WAR project with Java EE dependencies
- `persistence.xml` - JPA configuration with JNDI datasource `java:jboss/datasources/CoolstoreDS`
- `web.xml` - Minimal servlet 3.0 descriptor with distributable flag
- `beans.xml` - CDI activation (implicit)

---

## Target State

### Quarkus 3 Architecture

The migrated application will be a Quarkus 3 application with:

**Build & Packaging:**
- Maven-based build with JAR packaging
- Quarkus BOM (io.quarkus.platform:quarkus-bom:3.x)
- Quarkus Maven plugin for build and dev mode
- Java 17+ compatibility (Quarkus 3 minimum requirement)
- Quarkus-compatible Flyway extension

**Application Components:**
- **Model Layer:** JPA entities with `jakarta.persistence` annotations (Panache patterns optional)
- **Service Layer:** 
  - CDI @ApplicationScoped beans (replacing @Stateless/@Stateful)
  - SmallRye Reactive Messaging (replacing MDBs and JMS producers)
  - Direct CDI injection (replacing JNDI lookups)
- **REST API Layer:** JAX-RS with `jakarta.ws.rs` annotations
- **Lifecycle:** Quarkus lifecycle events (replacing WebLogic listeners)

**Technology Stack:**
- Jakarta EE 10 APIs (jakarta.* namespace)
- SmallRye Reactive Messaging (replacing JMS/MDB)
- Hibernate ORM with Panache (optional enhancement)
- RESTEasy Reactive or Classic
- Quarkus CDI (Arc)
- Quarkus lifecycle events

**Configuration Files:**
- `pom.xml` - Quarkus Maven project with JAR packaging
- `application.properties` - Unified Quarkus configuration (replacing persistence.xml, datasource config)
- Remove: `web.xml`, `beans.xml`, `persistence.xml`

---

## Migration Strategy

### Approach

The migration follows a **phased, bottom-up approach** aligned with the domain skill phases:

1. **Build Config** - Foundation layer (pom.xml transformation)
2. **App Config** - Configuration consolidation (XML → properties)
3. **EJB to CDI** - Component model transformation (core business logic)
4. **Messaging** - Async communication modernization (MDB/JMS → Reactive Messaging)
5. **Lifecycle** - Application lifecycle hooks (WebLogic → Quarkus events)
6. **Cleanup** - Remove legacy artifacts and verify migration

Each phase includes a build gate - the project must compile before proceeding to the next phase.

### Key Transformations

| Component | From | To |
|-----------|------|-----|
| **Packaging** | WAR | JAR |
| **APIs** | javax.* (Java EE 7) | jakarta.* (Jakarta EE 10) |
| **Dependencies** | javaee-web-api, javaee-api | Quarkus extensions |
| **EJB @Stateless** | OrderService, ShippingService, etc. | @ApplicationScoped CDI beans |
| **EJB @Stateful** | ShoppingCartService | @ApplicationScoped with @SessionScoped REST endpoints |
| **JNDI Lookups** | InitialContext, lookup() | Direct @Inject |
| **MDBs** | @MessageDriven beans | @Incoming reactive methods |
| **JMS Producers** | JMSContext, Topic | @Channel Emitter |
| **Datasource** | JNDI java:jboss/datasources/CoolstoreDS | quarkus.datasource.* properties |
| **JPA Config** | persistence.xml | application.properties |
| **Lifecycle** | ApplicationLifecycleListener | @Observes StartupEvent/ShutdownEvent |
| **Build** | maven-war-plugin | quarkus-maven-plugin |

---

## Migration Decisions

### Architectural Decisions

**AD-1: Maintain Monolithic Architecture**
- **Decision:** Keep the application as a single deployable unit
- **Rationale:** Migration scope is runtime modernization, not decomposition
- **Impact:** All services remain in one module

**AD-2: Use Application-Scoped CDI Beans**
- **Decision:** Replace @Stateless EJBs with @ApplicationScoped beans
- **Rationale:** Equivalent lifecycle for stateless services; simpler than @RequestScoped
- **Impact:** Services become CDI-managed; transactional behavior preserved with @Transactional

**AD-3: Replace Stateful EJB with Application-Scoped + Session-Scoped Endpoints**
- **Decision:** ShoppingCartService becomes @ApplicationScoped; cart state managed in @SessionScoped CartEndpoint
- **Rationale:** Quarkus doesn't support @Stateful EJB; session state belongs at REST layer
- **Impact:** ShoppingCart instances managed per HTTP session, not per EJB instance

**AD-4: Use SmallRye Reactive Messaging for Async**
- **Decision:** Replace JMS/MDB with Quarkus messaging (in-memory channels for now)
- **Rationale:** Modern async programming model; extensible to Kafka/AMQP later
- **Impact:** MDBs become methods with @Incoming; producers use @Channel Emitter

**AD-5: Consolidate Configuration to application.properties**
- **Decision:** Move all XML config (persistence.xml, datasource) to application.properties
- **Rationale:** Quarkus convention; single source of truth
- **Impact:** Datasource JNDI names eliminated; properties-based configuration

**AD-6: Upgrade to Java 17**
- **Decision:** Bump source/target from Java 8 to Java 17
- **Rationale:** Quarkus 3 requires Java 17 minimum
- **Impact:** Can leverage modern Java features; verify no incompatibilities

### Technical Decisions

**TD-1: Messaging Transport (Initial)**
- **Decision:** Use in-memory channels for topic/orders
- **Rationale:** Simplest migration path; preserves async semantics
- **Future:** Easy to switch to quarkus-smallrye-reactive-messaging-kafka

**TD-2: Flyway Version**
- **Decision:** Upgrade Flyway from 4.1.2 to Quarkus-managed version
- **Rationale:** Quarkus Flyway extension provides better integration
- **Impact:** Review migration scripts for compatibility

**TD-3: Hibernate Provider**
- **Decision:** Continue using Hibernate ORM (via Quarkus)
- **Rationale:** No need to change provider; Quarkus defaults to Hibernate
- **Impact:** Minimal changes to entity classes beyond jakarta.* imports

**TD-4: REST Framework**
- **Decision:** Use RESTEasy Classic (quarkus-resteasy-jackson)
- **Rationale:** Lower-risk migration path; behavioral compatibility with JAX-RS 2.x
- **Alternative:** RESTEasy Reactive for better performance (consider post-migration)

**TD-5: Transaction Management**
- **Decision:** Use @Transactional annotation on service methods
- **Rationale:** Replaces container-managed transactions from EJB
- **Impact:** Add @Transactional to OrderService.save(), CatalogService.updateInventoryItems()

---

## Risk Assessment

### High Risk Items

**R-1: Stateful Session Management**
- **Component:** ShoppingCartService (@Stateful EJB)
- **Risk:** Session affinity and cart state persistence across requests
- **Mitigation:** Test thoroughly with multiple concurrent users; consider externalized session store if needed

**R-2: JMS Topic Behavior**
- **Component:** OrderServiceMDB, InventoryNotificationMDB (both subscribe to topic/orders)
- **Risk:** Multiple consumers on same topic; ensure both still receive messages
- **Mitigation:** Use separate channel names or configure Reactive Messaging for pub/sub

**R-3: JNDI Datasource Compatibility**
- **Component:** persistence.xml references `java:jboss/datasources/CoolstoreDS`
- **Risk:** Datasource name changes may break external tooling/scripts
- **Mitigation:** Document new datasource config; update deployment scripts

### Medium Risk Items

**R-4: WebLogic-Specific Code**
- **Components:** StartupListener, InventoryNotificationMDB (JNDI lookups)
- **Risk:** Tight coupling to WebLogic APIs
- **Mitigation:** Replace with Quarkus equivalents; well-documented in domain skill

**R-5: Flyway Migration Compatibility**
- **Component:** DataBaseMigrationStartup with Flyway 4.1.2
- **Risk:** Version jump may introduce incompatibilities
- **Mitigation:** Test migrations in isolated environment; review Flyway upgrade guide

### Low Risk Items

**R-6: JAX-RS Endpoint Behavior**
- **Components:** CartEndpoint, OrderEndpoint, ProductEndpoint
- **Risk:** Subtle differences between Java EE and Quarkus REST implementations
- **Mitigation:** Integration tests cover existing functionality

**R-7: JPA Entity Mappings**
- **Components:** 8 JPA entities
- **Risk:** Minimal - only namespace changes
- **Mitigation:** Automated find/replace for javax → jakarta

---

## Out of Scope

The following items are **not** included in this migration:

1. **Microservices Decomposition** - Application remains monolithic
2. **Database Schema Changes** - Existing schema and migrations preserved
3. **Frontend Modernization** - JSP/JavaScript UI unchanged
4. **Cloud-Native Enhancements** - No Kubernetes operators, health checks, metrics (can be added post-migration)
5. **Performance Optimization** - Focus is functional equivalence, not performance tuning
6. **Reactive Programming Model** - Services remain imperative; only messaging is reactive
7. **Security Enhancements** - Existing security model (Keycloak integration) preserved
8. **API Changes** - REST endpoints maintain identical contracts

---

## Success Criteria

The migration is successful when:

1. ✅ **Build Success:** `mvn clean package` produces a runnable JAR
2. ✅ **Runtime Success:** Application starts with `mvn quarkus:dev`
3. ✅ **Functional Equivalence:** All REST endpoints return expected responses
4. ✅ **Data Access:** JPA entities can read/write to database
5. ✅ **Messaging:** Orders published to topic are consumed by both MDB replacements
6. ✅ **Lifecycle:** Startup and shutdown events fire correctly
7. ✅ **Zero Legacy Artifacts:** No javax.* EE imports, no EJB annotations, no XML config files
8. ✅ **Database Migrations:** Flyway migrations run successfully on startup

---

## Dependencies & Prerequisites

### Before Migration

- [ ] Java 17 JDK installed and configured
- [ ] Maven 3.8.1+ installed
- [ ] PostgreSQL or H2 database available (per existing datasource config)
- [ ] Git repository with clean working tree
- [ ] Baseline tests pass (if tests exist and are not skipped)

### During Migration

- [ ] Access to /opt/skills/javaee-to-quarkus domain skill
- [ ] Ability to run `mvn compile` after each phase
- [ ] Ability to run graphify for code analysis

### Post-Migration

- [ ] Quarkus CLI (optional, for dev experience)
- [ ] Container runtime (Docker/Podman) for packaging
- [ ] Updated CI/CD pipelines to use `mvn package` instead of WAR deployment

---

## Next Steps

Upon approval of this specification:

1. **Implementation Plan** will be generated in `.konveyor/implementation.md`
2. **Phase 1: Build Config** will be executed first
3. Each subsequent phase will be gated by a successful build
4. Final verification will ensure all success criteria are met

---

## Appendix: File Inventory

### Files to Modify (27 Java + 1 POM)

**Model (8 files):** javax → jakarta imports only
- CatalogItemEntity.java, InventoryEntity.java, Order.java, OrderItem.java
- Product.java, Promotion.java, ShoppingCart.java, ShoppingCartItem.java

**Service (10 files):** EJB → CDI, JMS → Reactive Messaging
- CatalogService.java, InventoryNotificationMDB.java, OrderService.java
- OrderServiceMDB.java, ProductService.java, PromoService.java
- ShippingService.java, ShippingServiceRemote.java (delete)
- ShoppingCartOrderProcessor.java, ShoppingCartService.java

**REST (4 files):** JAX-RS namespace changes
- CartEndpoint.java, OrderEndpoint.java, ProductEndpoint.java, RestApplication.java

**Utils (5 files):** Lifecycle, producers, migrations
- DataBaseMigrationStartup.java, Producers.java, StartupListener.java
- Transformers.java, Resources.java

**Build:**
- pom.xml

### Files to Delete

- src/main/resources/META-INF/persistence.xml
- src/main/webapp/WEB-INF/web.xml
- src/main/webapp/WEB-INF/beans.xml
- src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java
- src/main/java/weblogic/** (entire package)

### Files to Create

- src/main/resources/application.properties

---

**Specification prepared by:** Goose AI Migration Agent  
**Review Status:** Ready for approval  
**Approval Date:** _Pending_
