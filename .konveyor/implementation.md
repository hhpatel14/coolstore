# Implementation Plan
<!-- template-canary: IMPL-CANARY-2c9f5177 -->

## Goal
Migrate Java EE 7 coolstore-monolith from JBoss EAP 7.4 to Quarkus 3 as a standalone JAR application.
- Domain skill: javaee-to-quarkus

## Project Summary
- Type: Maven / Java EE 7 WAR monolith
- Files affected: 30 Java files, pom.xml, persistence.xml, keycloak.json, application.properties (new), 2 JSP files, web.xml/beans.xml (removal)
- Estimated complexity: Medium-High
- Hardest steps: 
  1. Converting InventoryNotificationMDB (WebLogic JNDI) to Quarkus messaging
  2. Replacing remote EJB JNDI lookup with CDI injection
  3. Migrating lifecycle listeners from WebLogic to Quarkus events

## Steps

### Step 1: Update POM - Change packaging to JAR
- Phase: Build Config
- File: pom.xml
- Action: MODIFY
- What to do: Change `<packaging>war</packaging>` to `<packaging>jar</packaging>`
- Why: Quarkus applications package as JAR, not WAR (no application server needed)
- Depends on: none
- Verify: `<packaging>jar</packaging>` is set in pom.xml

### Step 2: Update POM - Set Java 17
- Phase: Build Config
- File: pom.xml
- Action: MODIFY
- What to do: Update maven-compiler-plugin source and target from 1.8 to 17 (Quarkus 3 minimum is Java 17)
- Why: Quarkus 3 requires Java 17 as minimum JDK version
- Depends on: none
- Verify: `<source>17</source>` and `<target>17</target>` in compiler plugin configuration

### Step 3: Update POM - Add Quarkus BOM
- Phase: Build Config
- File: pom.xml
- Action: MODIFY
- What to do: Add `<dependencyManagement>` section with io.quarkus.platform:quarkus-bom:3.15.1 (or latest 3.x)
- Why: Quarkus BOM manages all Quarkus extension versions consistently
- Depends on: none
- Verify: quarkus-bom is in dependencyManagement section

### Step 4: Update POM - Replace Java EE dependencies with Quarkus extensions
- Phase: Build Config
- File: pom.xml
- Action: MODIFY
- What to do: Remove javaee-web-api, javaee-api, jboss-jms-api_2.0_spec, jboss-rmi-api_1.0_spec. Add quarkus-hibernate-orm, quarkus-jdbc-postgresql, quarkus-resteasy-reactive-jackson, quarkus-artemis-jms, quarkus-oidc, quarkus-flyway, quarkus-arc
- Why: Replace Java EE APIs with Quarkus extensions that provide same functionality
- Depends on: 3
- Verify: All quarkus-* dependencies added, old javax dependencies removed, build compiles

### Step 5: Update POM - Add Quarkus Maven plugin
- Phase: Build Config
- File: pom.xml
- Action: MODIFY
- What to do: Add io.quarkus:quarkus-maven-plugin with executions for build and generate-code
- Why: Quarkus plugin is required to build Quarkus applications
- Depends on: 3
- Verify: quarkus-maven-plugin present in build plugins section

### Step 6: Update POM - Remove maven-war-plugin
- Phase: Build Config
- File: pom.xml
- Action: MODIFY
- What to do: Remove maven-war-plugin (version 3.2.0) from build plugins
- Why: JAR packaging doesn't need WAR plugin
- Depends on: 1
- Verify: maven-war-plugin is not in pom.xml, build succeeds

### Step 7: Create application.properties
- Phase: App Config
- File: src/main/resources/application.properties
- Action: CREATE
- What to do: Create file with datasource config (quarkus.datasource.db-kind=postgresql, quarkus.datasource.username, quarkus.datasource.password, quarkus.datasource.jdbc.url), hibernate config (quarkus.hibernate-orm.database.generation=none, quarkus.hibernate-orm.log.sql=false), Flyway (quarkus.flyway.migrate-at-start=true, quarkus.flyway.locations=classpath:db/migration)
- Why: Replace persistence.xml JNDI datasource and server configuration with Quarkus properties
- Depends on: none
- Verify: application.properties created with datasource and hibernate settings

