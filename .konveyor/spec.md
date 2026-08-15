# Migration Spec
<!-- template-canary: SPEC-CANARY-4b1e8d55 -->

## Goal
Migrate a Java EE 7 monolith application from JBoss EAP 7.4 to Quarkus 3.2 LTS.

## Source → Target
Java EE 7 / JBoss EAP 7.4 → Quarkus 3.2 LTS

## Scope
- Files affected: 30 Java source files + 4 config files + pom.xml = 35 files
- Estimated complexity: High
- Hardest areas:
  1. JMS messaging migration (2 MDBs with WebLogic JNDI code) to SmallRye Reactive Messaging with Artemis
  2. EJB removal (@Stateful, @MessageDriven) and replacement with CDI + Quarkus patterns
  3. JNDI lookups (ShippingService remote EJB, JMS resources) to direct injection

## Key Decisions Applied

### Decision 1: Quarkus Version
- **Chosen**: A) Quarkus 3.2 LTS - Long-term support release with stability guarantees
- **Reasoning**: Quarkus 3.2 LTS is recommended for enterprise migrations from Java EE/JBoss EAP. It provides long-term support with backported security fixes, making it the safest choice for production workloads. The application shows production characteristics (clustering, JMS, Keycloak integration) that benefit from LTS stability.

### Decision 2: WebLogic Stub Classes
- **Chosen**: C) Investigate first - search for any references in the codebase before deciding on removal or replacement
- **Reasoning**: The weblogic.* classes are stub implementations (empty methods, Apache license headers), suggesting they were created to satisfy compilation dependencies. InventoryNotificationMDB references weblogic.jndi.WLInitialContextFactory and uses PortableRemoteObject, indicating WebLogic-specific JMS initialization code exists. Must search for all references before deciding whether to remove or replace these stubs and the code that depends on them.

### Decision 3: View Layer Strategy
- **Chosen**: B) Convert to API-only - Remove JSP files, serve static AngularJS files directly, app becomes pure REST backend
- **Reasoning**: The JSP files are minimal wrappers around an AngularJS SPA (index.jsp loads bower components and Angular app files, then renders <div ng-view></div>). The only server-side logic is request.getSession(true). The frontend is already a client-side SPA that communicates with REST endpoints (/services/products, /services/cart). Converting to API-only means serving the Angular app as static files and removing JSP, which aligns with Quarkus best practices and cloud-native architecture.

### Decision 4: Keycloak Authentication
- **Chosen**: A) Quarkus OIDC - Use quarkus-oidc extension with the existing Keycloak server configuration
- **Reasoning**: The application already uses Keycloak with OIDC flow (keycloak.json shows public-client: true, typical for SPAs). Quarkus OIDC (quarkus-oidc) is the direct replacement for Keycloak adapter, supporting the same realm/client configuration. The existing keycloak.json can be translated to application.properties with minimal changes. This maintains the existing security architecture while using Quarkus-native extensions.

### Decision 5: Database Configuration
- **Chosen**: A) PostgreSQL with Hibernate ORM - Use quarkus-hibernate-orm and quarkus-jdbc-postgresql, configure PostgreSQL datasource
- **Reasoning**: The application uses standard JPA/Hibernate (CriteriaBuilder queries, EntityManager, @Entity/@OneToOne mappings) without reactive patterns. The README documents explicit PostgreSQL setup instructions with JDBC driver configuration. Migration should use quarkus-hibernate-orm with quarkus-jdbc-postgresql to maintain compatibility. The Flyway migrations (V1_1__CreateSchema.sql, V1_2__AddInitialData.sql) indicate schema management is already in place and should continue with quarkus-flyway.

### Decision 6: Messaging Strategy
- **Chosen**: C) Quarkus Artemis JMS - Use quarkus-artemis-jms to maintain JMS API compatibility with ActiveMQ Artemis (JBoss successor)
- **Reasoning**: The application uses JMS Topics with MDB pattern (OrderServiceMDB, InventoryNotificationMDB both consume topic/orders). The README shows ActiveMQ configuration (messaging-activemq subsystem, cluster-password) and demonstrates clustering with two JBoss instances processing messages. InventoryNotificationMDB contains WebLogic-specific JMS code that needs replacement. Quarkus Artemis JMS (quarkus-artemis-jms) provides JMS API compatibility while enabling migration of MDB to @Incoming annotations incrementally, and Artemis is the natural successor to ActiveMQ in the Red Hat ecosystem.

