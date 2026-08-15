# Migration Specification: Java EE 7 to Quarkus 3

**Generated**: 2026-08-15T20:18:00Z  
**Project**: coolstore-monolith  
**Source**: Java EE 7 (JBoss EAP 7.4/WildFly) WAR application  
**Target**: Quarkus 3 JAR application

---

## Executive Summary

This specification outlines the migration of the Coolstore monolith from Java EE 7 (running on JBoss EAP/WildFly) to Quarkus 3. The application is a small-to-medium e-commerce backend (26 Java classes) with REST APIs, JPA persistence, JMS messaging, and Keycloak authentication. The migration will transform it into a cloud-native Quarkus application while cleaning up legacy WebLogic code from a previous migration.

**Migration Type**: Runtime modernization (Java EE → Quarkus)  
**Complexity**: Medium  
**Estimated Effort**: 3-5 days  
**Risk Level**: Low-Medium

---

## Current State

### Architecture
- **Packaging**: WAR (deployed to JBoss EAP 7.4/WildFly)
- **Java Version**: Java 8
- **Build Tool**: Maven
- **Application Server**: JBoss EAP 7.4 or WildFly

### Technology Stack
| Component | Current Technology | Version |
|-----------|-------------------|---------|
| Programming Model | Java EE | 7 |
| Dependency Injection | CDI | 1.1 |
| EJB | Session/Message-Driven Beans | 3.2 |
| Persistence | JPA (Hibernate) | 2.1 |
| REST API | JAX-RS | 2.0 |
| Messaging | JMS | 2.0 |
| Database | PostgreSQL | N/A |
| DB Migration | Flyway | 4.1.2 |
| Authentication | Keycloak (Java EE adapter) | N/A |
| Frontend | JSP + AngularJS 1.x | N/A |

### Application Layers
1. **Model Layer** (`com.redhat.coolstore.model`)
   - 8 JPA entities: CatalogItemEntity, InventoryEntity, Order, OrderItem, Product, Promotion, ShoppingCart, ShoppingCartItem
   - Clean domain model with JPA 2.1 annotations

2. **Persistence Layer** (`com.redhat.coolstore.persistence`)
   - EntityManager producer (Resources.java)
   - Datasource: `java:jboss/datasources/CoolstoreDS`
   - Flyway migrations: V1_1__CreateSchema.sql, V1_2__AddInitialData.sql

3. **Service Layer** (`com.redhat.coolstore.service`)
   - **@Stateless EJBs**: ProductService, CatalogService, OrderService, ShoppingCartOrderProcessor
   - **@Stateful EJB**: ShoppingCartService (1 instance per user session)
   - **@Remote EJB**: ShippingService (with JNDI lookup)
   - **Message-Driven Beans**: OrderServiceMDB (functional), InventoryNotificationMDB (broken WebLogic code)
   - **Plain CDI**: PromoService
   - **JMS Producer**: ShoppingCartOrderProcessor publishes to `topic/orders`

4. **REST Layer** (`com.redhat.coolstore.rest`)
   - RestApplication (@ApplicationPath("/services"))
   - 3 endpoints: ProductEndpoint, CartEndpoint, OrderEndpoint
   - Session-scoped CartEndpoint

5. **Utilities** (`com.redhat.coolstore.utils`)
   - DataBaseMigrationStartup (@Singleton @Startup) - runs Flyway migrations
   - StartupListener (WebLogic ApplicationLifecycleListener) - legacy, non-functional
   - Transformers (JSON serialization)
   - Producers (Logger producer)