### Step 8: Migrate Keycloak configuration to application.properties
- Phase: App Config
- File: src/main/resources/application.properties
- Action: MODIFY
- What to do: Add OIDC properties from keycloak.json: quarkus.oidc.auth-server-url=http://localhost:8081/auth/realms/eap, quarkus.oidc.client-id=eap-app, quarkus.oidc.credentials.secret (if not public client)
- Why: Quarkus OIDC extension uses application.properties instead of keycloak.json
- Depends on: 7
- Verify: OIDC properties in application.properties match keycloak.json values

### Step 9: Configure Artemis JMS in application.properties
- Phase: App Config
- File: src/main/resources/application.properties
- Action: MODIFY
- What to do: Add quarkus.artemis.url=tcp://localhost:61616, quarkus.artemis.username, quarkus.artemis.password (configure for local Artemis broker, replacing JBoss integrated messaging)
- Why: Configure Artemis JMS broker connection for message-driven beans
- Depends on: 7
- Verify: Artemis configuration present in application.properties

### Step 10: Delete persistence.xml
- Phase: App Config
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Remove file (configuration moved to application.properties)
- Why: Quarkus configures JPA through application.properties, persistence.xml not needed
- Depends on: 7
- Verify: persistence.xml deleted, datasource configured in application.properties

### Step 11: Delete web.xml (if exists)
- Phase: App Config
- File: src/main/webapp/WEB-INF/web.xml
- Action: DELETE
- What to do: Remove file if present (JAX-RS application configured via @ApplicationPath annotation)
- Why: Quarkus doesn't use web.xml for REST endpoint configuration
- Depends on: none
- Verify: web.xml removed or confirmed not present

### Step 12: Delete beans.xml (if exists)
- Phase: App Config
- File: src/main/webapp/WEB-INF/beans.xml
- Action: DELETE
- What to do: Remove file if present (CDI enabled by default in Quarkus)
- Why: Quarkus has CDI enabled automatically, beans.xml not needed
- Depends on: none
- Verify: beans.xml removed or confirmed not present

### Step 13: Delete keycloak.json
- Phase: App Config
- File: src/main/webapp/keycloak.json
- Action: DELETE
- What to do: Remove file (configuration migrated to application.properties)
- Why: Configuration moved to application.properties in step 8
- Depends on: 8
- Verify: keycloak.json deleted, OIDC config in application.properties

### Step 14: Convert ShoppingCartService - Remove @Stateful
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do: Replace `@Stateful` with `@ApplicationScoped`, update imports from javax.ejb to jakarta.enterprise.context
- Why: Quarkus uses CDI beans instead of EJBs; @ApplicationScoped provides singleton behavior
- Depends on: 4
- Verify: @ApplicationScoped annotation present, no @Stateful, imports use jakarta.*

### Step 15: COMPLEX: Convert ShoppingCartService - Remove JNDI lookup for ShippingService
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do: Delete lookupShippingServiceRemote() method. Add `@Inject ShippingService shippingService;` field. Replace all `lookupShippingServiceRemote().calculateShipping(sc)` calls with `shippingService.calculateShipping(sc)` and `lookupShippingServiceRemote().calculateShippingInsurance(sc)` with `shippingService.calculateShippingInsurance(sc)`. Remove Context, InitialContext, NamingException imports.
- Why: Both services are in same monolith; direct CDI injection is simpler and more efficient than remote EJB JNDI lookup
- Depends on: 14, 16
- Verify: No lookupShippingServiceRemote() method, ShippingService injected, JNDI imports removed

### Step 16: Convert ShippingService - Remove @Remote and @Stateless
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do: Replace `@Stateless` and `@Remote` with `@ApplicationScoped`. Remove implements ShippingServiceRemote. Update imports from javax.ejb to jakarta.enterprise.context.
- Why: Convert EJB to CDI bean; no remote interface needed for local injection
- Depends on: 4
- Verify: @ApplicationScoped present, @Stateless/@Remote removed, no interface implementation

### Step 17: Delete ShippingServiceRemote interface
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java
- Action: DELETE
- What to do: Remove file (remote interface no longer needed)
- Why: Direct CDI injection doesn't require remote interface
- Depends on: 16
- Verify: ShippingServiceRemote.java deleted

