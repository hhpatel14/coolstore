# Migration Plan: Java EE 7 → Quarkus 3

## Goal
Migrate this Java EE 7 / WebLogic application to Quarkus 3, including MDB conversions, lifecycle listeners, and WebLogic-specific code removal.

- **Reference used**: javaee-quarkus.md

## Project Summary
- Type: Maven WAR application (Java EE 7)
- Files affected: 30 Java source files
- Estimated complexity: **High**
- Hardest steps:
  1. Convert 2 MDB classes to SmallRye Reactive Messaging
  2. Convert WebLogic ApplicationLifecycleListener to Quarkus events
  3. Remove WebLogic stub classes and JNDI lookups

## Steps

### Step 1: Update pom.xml for Quarkus
- File: pom.xml
- Action: MODIFY
- What to do:
  - Change `<packaging>war</packaging>` → `<packaging>jar</packaging>`
  - Remove `javaee-web-api` and `javaee-api` dependencies
  - Remove `maven-war-plugin`
  - Add Quarkus BOM in `<dependencyManagement>`: `io.quarkus.platform:quarkus-bom:3.8.4`
  - Add Quarkus extensions:
    - `quarkus-arc` (CDI)
    - `quarkus-rest-jackson` (JAX-RS + JSON)
    - `quarkus-hibernate-orm` (JPA)
    - `quarkus-jdbc-postgresql` (PostgreSQL)
    - `quarkus-jdbc-h2` (H2 for dev/test)
    - `quarkus-flyway` (DB migrations)
    - `quarkus-smallrye-reactive-messaging-amqp` (MDB replacement)
  - Add Quarkus Maven plugin: `io.quarkus.platform:quarkus-maven-plugin:3.8.4`
  - Keep Flyway dependency version or use Quarkus-managed version
- Why: Transform from WAR to Quarkus JAR, add required Quarkus dependencies
- Depends on: none
- Verify: `mvn clean compile` succeeds

### Step 2: Create application.properties
- File: src/main/resources/application.properties
- Action: CREATE
- What to do:
  - Add datasource configuration:
    ```properties
    quarkus.datasource.db-kind=postgresql
    quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/coolstore
    quarkus.datasource.username=${DB_USER:coolstore}
    quarkus.datasource.password=${DB_PASS:coolstore}
    quarkus.hibernate-orm.database.generation=none
    quarkus.flyway.migrate-at-start=true
    quarkus.flyway.locations=classpath:db/migration
    ```
  - Add messaging configuration for MDB channels (will refine after reading MDB files):
    ```properties
    # Order processing channel
    mp.messaging.incoming.orders.connector=smallrye-amqp
    mp.messaging.incoming.orders.address=orders
    %dev.mp.messaging.incoming.orders.connector=smallrye-in-memory
    
    # Inventory notification channel
    mp.messaging.incoming.inventory.connector=smallrye-amqp
    mp.messaging.incoming.inventory.address=inventory
    %dev.mp.messaging.incoming.inventory.connector=smallrye-in-memory
    ```
- Why: Replace persistence.xml and configure messaging
- Depends on: Step 1
- Verify: File exists with valid properties syntax

### Step 3: Migrate Resources.java (CDI producer)
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do:
  - Replace `javax.enterprise.*` → `jakarta.enterprise.*`
  - Replace `javax.persistence.*` → `jakarta.persistence.*`
  - Keep `@Produces` annotations as-is (Jakarta CDI still uses them)
- Why: Update to Jakarta EE namespace
- Depends on: Step 1
- Verify: No `javax.` imports remain

### Step 4: Migrate CatalogItemEntity.java
- File: src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java
- Action: MODIFY
- What to do:
  - Replace `javax.persistence.*` → `jakarta.persistence.*`
  - Keep `implements Serializable` as-is
- Why: Update JPA entity to Jakarta namespace
- Depends on: Step 1
- Verify: No `javax.` imports remain

### Step 5: Migrate InventoryEntity.java
- File: src/main/java/com/redhat/coolstore/model/InventoryEntity.java
- Action: MODIFY
- What to do:
  - Replace `javax.persistence.*` → `jakarta.persistence.*`
  - Keep `implements Serializable` as-is
- Why: Update JPA entity to Jakarta namespace
- Depends on: Step 1
- Verify: No `javax.` imports remain

