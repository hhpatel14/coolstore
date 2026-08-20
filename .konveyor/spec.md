# Migration Spec

## Goal
Migrate a Java EE 7 monolithic application from JBoss/WildFly to Quarkus 3.2 LTS

## Source → Target
Java EE 7 (JBoss EAP/WildFly) → Quarkus 3.2 LTS

## Scope
- Files affected: 34 (30 Java source files, 1 pom.xml, 3 XML config files)
- Estimated complexity: High
- Hardest areas: 
  1. InventoryNotificationMDB - WebLogic-specific JNDI with manual JMS connection setup
  2. ShoppingCartService - Remote EJB JNDI lookup pattern needs conversion to local CDI injection
  3. Message-driven beans - Two MDBs (OrderServiceMDB, InventoryNotificationMDB) require conversion to SmallRye Reactive Messaging

## Key Decisions Applied

1. **Source Application Server (Decision #1)**
   - Chosen: B) JBoss EAP/WildFly
   - Reasoning: The persistence.xml references 'java:jboss/datasources/CoolstoreDS' and ShoppingCartService uses 'org.wildfly.naming.client.WildFlyInitialContextFactory'. The WebLogic classes are locally vendored stubs in src/main/java, not external dependencies.

2. **View Layer Strategy (Decision #2)**
   - Chosen: C) Convert to API-only backend
   - Reasoning: The JSP files are minimal (index.jsp primarily loads AngularJS). The app already has a clean REST API layer (CartEndpoint, ProductEndpoint, OrderEndpoint). Converting to pure API backend aligns with Quarkus cloud-native philosophy and eliminates JSP support complexity.

3. **Authentication Strategy (Decision #3)**
   - Chosen: A) Use quarkus-oidc extension
   - Reasoning: Quarkus 3 uses quarkus-oidc as the recommended approach for Keycloak integration. The existing keycloak.json configuration will be migrated to application.properties with OIDC settings, maintaining Keycloak as the identity provider.

4. **Database and Persistence (Decision #4)**
   - Chosen: D) Ask application owner for production database details
   - Reasoning: The code shows no explicit database type - only JNDI datasource reference and Flyway migrations with generic SQL. Without knowing the production database, we risk incompatibility. Implementation will provide H2 for dev/test and PostgreSQL configuration template for production.

5. **Messaging Strategy (Decision #5)**
   - Chosen: B) Use quarkus-artemis-jms
   - Reasoning: The app uses JMS topics with MDBs, suggesting JBoss/WildFly environment likely using integrated HornetQ/Artemis. Using quarkus-artemis-jms maintains JMS API compatibility while moving to Quarkus, with minimal code changes. The @MessageDriven annotations will be replaced with @Incoming from SmallRye Reactive Messaging with Artemis JMS configuration.

6. **Remote EJB Pattern (Decision #6)**
   - Chosen: A) Convert to local CDI injection
   - Reasoning: ShippingService is defined in the same codebase as ShoppingCartService. The remote EJB lookup is over-engineering for a local service call. Converting to simple CDI @Inject eliminates JNDI complexity, improves performance, and aligns with Quarkus dependency injection model.

7. **Quarkus Version (Decision #7)**
   - Chosen: B) Quarkus 3.2.x LTS
   - Reasoning: For enterprise migrations, Quarkus 3.2.x LTS provides long-term support (minimum 12 months maintenance), stability for production deployments, and sufficient time for testing. This reduces risk of compatibility issues.

## Approach

The migration follows six phases from the javaee-to-quarkus domain skill:

1. **Build Config** - Transform pom.xml from WAR to JAR packaging, add Quarkus BOM and plugin, replace Java EE dependencies with Quarkus extensions (quarkus-arc, quarkus-rest-jackson, quarkus-hibernate-orm, quarkus-jdbc-h2, quarkus-jdbc-postgresql, quarkus-flyway, quarkus-smallrye-reactive-messaging-amqp, quarkus-oidc)

2. **App Config** - Replace persistence.xml with application.properties datasource configuration, delete web.xml and beans.xml, add messaging channel configuration for MDBs

3. **EJB to CDI** - Replace @Stateless/@Stateful with @ApplicationScoped, replace @EJB with @Inject, update all javax.* imports to jakarta.*, remove @Remote interface (ShippingServiceRemote), eliminate JNDI lookups in ShoppingCartService

4. **Messaging** - Convert OrderServiceMDB and InventoryNotificationMDB from @MessageDriven to @Incoming reactive messaging pattern, replace manual JMS connection code in InventoryNotificationMDB with declarative configuration

5. **Lifecycle** - Convert StartupListener from WebLogic ApplicationLifecycleListener to Quarkus @Observes StartupEvent/ShutdownEvent pattern, migrate DataBaseMigrationStartup to use Quarkus Flyway auto-configuration

6. **Cleanup** - Delete src/main/java/weblogic/ stub directory (3 files), delete persistence.xml, web.xml, beans.xml, verify no javax.ejb/javax.jms imports remain

## Domain Skill
javaee-to-quarkus - Migrates Java EE 7/8 applications (WebLogic, JBoss, WildFly) to Quarkus 3. Replaces EJB with CDI, JMS/MDB with SmallRye Reactive Messaging, WAR with JAR packaging, and eliminates application server dependencies.