### Step 18: Convert CatalogService - Replace EJB with CDI
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do: If @Stateless present, replace with `@ApplicationScoped`. Update imports from javax.ejb to jakarta.enterprise.context.
- Why: Convert all EJBs to CDI beans
- Depends on: 4
- Verify: Uses @ApplicationScoped or appropriate CDI scope, no EJB annotations

### Step 19: Convert OrderService - Replace EJB with CDI
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do: If @Stateless present, replace with `@ApplicationScoped`. Update imports from javax.ejb to jakarta.enterprise.context.
- Why: Convert all EJBs to CDI beans
- Depends on: 4
- Verify: Uses @ApplicationScoped or appropriate CDI scope, no EJB annotations

### Step 20: Convert ProductService - Replace EJB with CDI
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do: If @Stateless present, replace with `@ApplicationScoped`. Update imports from javax.ejb to jakarta.enterprise.context.
- Why: Convert all EJBs to CDI beans
- Depends on: 4
- Verify: Uses @ApplicationScoped or appropriate CDI scope, no EJB annotations

### Step 21: Convert PromoService - Replace EJB with CDI
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/PromoService.java
- Action: MODIFY
- What to do: If @Stateless present, replace with `@ApplicationScoped`. Update imports from javax.ejb to jakarta.enterprise.context.
- Why: Convert all EJBs to CDI beans
- Depends on: 4
- Verify: Uses @ApplicationScoped or appropriate CDI scope, no EJB annotations

### Step 22: Update CartEndpoint - Replace @SessionScoped
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do: Update imports from javax.enterprise.context to jakarta.enterprise.context, javax.inject to jakarta.inject, javax.ws.rs to jakarta.ws.rs. Verify @SessionScoped, @Inject, @Path, @GET, @POST, @DELETE all use jakarta.* packages.
- Why: Quarkus 3 uses Jakarta EE namespace (jakarta.*) instead of Java EE (javax.*)
- Depends on: 4
- Verify: All imports use jakarta.* namespace, no javax.* imports remain

### Step 23: Update OrderEndpoint - Replace javax with jakarta
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do: Update imports from javax.inject to jakarta.inject, javax.ws.rs to jakarta.ws.rs
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: 4
- Verify: All imports use jakarta.* namespace

### Step 24: Update ProductEndpoint - Replace javax with jakarta
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do: Update imports from javax.inject to jakarta.inject, javax.ws.rs to jakarta.ws.rs
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: 4
- Verify: All imports use jakarta.* namespace

### Step 25: Update RestApplication - Replace javax with jakarta
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: MODIFY
- What to do: Update imports from javax.ws.rs to jakarta.ws.rs
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: 4
- Verify: All imports use jakarta.* namespace

### Step 26: Update all JPA entities - Replace javax.persistence with jakarta.persistence
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java
- Action: MODIFY
- What to do: Replace all javax.persistence imports with jakarta.persistence
- Why: Quarkus 3 uses Jakarta EE namespace for JPA
- Depends on: 4
- Verify: All @Entity, @Table, @Id, @Column etc use jakarta.persistence

### Step 27: Update InventoryEntity - Replace javax.persistence with jakarta.persistence
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/model/InventoryEntity.java
- Action: MODIFY
- What to do: Replace all javax.persistence imports with jakarta.persistence
- Why: Quarkus 3 uses Jakarta EE namespace for JPA
- Depends on: 4
- Verify: All JPA annotations use jakarta.persistence

### Step 28: Update Order - Replace javax.persistence with jakarta.persistence
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/model/Order.java
- Action: MODIFY
- What to do: Replace all javax.persistence imports with jakarta.persistence
- Why: Quarkus 3 uses Jakarta EE namespace for JPA
- Depends on: 4
- Verify: All JPA annotations use jakarta.persistence

### Step 29: Update OrderItem - Replace javax.persistence with jakarta.persistence
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/model/OrderItem.java
- Action: MODIFY
- What to do: Replace all javax.persistence imports with jakarta.persistence
- Why: Quarkus 3 uses Jakarta EE namespace for JPA
- Depends on: 4
- Verify: All JPA annotations use jakarta.persistence

### Step 30: Update Producers - Replace javax with jakarta
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/utils/Producers.java
- Action: MODIFY
- What to do: Replace javax.enterprise.inject with jakarta.enterprise.inject, javax.enterprise.inject.Produces with jakarta.enterprise.inject.Produces
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: 4
- Verify: All CDI imports use jakarta.* namespace