### Decision 7: Migration Scope
- **Chosen**: A) Migrate entire monolith to Quarkus - Keep single-module structure, convert all Java EE APIs to Quarkus extensions
- **Reasoning**: The application is already a cohesive monolith with clear layering (model, service, rest) and only 23 source files. The services are tightly coupled (OrderServiceMDB updates inventory via CatalogService, components share EntityManager). The migration prompt is 'migrate Java EE application to Quarkus 3' not 'decompose to microservices'. Migrate the entire application to Quarkus as a single module first, maintaining the monolith architecture. This reduces migration risk, preserves transactions/consistency, and allows later decomposition if needed based on operational experience with the Quarkus version.

## Approach

The migration follows the javaee-to-quarkus domain skill's 6-phase approach:

### Phase 1: Build Config
- Replace WAR packaging with JAR
- Add Quarkus BOM (3.2.12.Final - latest 3.2 LTS patch)
- Add quarkus-maven-plugin
- Replace Java EE dependencies with Quarkus extensions:
  - quarkus-resteasy-jackson (JAX-RS)
  - quarkus-hibernate-orm (JPA)
  - quarkus-jdbc-postgresql (datasource)
  - quarkus-flyway (schema migrations)
  - quarkus-artemis-jms (messaging)
  - quarkus-oidc (Keycloak auth)
  - quarkus-undertow (static files for Angular SPA)
- Update Java version from 1.8 to 17 (Quarkus 3 minimum)

### Phase 2: App Config
- Create application.properties with all runtime configuration
- Migrate persistence.xml settings (datasource, Hibernate properties)
- Migrate keycloak.json to OIDC properties
- Configure Artemis broker connection
- Configure Flyway migration paths
- Delete persistence.xml, web.xml, beans.xml, jboss-web.xml

### Phase 3: EJB to CDI
- Remove @Stateful from ShoppingCartService → convert to @ApplicationScoped
- Remove @MessageDriven from OrderServiceMDB and InventoryNotificationMDB
- Replace ShippingServiceRemote JNDI lookup with direct @Inject
- Remove Remote/Local interfaces (ShippingServiceRemote)
- Remove all JNDI lookups and InitialContext usage
- Replace javax.ejb.* imports with jakarta.enterprise.context.*

### Phase 4: Messaging
- Convert OrderServiceMDB to SmallRye Reactive Messaging @Incoming method
- Convert InventoryNotificationMDB to @Incoming method
- Remove WebLogic JNDI code (WLInitialContextFactory, PortableRemoteObject)
- Replace JMS producer code in ShoppingCartOrderProcessor with @Channel Emitter
- Remove MessageListener implementations
- Configure Artemis connection in application.properties

### Phase 5: Lifecycle
- Replace StartupListener (extends ApplicationLifecycleListener) with Quarkus lifecycle events
- Use @Observes StartupEvent and ShutdownEvent
- Remove weblogic.application.* imports and stub classes
- Delete weblogic/* stub files after migration

### Phase 6: Cleanup
- Delete src/main/webapp/WEB-INF/web.xml
- Delete src/main/webapp/WEB-INF/beans.xml
- Delete src/main/webapp/WEB-INF/jboss-web.xml
- Delete src/main/resources/META-INF/persistence.xml
- Delete src/main/webapp/*.jsp files
- Delete weblogic/* stub classes
- Verify no javax.* EE imports remain (all should be jakarta.*)
- Move webapp static files to src/main/resources/META-INF/resources/

## Domain Skill
**javaee-to-quarkus** - Migrates Java EE 7/8 applications (WebLogic, JBoss, WildFly) to Quarkus 3. This migration replaces the Java EE programming model with Quarkus: EJB → CDI managed beans, JMS/MDB → SmallRye Reactive Messaging, WAR → JAR packaging, persistence.xml → application.properties, JNDI lookups → direct injection, and app server lifecycle hooks → Quarkus lifecycle events.