### Step 6: Migrate Order.java
- File: src/main/java/com/redhat/coolstore/model/Order.java
- Action: MODIFY
- What to do:
  - Replace `javax.persistence.*` → `jakarta.persistence.*`
  - Keep `implements Serializable` as-is
- Why: Update JPA entity to Jakarta namespace
- Depends on: Step 1
- Verify: No `javax.` imports remain

### Step 7: Migrate OrderItem.java
- File: src/main/java/com/redhat/coolstore/model/OrderItem.java
- Action: MODIFY
- What to do:
  - Replace `javax.persistence.*` → `jakarta.persistence.*`
  - Keep `implements Serializable` as-is
- Why: Update JPA entity to Jakarta namespace
- Depends on: Step 1
- Verify: No `javax.` imports remain

### Step 8: Migrate Product.java
- File: src/main/java/com/redhat/coolstore/model/Product.java
- Action: MODIFY
- What to do:
  - Replace `javax.persistence.*` → `jakarta.persistence.*`
  - Keep `implements Serializable` as-is
- Why: Update JPA entity to Jakarta namespace
- Depends on: Step 1
- Verify: No `javax.` imports remain

### Step 9: Migrate Promotion.java
- File: src/main/java/com/redhat/coolstore/model/Promotion.java
- Action: MODIFY
- What to do:
  - Replace `javax.persistence.*` → `jakarta.persistence.*`
  - Keep `implements Serializable` as-is
- Why: Update JPA entity to Jakarta namespace
- Depends on: Step 1
- Verify: No `javax.` imports remain

### Step 10: Migrate ShoppingCart.java
- File: src/main/java/com/redhat/coolstore/model/ShoppingCart.java
- Action: MODIFY
- What to do:
  - Replace `javax.persistence.*` → `jakarta.persistence.*`
  - Keep `implements Serializable` as-is
- Why: Update JPA entity to Jakarta namespace
- Depends on: Step 1
- Verify: No `javax.` imports remain

### Step 11: Migrate ShoppingCartItem.java
- File: src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java
- Action: MODIFY
- What to do:
  - Replace `javax.persistence.*` → `jakarta.persistence.*`
  - Keep `implements Serializable` as-is
- Why: Update JPA entity to Jakarta namespace
- Depends on: Step 1
- Verify: No `javax.` imports remain

### Step 12: Migrate Transformers.java
- File: src/main/java/com/redhat/coolstore/utils/Transformers.java
- Action: MODIFY
- What to do:
  - Replace `javax.enterprise.*` → `jakarta.enterprise.*`
  - Replace any `javax.*` imports → `jakarta.*`
- Why: Update utilities to Jakarta namespace
- Depends on: Step 1
- Verify: No `javax.` imports remain

### Step 13: Migrate Producers.java
- File: src/main/java/com/redhat/coolstore/utils/Producers.java
- Action: MODIFY
- What to do:
  - Replace `javax.enterprise.*` → `jakarta.enterprise.*`
  - Replace any `javax.*` imports → `jakarta.*`
- Why: Update CDI producers to Jakarta namespace
- Depends on: Step 1
- Verify: No `javax.` imports remain

### Step 14: Delete DataBaseMigrationStartup.java
- File: src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- Action: DELETE
- What to do: Remove entire file
- Why: This class manually runs Flyway using `@PostConstruct`. Quarkus Flyway extension auto-runs migrations at startup (configured in Step 2). Keeping this would cause duplicate migration attempts and potential errors.
- Depends on: Step 1, Step 2 (application.properties with `quarkus.flyway.migrate-at-start=true`)
- Verify: File deleted, Flyway still runs automatically via Quarkus extension

### Step 15: ⚠️ COMPLEX — Migrate StartupListener.java (WebLogic lifecycle listener)
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do:
  - Remove `extends weblogic.application.ApplicationLifecycleListener`
  - Remove all `weblogic.*` imports
  - Add `jakarta.enterprise.context.ApplicationScoped`
  - Add `jakarta.enterprise.event.Observes`
  - Add `io.quarkus.runtime.StartupEvent` and `io.quarkus.runtime.ShutdownEvent`
  - Transform lifecycle methods:
    - `postStart(ApplicationLifecycleEvent evt)` → `onStart(@Observes StartupEvent ev)`
    - `preStop(ApplicationLifecycleEvent evt)` → `onStop(@Observes ShutdownEvent ev)`
  - Keep business logic inside methods, just change signatures