### Step 31: Update Resources - Replace javax with jakarta
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do: Replace javax.enterprise.inject with jakarta.enterprise.inject, javax.persistence with jakarta.persistence
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: 4
- Verify: All imports use jakarta.* namespace

### Step 32: COMPLEX: Convert OrderServiceMDB to Artemis JMS listener
- Phase: Messaging
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do: Keep @MessageDriven annotation but update imports from javax.ejb to jakarta.ejb, javax.jms to jakarta.jms, javax.inject to jakarta.inject. Update activation config property "destinationLookup" value from "topic/orders" to match Artemis configuration. Keep MessageListener interface and onMessage implementation.
- Why: Quarkus Artemis JMS extension supports JMS API with minimal changes; update to Jakarta namespace
- Depends on: 4, 9
- Verify: Uses jakarta.* imports, connects to Artemis broker, receives messages from topic

### Step 33: COMPLEX: Convert InventoryNotificationMDB to Artemis JMS listener
- Phase: Messaging
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do: Add @MessageDriven annotation with activation config (destinationLookup, destinationType=jakarta.jms.Topic, acknowledgeMode). Update imports from javax.jms to jakarta.jms, javax.inject to jakarta.inject. Delete init(), close(), getInitialContext() methods (WebLogic-specific JNDI). Delete JNDI_FACTORY, JMS_FACTORY, TOPIC constants and TopicConnection/TopicSession/TopicSubscriber fields. Keep only MessageListener interface and onMessage() implementation.
- Why: Remove WebLogic JNDI initialization, use standard JMS MDB pattern with Quarkus Artemis extension
- Depends on: 4, 9
- Verify: @MessageDriven annotation present, WebLogic JNDI code removed, onMessage() works with Artemis

### Step 34: Update ShoppingCartOrderProcessor - Replace javax with jakarta
- Phase: Messaging
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do: Replace javax.ejb with jakarta.ejb (if present), javax.inject with jakarta.inject, javax.jms with jakarta.jms
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: 4
- Verify: All imports use jakarta.* namespace

### Step 35: COMPLEX: Replace StartupListener with Quarkus lifecycle events
- Phase: Lifecycle
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do: Remove `extends ApplicationLifecycleListener`. Add `@ApplicationScoped` annotation. Replace postStart() with method annotated `void onStart(@Observes StartupEvent event)`. Replace preStop() with method annotated `void onStop(@Observes ShutdownEvent event)`. Update imports: remove weblogic.application.*, add jakarta.enterprise.context.ApplicationScoped, jakarta.enterprise.event.Observes, io.quarkus.runtime.StartupEvent, io.quarkus.runtime.ShutdownEvent. Keep @Inject Logger.
- Why: Replace WebLogic-specific lifecycle listener with Quarkus CDI event observers
- Depends on: 4
- Verify: Uses @Observes StartupEvent/ShutdownEvent, no WebLogic dependencies, logs on startup/shutdown

### Step 36: Update DataBaseMigrationStartup - Replace javax with jakarta
- Phase: Lifecycle
- File: src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- Action: MODIFY
- What to do: Replace javax.annotation with jakarta.annotation, javax.inject with jakarta.inject if present. Check for @PostConstruct and update to jakarta.annotation.PostConstruct.
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: 4
- Verify: All imports use jakarta.* namespace

### Step 37: Delete weblogic/application/ApplicationLifecycleEvent.java
- Phase: Cleanup
- File: src/main/java/weblogic/application/ApplicationLifecycleEvent.java
- Action: DELETE
- What to do: Remove file (WebLogic stub, no longer needed)
- Why: WebLogic lifecycle replaced with Quarkus events in step 35
- Depends on: 35
- Verify: File deleted, no references to ApplicationLifecycleEvent remain

### Step 38: Delete weblogic/application/ApplicationLifecycleListener.java
- Phase: Cleanup
- File: src/main/java/weblogic/application/ApplicationLifecycleListener.java
- Action: DELETE
- What to do: Remove file (WebLogic stub, no longer needed)
- Why: WebLogic lifecycle replaced with Quarkus events in step 35
- Depends on: 35
- Verify: File deleted, no references to ApplicationLifecycleListener remain

