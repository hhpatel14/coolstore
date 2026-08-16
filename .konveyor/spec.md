# Migration Spec
<!-- template-canary: SPEC-CANARY-4b1e8d55 -->

## Goal
Migrate a Java EE 7 monolith application from JBoss EAP 7.4 to Quarkus 3 as a standalone application.

## Source → Target
Java EE 7 (JBoss EAP 7.4) → Quarkus 3

## Scope
- Files affected: 30 Java files, 1 pom.xml, 1 persistence.xml, keycloak.json, 2 JSP files
- Estimated complexity: Medium-High
- Hardest areas: 
  1. JMS Message-Driven Beans (especially InventoryNotificationMDB with WebLogic JNDI)
  2. Remote EJB JNDI lookups (ShoppingCartService → ShippingService)
  3. WebLogic lifecycle listeners and stub classes

## Key Decisions Applied

1. **WebLogic stub classes** (Decision #1)
   - Chosen: D - Investigate usage before deciding
   - Reasoning: Stub implementations found in src/main/java/weblogic/* may be unused compatibility shims from previous migration. StartupListener extends ApplicationLifecycleListener, and InventoryNotificationMDB references weblogic.jndi.WLInitialContextFactory. Need to verify usage before removal.

2. **View layer strategy** (Decision #2)
   - Chosen: B - Convert to pure REST API-only backend, serve static files directly
   - Reasoning: Application is already an AngularJS SPA with minimal JSP files that only bootstrap the client-side app. Eliminate JSP dependency and serve static HTML/JS/CSS files directly through Quarkus.

3. **Authentication** (Decision #3)
   - Chosen: A - Use Quarkus OIDC extension with Keycloak adapter
   - Reasoning: Migrate keycloak.json configuration to application.properties using quarkus.oidc.* properties. Quarkus has native support for Keycloak with better performance and native compilation compatibility.

4. **Messaging approach** (Decision #4)
   - Chosen: B - Use Quarkus Artemis JMS extension to preserve JMS API compatibility
   - Reasoning: Two MDBs consume from 'topic/orders' in publish-subscribe pattern. Artemis JMS is the successor to ActiveMQ/JBoss messaging and provides smoothest migration with minimal code changes.

5. **Remote EJB calls** (Decision #5)
   - Chosen: A - Replace with direct CDI injection
   - Reasoning: ShippingService and ShoppingCartService are in the same monolith deployment. JNDI remote lookup is unnecessary overhead. Replace with direct @Inject for efficiency.

6. **Database configuration** (Decision #6)
   - Chosen: A - Use Quarkus datasource configuration in application.properties
   - Reasoning: Replace JNDI datasource (java:jboss/datasources/CoolstoreDS) with quarkus.datasource.* properties. Flyway migrations supported via quarkus-flyway extension.

7. **Migration scope** (Decision #7)
   - Chosen: A - Migrate entire monolith to Quarkus as single application
   - Reasoning: Application is designed as coolstore-monolith with single WAR packaging. Preserve architecture while gaining Quarkus benefits. Microservices decomposition can be a separate future decision.

## Approach

Following the javaee-to-quarkus migration phases:

1. **Build Config** — Convert WAR to JAR packaging, add Quarkus BOM and plugin (io.quarkus:quarkus-maven-plugin), replace Java EE dependencies with Quarkus extensions (quarkus-hibernate-orm-panache, quarkus-resteasy-reactive-jackson, quarkus-artemis-jms, quarkus-oidc, quarkus-flyway, quarkus-jdbc-postgresql).

2. **App Config** — Migrate persistence.xml JNDI datasource to application.properties (quarkus.datasource.*, quarkus.hibernate-orm.*). Convert keycloak.json to quarkus.oidc.* properties. Remove web.xml and beans.xml (not needed in Quarkus).

3. **EJB to CDI** — Replace @Stateful with @ApplicationScoped, remove @Remote and ShippingServiceRemote interface, replace JNDI lookups (lookupShippingServiceRemote()) with direct @Inject. Convert all EJB annotations to CDI equivalents.

4. **Messaging** — Convert OrderServiceMDB and InventoryNotificationMDB from @MessageDriven to @Incoming methods using SmallRye Reactive Messaging (or keep JMS API with Artemis extension per decision #4). Remove WebLogic JNDI initialization code from InventoryNotificationMDB.

5. **Lifecycle** — Replace StartupListener (extends weblogic.application.ApplicationLifecycleListener) with Quarkus @Observes StartupEvent and ShutdownEvent. Remove WebLogic stub classes.

6. **Cleanup** — Delete weblogic/* stub classes, remove persistence.xml (replaced by application.properties), delete web.xml and beans.xml if present, verify no javax.* imports remain (should be jakarta.*).

## Domain Skill
javaee-to-quarkus - Migrates Java EE 7/8 applications (WebLogic, JBoss, WildFly) to Quarkus 3. Replaces Java EE programming model with Quarkus: EJB → CDI, JMS/MDB → SmallRye Reactive Messaging or Artemis JMS, WAR → JAR, persistence.xml → application.properties, JNDI → direct injection, lifecycle hooks → Quarkus events.