- Why: WebLogic-specific lifecycle listener not supported in Quarkus
- Depends on: Step 1
- Verify: No `weblogic.*` imports, class compiles, startup logic runs

### Step 16: Migrate CatalogService.java
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do:
  - Replace `javax.ejb.Stateless` → `jakarta.enterprise.context.ApplicationScoped`
  - Replace `javax.inject.*` → `jakarta.inject.*`
  - Replace `javax.persistence.*` → `jakarta.persistence.*`
  - Replace `@EJB` → `@Inject`
- Why: Convert EJB to CDI bean
- Depends on: Steps 4-11 (entities), Step 3 (Resources)
- Verify: No `javax.ejb` imports remain

### Step 17: Migrate ProductService.java
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do:
  - Replace `javax.ejb.Stateless` → `jakarta.enterprise.context.ApplicationScoped`
  - Replace `javax.inject.*` → `jakarta.inject.*`
  - Replace `@EJB` → `@Inject`
- Why: Convert EJB to CDI bean
- Depends on: Step 16 (CatalogService)
- Verify: No `javax.ejb` imports remain

### Step 18: Migrate PromoService.java
- File: src/main/java/com/redhat/coolstore/service/PromoService.java
- Action: MODIFY
- What to do:
  - Replace `javax.ejb.Stateless` → `jakarta.enterprise.context.ApplicationScoped`
  - Replace `javax.inject.*` → `jakarta.inject.*`
  - Replace `@EJB` → `@Inject`
- Why: Convert EJB to CDI bean
- Depends on: Step 1
- Verify: No `javax.ejb` imports remain

### Step 19: Migrate ShippingService.java
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do:
  - Replace `javax.ejb.Stateless` → `jakarta.enterprise.context.ApplicationScoped`
  - Replace `javax.inject.*` → `jakarta.inject.*`
  - Replace `@EJB` → `@Inject`
  - If this uses `ShippingServiceRemote`, handle JNDI lookup removal
- Why: Convert EJB to CDI bean
- Depends on: Step 1
- Verify: No `javax.ejb` imports remain

### Step 20: Migrate ShippingServiceRemote.java (interface)
- File: src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java
- Action: MODIFY
- What to do:
  - Remove `@Remote` annotation
  - Remove `javax.ejb.*` imports
  - Keep as plain Java interface (or remove entirely if only used for remote lookup)
- Why: Remote EJB interfaces not needed in Quarkus
- Depends on: Step 1
- Verify: Interface remains valid or is removed

### Step 21: Migrate OrderService.java
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do:
  - Replace `javax.ejb.Stateless` → `jakarta.enterprise.context.ApplicationScoped`
  - Replace `javax.inject.*` → `jakarta.inject.*`
  - Replace `javax.persistence.*` → `jakarta.persistence.*`
  - Replace `@EJB` → `@Inject`
- Why: Convert EJB to CDI bean
- Depends on: Steps 4-11 (entities), Step 3 (Resources)
- Verify: No `javax.ejb` imports remain

### Step 22: Migrate ShoppingCartService.java
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - Replace `javax.ejb.Stateful` → `jakarta.enterprise.context.ApplicationScoped`
  - Replace `javax.inject.*` → `jakarta.inject.*`
  - Replace `javax.persistence.*` → `jakarta.persistence.*`
  - Replace `@EJB` → `@Inject`
- Why: Convert stateful EJB to CDI bean
- Depends on: Steps 4-11 (entities), Step 17 (ProductService), Step 18 (PromoService), Step 19 (ShippingService)
- Verify: No `javax.ejb` imports remain

### Step 23: Migrate ShoppingCartOrderProcessor.java
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - Replace `javax.ejb.Stateless` → `jakarta.enterprise.context.ApplicationScoped`
  - Replace `javax.inject.*` → `jakarta.inject.*`
  - Replace `@EJB` → `@Inject`
- Why: Convert EJB to CDI bean
- Depends on: Step 21 (OrderService)
- Verify: No `javax.ejb` imports remain

### Step 24: ⚠️ COMPLEX — Migrate OrderServiceMDB.java (Message-Driven Bean)
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
  - **BEFORE**: `@MessageDriven` with JMS listener
  - **AFTER**: `@ApplicationScoped` with `@Incoming`
  - Remove `@MessageDriven`, `@ActivationConfigProperty`
  - Remove `implements MessageListener`
  - Remove all `javax.jms.*` imports
  - Add `jakarta.enterprise.context.ApplicationScoped`
  - Add `org.eclipse.microprofile.reactive.messaging.Incoming`
  - Transform `onMessage(Message msg)`:
    - Old: Extract text from JMS Message object
    - New: `@Incoming("orders") public void onMessage(String body)`
    - Body arrives as String directly, no casting needed
  - Keep business logic, just change method signature