### Step 39: Delete weblogic/i18n/logging/NonCatalogLogger.java
- Phase: Cleanup
- File: src/main/java/weblogic/i18n/logging/NonCatalogLogger.java
- Action: DELETE
- What to do: Remove file (WebLogic stub, unused)
- Why: WebLogic-specific logging not needed in Quarkus
- Depends on: none
- Verify: File deleted, verify no code references this class

### Step 40: Verify no javax.* EE imports remain
- Phase: Cleanup
- File: src/main/java (all files)
- Action: MODIFY
- What to do: Search all Java files for remaining javax.ejb, javax.persistence, javax.inject, javax.ws.rs, javax.jms, javax.enterprise imports and replace with jakarta.* equivalents. Exceptions: javax.naming (if needed for compatibility), java.util.logging (JDK class, not EE).
- Why: Ensure complete migration to Jakarta EE namespace
- Depends on: 14-36
- Verify: `grep -r "import javax\\.(ejb|persistence|inject|ws\\.rs|jms|enterprise)" src/main/java` returns no results

### Step 41: Move static web content for direct serving
- Phase: Cleanup
- File: src/main/webapp → src/main/resources/META-INF/resources
- Action: MODIFY
- What to do: Move index.jsp, health.jsp, app/, bower_components/, partials/, coolstore.json from src/main/webapp/ to src/main/resources/META-INF/resources/. Optionally convert index.jsp to index.html (remove JSP tags, keep HTML/JS).
- Why: Quarkus serves static content from META-INF/resources; JSP can be converted to static HTML since it only bootstraps AngularJS SPA
- Depends on: none
- Verify: Static files in META-INF/resources/, accessible at runtime

### Step 42: Delete empty webapp directory
- Phase: Cleanup
- File: src/main/webapp
- Action: DELETE
- What to do: Remove src/main/webapp directory if empty after moving static content
- Why: JAR packaging doesn't use webapp directory
- Depends on: 41
- Verify: src/main/webapp deleted or contains only necessary files

## Verification
- Build: `mvn clean package` (should produce target/monolith-1.0.0-SNAPSHOT-runner.jar)
- Test: `mvn test` (currently maven.test.skip=true, so tests skipped - remove this property if tests exist)
- Blackbox: 
  1. Start PostgreSQL: Ensure PostgreSQL is running on localhost:5432 with database 'coolstore'
  2. Start Artemis: Ensure ActiveMQ Artemis is running on localhost:61616
  3. Start Keycloak: Ensure Keycloak is running on localhost:8081 with realm 'eap'
  4. Run application: `java -jar target/monolith-1.0.0-SNAPSHOT-runner.jar` or `mvn quarkus:dev`
  5. Verify REST endpoints: 
     - GET http://localhost:8080/services/products (should return product list)
     - GET http://localhost:8080/services/cart/{cartId} (should return empty cart)
  6. Verify messaging: POST order to checkout endpoint, check logs for MDB message processing
  7. Verify static content: Access http://localhost:8080/ (should serve AngularJS SPA)
  8. Verify Keycloak integration: Attempt authenticated operation, verify OIDC flow

## Notes
- **Java 17 Required**: Quarkus 3 minimum is Java 17. Ensure JAVA_HOME points to JDK 17+
- **Database Migration**: Flyway migrations (V1_1, V1_2) should run automatically at startup with quarkus.flyway.migrate-at-start=true
- **Session State**: Original app uses @Stateful EJB for ShoppingCartService with session-scoped cart. Migrated version uses @ApplicationScoped singleton with cart parameter. Consider adding session management if multi-user sessions needed.
- **Artemis vs ActiveMQ**: JBoss EAP uses integrated ActiveMQ. For Quarkus, use external ActiveMQ Artemis broker. Configure connection in application.properties.
- **Native Compilation**: After migration, can build native executable with `mvn package -Pnative` for faster startup and lower memory usage
- **InventoryNotificationMDB**: This MDB lacks @MessageDriven annotation in original code and has WebLogic JNDI init code. Likely non-functional in current JBoss deployment. Migration makes it functional with proper @MessageDriven configuration.
- **Remote EJB Pattern**: Original code uses remote EJB lookup for local call (ShoppingCartService → ShippingService). This is inefficient. CDI injection is recommended for same-JVM calls.
- **JSP Deprecation**: JSP files only bootstrap AngularJS SPA. Can be converted to static HTML for better performance and simpler deployment.