### Legacy Code Issues
The application contains WebLogic stub classes and JNDI code from a previous WebLogic-to-JBoss migration:
- `weblogic.application.*` package (stub implementations)
- `StartupListener.java` extends WebLogic ApplicationLifecycleListener (non-functional)
- `InventoryNotificationMDB.java` contains WebLogic JNDI code (broken, uses t3://localhost:7001)

### Messaging Architecture
- **Broker**: Embedded JMS (topic/orders)
- **Consumers**:
  - OrderServiceMDB: Processes orders, updates inventory (functional)
  - InventoryNotificationMDB: Low inventory alerts (broken WebLogic JNDI)
- **Producer**: ShoppingCartOrderProcessor sends order messages

### Authentication
- **Technology**: Keycloak
- **Configuration**: `src/main/webapp/keycloak.json`
- **Realm**: eap
- **Client**: eap-app (public client)
- **Auth Server**: http://localhost:8081/

### Frontend
- **Technology**: JSP serving AngularJS 1.x SPA
- **Files**: index.jsp (initializes HTTP session), health.jsp
- **UI Framework**: PatternFly
- **Package Manager**: Bower

---

## Target State

### Architecture
- **Packaging**: JAR (standalone Quarkus application)
- **Java Version**: Java 17+ (Quarkus 3 minimum)
- **Build Tool**: Maven with Quarkus plugin
- **Runtime**: Quarkus 3 (no application server)

### Technology Stack
| Component | Target Technology | Rationale |
|-----------|------------------|-----------|
| Framework | Quarkus | 3.x LTS |
| Dependency Injection | CDI (ArC) | 4.0 (Quarkus subset) |
| EJB | CDI beans | Replace with @ApplicationScoped |
| Persistence | Hibernate ORM | 6.x (Quarkus-optimized) |
| REST API | RESTEasy Reactive | JAX-RS 3.1 compatible |
| Messaging | SmallRye Reactive Messaging | In-memory connector |
| Database | PostgreSQL | No change |
| DB Migration | Flyway | Quarkus extension |
| Authentication | Quarkus OIDC | Keycloak integration |
| Frontend | Static files | Serve from META-INF/resources |

### Migration Approach

#### 1. Monolith Preservation
Keep the application as a single Quarkus module. The codebase is appropriately sized (26 classes) and well-layered for a monolith. Quarkus fast startup and low memory footprint make monoliths viable for cloud deployments.

#### 2. Legacy Code Cleanup
Remove all WebLogic artifacts per questionnaire decisions:
- Delete `weblogic.*` package entirely
- Remove `StartupListener.java`
- Fix or remove `InventoryNotificationMDB` (determine if actually used)

#### 3. EJB to CDI Conversion
- **@Stateless** → **@ApplicationScoped** (ProductService, CatalogService, OrderService, ShoppingCartOrderProcessor)
- **@Stateful** → **@SessionScoped** or custom state management (ShoppingCartService)
- **@Remote EJB** → Local CDI bean (ShippingService), remove JNDI lookup
- **@Singleton @Startup** → CDI @ApplicationScoped with lifecycle events (DataBaseMigrationStartup)

#### 4. Messaging Migration
Replace JMS with SmallRye Reactive Messaging:
- **MDB onMessage()** → **@Incoming** methods
- **JMS Producer** → **@Channel Emitter** or **@Outgoing**
- **topic/orders** → In-memory channel "orders"

*Note*: In-memory connector is suitable for single-instance deployments. If production requires multi-replica deployment with shared broker, switch to Kafka or AMQP connector.

#### 5. Configuration Migration
- **persistence.xml** → Delete, configure in application.properties
- **keycloak.json** → Delete, configure with quarkus.oidc.* properties
- **web.xml** → Delete (not needed)
- **beans.xml** → Delete (CDI enabled by default in Quarkus)

#### 6. View Layer Simplification
- Move AngularJS files from `src/main/webapp/` to `src/main/resources/META-INF/resources/`
- Delete JSP files (index.jsp, health.jsp)
- Convert index.jsp to index.html (remove session initialization)
- Replace health.jsp with Quarkus SmallRye Health extension

#### 7. Authentication Migration
- Add `quarkus-oidc` extension
- Configure in application.properties:
  - `quarkus.oidc.auth-server-url`
  - `quarkus.oidc.client-id`
  - Application type (web-app for frontend + backend)
- Remove Java EE Keycloak adapter dependency

---

## Questionnaire Decisions Applied

The following decisions from `.konveyor/questionnaire.json` are incorporated:

### 1. WebLogic Stub Classes → **Remove**
**Decision**: `remove-stubs` (confirmed)
- Delete `weblogic.*` package
- Remove `StartupListener.java`
- Fix `InventoryNotificationMDB` WebLogic JNDI code or remove if unused
- Use Quarkus lifecycle events (@Observes StartupEvent/ShutdownEvent)

### 2. View Layer → **Static Files + API**
**Decision**: `static-files-api-only` (needs-confirmation)
- Convert JSP to static HTML
- Serve AngularJS from `META-INF/resources/`
- Replace JSP session with Keycloak tokens
- Use Quarkus SmallRye Health for health checks

### 3. Authentication → **Quarkus OIDC**
**Decision**: `quarkus-oidc` (confirmed)
- Use `quarkus-oidc` extension
- Migrate config from keycloak.json to application.properties
- Reuse existing Keycloak realm without changes

### 4. Database/Persistence → **Hibernate ORM + Flyway**
**Decision**: `quarkus-hibernate-orm-flyway` (confirmed)
- Keep PostgreSQL, Hibernate, and Flyway
- Add Quarkus extensions: hibernate-orm, jdbc-postgresql, flyway
- Configure datasource in application.properties
- Keep existing JPA entities and Flyway migration scripts
- Remove @PersistenceContext producer (auto-injection in Quarkus)

### 5. Messaging → **Reactive Messaging (In-Memory)**
**Decision**: `quarkus-reactive-messaging-memory` (needs-confirmation)
- Use SmallRye Reactive Messaging with in-memory connector
- Convert OrderServiceMDB to @Incoming("orders")
- Determine status of InventoryNotificationMDB (fix or remove)
- Convert ShoppingCartOrderProcessor to @Channel Emitter
- **Confirmation needed**: Production deployment model (single vs. multi-replica)

### 6. Scope/Architecture → **Migrate Monolith with Cleanup**
**Decision**: `migrate-monolith-cleanup` (confirmed)
- Keep as single Quarkus application
- Remove all legacy WebLogic code
- Convert EJB Remote to local CDI
- Clean, maintainable Quarkus monolith

---

## Migration Phases

Following the javaee-to-quarkus skill phases:

### Phase 1: Build Config
- Change packaging: `<packaging>war</packaging>` → `<packaging>jar</packaging>`
- Add Quarkus BOM and plugin
- Replace Java EE dependencies with Quarkus extensions:
  - `javaee-web-api` → Remove
  - Add `quarkus-resteasy-reactive-jackson`
  - Add `quarkus-hibernate-orm`
  - Add `quarkus-jdbc-postgresql`
  - Add `quarkus-flyway`
  - Add `quarkus-oidc`
  - Add `quarkus-smallrye-reactive-messaging`
  - Add `quarkus-smallrye-health`
- Update Java version: 1.8 → 17
- Remove `maven-war-plugin`

### Phase 2: App Config
- Delete `src/main/resources/META-INF/persistence.xml`
- Create `src/main/resources/application.properties` with:
  - Datasource configuration (quarkus.datasource.*)
  - Hibernate ORM configuration (quarkus.hibernate-orm.*)
  - Flyway configuration (quarkus.flyway.*)
  - OIDC configuration (quarkus.oidc.*)
  - Messaging channels (mp.messaging.*)
- Delete `src/main/webapp/WEB-INF/web.xml` (if exists)
- Delete `src/main/webapp/WEB-INF/beans.xml` (if exists)
- Delete `src/main/webapp/keycloak.json`

### Phase 3: EJB to CDI
- ProductService: @Stateless → @ApplicationScoped
- CatalogService: @Stateless → @ApplicationScoped
- OrderService: @Stateless → @ApplicationScoped
- ShoppingCartOrderProcessor: @Stateless → @ApplicationScoped
- ShoppingCartService: @Stateful → @SessionScoped (verify session scope compatibility)
- ShippingService: Remove @Remote, add @ApplicationScoped
- ShoppingCartService.lookupShippingServiceRemote(): Remove JNDI lookup, use @Inject
- Delete ShippingServiceRemote.java interface

### Phase 4: Messaging
- OrderServiceMDB: Convert to @Incoming("orders") method
- InventoryNotificationMDB: **Decision required** - fix to @Incoming or remove
- ShoppingCartOrderProcessor: Replace JMS with @Channel Emitter<String>
- Configure in-memory channel in application.properties
- Remove javax.jms imports

### Phase 5: Lifecycle
- DataBaseMigrationStartup: Remove @PostConstruct Flyway code (handled by Quarkus Flyway extension)
- Add @Observes StartupEvent if custom startup logic needed
- Delete StartupListener.java

### Phase 6: Cleanup
- Delete `src/main/java/weblogic/` package
- Delete StartupListener.java
- Remove EntityManager producer (Resources.java) - Quarkus auto-injects
- Verify no `javax.*` EE imports remain (should be `jakarta.*` or removed)
- Move webapp files to META-INF/resources
- Delete JSP files

---

## Risk Assessment

### Low Risk
- ✅ JPA entities (no changes needed)
- ✅ JAX-RS endpoints (compatible with RESTEasy Reactive)
- ✅ Flyway migrations (same SQL scripts)
- ✅ Keycloak realm (no changes)
- ✅ PostgreSQL database (no changes)

### Medium Risk
- ⚠️ **@Stateful EJB** (ShoppingCartService): Need to verify session scope handling in Quarkus
- ⚠️ **Messaging**: In-memory connector appropriate for dev/single-instance; production may need Kafka
- ⚠️ **Frontend session**: Verify AngularJS doesn't rely on HTTP session beyond authentication
- ⚠️ **InventoryNotificationMDB**: Determine if used in production (broken WebLogic code)

### High Risk
- ❌ None identified

---

## Open Questions

The following questions require user input before/during implementation:

1. **InventoryNotificationMDB Status**
   - **Question**: Is InventoryNotificationMDB used in production, or is it dead code?
   - **Impact**: Determines whether to fix and migrate or simply delete
   - **Default Action**: Remove (appears non-functional with WebLogic JNDI)

2. **Production Deployment Model**
   - **Question**: How is the application deployed in production? Single instance, multiple replicas with shared broker, or Kubernetes?
   - **Impact**: Determines messaging connector (in-memory vs. Kafka/AMQP)
   - **Default Action**: Use in-memory connector, document how to switch to Kafka

3. **ShippingService Remote Interface**
   - **Question**: Is ShippingServiceRemote called by external applications, or only within this monolith?
   - **Impact**: Determines if REST API exposure is needed
   - **Default Action**: Convert to local CDI bean (JNDI lookup is within same app)

4. **AngularJS Session Dependencies**
   - **Question**: Does the AngularJS application rely on HTTP session state beyond Keycloak authentication?
   - **Impact**: Affects whether JSP session initialization can be removed cleanly
   - **Default Action**: Replace with token-based auth (Keycloak OIDC tokens)

---

## Success Criteria

### Functional Requirements
- ✅ All REST APIs return same responses
- ✅ Database schema and data unchanged
- ✅ Order processing workflow works (checkout → JMS → order save → inventory update)
- ✅ Keycloak authentication works
- ✅ AngularJS frontend loads and functions
- ✅ Flyway migrations run on startup

### Non-Functional Requirements
- ✅ Application starts in <2 seconds (Quarkus fast startup)
- ✅ Memory footprint <100MB (vs. ~500MB+ for JBoss/WildFly)
- ✅ Native image compilation possible (future optimization)
- ✅ Cloud-native: runs in containers without application server
- ✅ No proprietary dependencies (JBoss/WebLogic-specific code removed)

### Build Requirements
- ✅ `mvn clean package` produces executable JAR
- ✅ `java -jar target/quarkus-app/quarkus-run.jar` starts application
- ✅ Dev mode works: `mvn quarkus:dev`
- ✅ No compilation errors or warnings

---

## Rollback Plan

If migration fails or issues are discovered:

1. **Git revert**: All changes are in Git, revert to pre-migration commit
2. **WAR deployment**: Original WAR can be redeployed to JBoss/WildFly
3. **Database**: No schema changes; Flyway migrations are identical
4. **Keycloak**: No realm changes; same configuration works

**Recovery Time**: <1 hour (redeploy original WAR)

---

## Sign-off

**Prepared by**: Goose AI Migration Agent  
**Date**: 2026-08-15  
**Status**: READY FOR IMPLEMENTATION

**Approval Required From**:
- [ ] Application Owner (for business logic decisions)
- [ ] Operations Team (for deployment model confirmation)
- [ ] Development Team (for implementation execution)

---

## Appendices

### A. Dependency Mapping

| Java EE Dependency | Quarkus Extension |
|-------------------|-------------------|
| javaee-web-api:7.0 | (multiple, see below) |
| javaee-api:7.0 | (multiple, see below) |
| jboss-jms-api_2.0_spec | quarkus-smallrye-reactive-messaging |
| flyway-core:4.1.2 | quarkus-flyway |
| jboss-rmi-api_1.0_spec | (not needed) |
| (implicit) Keycloak adapter | quarkus-oidc |

**Quarkus Extensions to Add**:
- quarkus-resteasy-reactive-jackson (REST API)
- quarkus-hibernate-orm (JPA)
- quarkus-jdbc-postgresql (Database driver)
- quarkus-flyway (Database migrations)
- quarkus-oidc (Authentication)
- quarkus-smallrye-reactive-messaging (Messaging)
- quarkus-smallrye-health (Health checks)

### B. File Changes Summary

| Action | Count | Files |
|--------|-------|-------|
| Modify | 15 | pom.xml, all Service classes, REST endpoints, entities (imports) |
| Create | 1 | application.properties |
| Delete | 6+ | persistence.xml, weblogic package (3 files), StartupListener, Resources.java, keycloak.json |
| Move | 10+ | webapp/* → META-INF/resources/* |

### C. Testing Strategy

1. **Unit Tests**: Not present in current codebase; add if time permits
2. **Integration Tests**: Quarkus @QuarkusTest for REST endpoints
3. **Manual Testing**: 
   - Load frontend, verify authentication
   - Add products to cart
   - Checkout (triggers JMS message)
   - Verify order saved and inventory updated
4. **Performance Testing**: Compare startup time and memory (optional)

---

*End of Migration Specification*