- Why: MDB pattern not supported in Quarkus; use SmallRye Reactive Messaging
- Depends on: Step 1, Step 2 (application.properties with messaging config)
- Verify: No `javax.jms` imports, method signature matches `@Incoming`, compiles successfully

### Step 25: ⚠️ COMPLEX — Migrate InventoryNotificationMDB.java (Message-Driven Bean with manual JNDI)
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
  - **BEFORE**: Manual JMS setup with WebLogic JNDI lookups (NO @MessageDriven annotation)
  - **AFTER**: `@ApplicationScoped` with `@Incoming`
  - Remove `implements MessageListener`
  - Remove ALL imports:
    - `javax.jms.*` (JMS API)
    - `javax.naming.*` (JNDI)
    - `javax.rmi.PortableRemoteObject` (RMI)
  - Remove class-level fields: `JNDI_FACTORY`, `JMS_FACTORY`, `TOPIC`, `tcon`, `tsession`, `tsubscriber`
  - Delete `init()` method entirely (manual JNDI/JMS setup)
  - Delete `close()` method entirely (cleanup)
  - Delete `getInitialContext()` method entirely (WebLogic JNDI factory)
  - Add annotations:
    - `jakarta.enterprise.context.ApplicationScoped`
    - `org.eclipse.microprofile.reactive.messaging.Incoming`
  - Transform `onMessage(Message msg)`:
    - Old: `TextMessage msg = (TextMessage) rcvMessage; String orderStr = msg.getBody(String.class);`
    - New: `@Incoming("inventory") public void onMessage(String orderStr)`
    - Body arrives as String directly, no JMS casting needed
  - Keep business logic inside method:
    - `Order order = Transformers.jsonToOrder(orderStr);`
    - Inventory threshold check logic
    - Keep `LOW_THRESHOLD = 50` constant
    - Keep `@Inject CatalogService catalogService`
- Why: This MDB manually sets up WebLogic JNDI connections instead of using @MessageDriven. All JNDI/JMS infrastructure is replaced by SmallRye Reactive Messaging which handles connections automatically.
- Depends on: Step 1, Step 2 (application.properties with `mp.messaging.incoming.inventory` channel), Step 16 (CatalogService)
- Verify: No `javax.jms`, `javax.naming`, `weblogic.*` imports remain; only `onMessage(String)` method exists; compiles successfully

### Step 26: Migrate RestApplication.java
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: MODIFY
- What to do:
  - Replace `javax.ws.rs.*` → `jakarta.ws.rs.*`
  - Keep `@ApplicationPath` as-is (Jakarta REST still uses it)
- Why: Update JAX-RS application to Jakarta namespace
- Depends on: Step 1
- Verify: No `javax.` imports remain

### Step 27: Migrate CartEndpoint.java
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do:
  - Replace `javax.ws.rs.*` → `jakarta.ws.rs.*`
  - Replace `javax.inject.*` → `jakarta.inject.*`
  - Replace `@EJB` → `@Inject`
- Why: Update REST endpoint to Jakarta namespace
- Depends on: Step 22 (ShoppingCartService), Step 17 (ProductService), Step 26 (RestApplication)
- Verify: No `javax.` imports remain

### Step 28: Migrate OrderEndpoint.java
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do:
  - Replace `javax.ws.rs.*` → `jakarta.ws.rs.*`
  - Replace `javax.inject.*` → `jakarta.inject.*`
  - Replace `@EJB` → `@Inject`
- Why: Update REST endpoint to Jakarta namespace
- Depends on: Step 21 (OrderService), Step 26 (RestApplication)
- Verify: No `javax.` imports remain

### Step 29: Migrate ProductEndpoint.java
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do:
  - Replace `javax.ws.rs.*` → `jakarta.ws.rs.*`
  - Replace `javax.inject.*` → `jakarta.inject.*`
  - Replace `@EJB` → `@Inject`
- Why: Update REST endpoint to Jakarta namespace
- Depends on: Step 16 (CatalogService), Step 26 (RestApplication)
- Verify: No `javax.` imports remain

### Step 30: Delete WebLogic stub classes
- File: src/main/java/weblogic/application/ApplicationLifecycleEvent.java
- Action: DELETE
- What to do: Remove entire file
- Why: WebLogic-specific stub, not needed in Quarkus
- Depends on: Step 15 (StartupListener migrated)
- Verify: File deleted, no references remain

### Step 31: Delete WebLogic ApplicationLifecycleListener
- File: src/main/java/weblogic/application/ApplicationLifecycleListener.java
- Action: DELETE
- What to do: Remove entire file
- Why: WebLogic-specific stub, not needed in Quarkus
- Depends on: Step 15 (StartupListener migrated)
- Verify: File deleted, no references remain

### Step 32: Delete WebLogic NonCatalogLogger
- File: src/main/java/weblogic/i18n/logging/NonCatalogLogger.java
- Action: DELETE
- What to do: Remove entire file
- Why: WebLogic-specific stub, not needed in Quarkus
- Depends on: Step 15 (StartupListener migrated)
- Verify: File deleted, no references remain

### Step 33: Delete persistence.xml
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Remove entire file
- Why: Replaced by application.properties datasource config
- Depends on: Step 2 (application.properties created)
- Verify: File deleted, datasource configured in application.properties

### Step 34: Delete beans.xml (if exists)
- File: src/main/webapp/WEB-INF/beans.xml
- Action: DELETE
- What to do: Remove entire file if it exists
- Why: Quarkus enables CDI automatically
- Depends on: Step 1
- Verify: File deleted or confirmed not present

### Step 35: Delete web.xml (if exists)
- File: src/main/webapp/WEB-INF/web.xml
- Action: DELETE
- What to do: Remove entire file if it exists
- Why: Quarkus uses application.properties for configuration
- Depends on: Step 1
- Verify: File deleted or confirmed not present

## Verification

```bash
# Clean compile check
mvn clean compile

# Expected output: BUILD SUCCESS with no compilation errors

# Start Quarkus in dev mode
mvn quarkus:dev

# Expected output:
# - Application starts on port 8080
# - REST endpoints accessible at http://localhost:8080/api/*
# - No errors about missing javax.* classes
# - No errors about WebLogic classes
# - Messaging channels configured (check logs)

# Test REST endpoints
curl http://localhost:8080/api/products
curl http://localhost:8080/api/cart/123

# Check for common migration issues:
grep -r "javax\." src/main/java/  # Should return nothing
grep -r "weblogic\." src/main/java/  # Should return nothing
```

## Notes

### Complex Conversions
- **MDB → Reactive Messaging**: OrderServiceMDB and InventoryNotificationMDB require structural changes from JMS listener pattern to `@Incoming` method pattern. The channel configuration in application.properties enables dev mode testing without a real message broker.

- **WebLogic Lifecycle**: StartupListener extends WebLogic-specific class that doesn't exist in Quarkus. The migration uses Quarkus CDI event observers which provide equivalent functionality in a portable way.

### Messaging Configuration
The application.properties includes both production (AMQP broker) and dev mode (in-memory) configurations. In dev mode (`mvn quarkus:dev`), messages are stored in-memory, allowing testing without external infrastructure.

### Database Migrations
The existing Flyway migrations in `src/main/resources/db/migration/` will be auto-executed by Quarkus Flyway extension at startup. If DataBaseMigrationStartup.java manually runs Flyway, that logic should be removed to avoid duplicate execution.

### Dependencies Removed
- `javaee-web-api` and `javaee-api` → replaced by individual Quarkus extensions
- `jboss-jms-api_2.0_spec` → replaced by SmallRye Reactive Messaging
- `jboss-rmi-api_1.0_spec` → not needed (remote EJB removed)
- `maven-war-plugin` → replaced by `quarkus-maven-plugin`

### Gotchas
1. **Stateful EJBs**: ShoppingCartService is `@Stateful` but Quarkus uses `@ApplicationScoped`. Review if session state management is needed (might need `@SessionScoped` or external session storage).

2. **Transaction Management**: EJB methods are transactional by default. In Quarkus, add `@Transactional` explicitly where needed.

3. **WebLogic Stubs**: The three WebLogic stub classes must be deleted *after* StartupListener is migrated to avoid compilation errors during the transition.

4. **Packaging**: WAR → JAR is a fundamental change. The application will run as a standalone JAR, not deployed to an application server.
