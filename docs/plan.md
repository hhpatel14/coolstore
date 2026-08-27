# Migration Plan

## Goal
Migrate the CoolStore monolith from Java EE 7 (JBoss EAP 7.4) to Quarkus 3

## Source → Target
Java EE 7 (JBoss EAP 7.4) → Quarkus 3

## Scope
- Files affected: 38
- Estimated complexity: High
- Hardest areas:
  1. JMS-based message-driven beans (OrderServiceMDB, InventoryNotificationMDB) - require conversion to reactive messaging
  2. Remote EJB lookups with JNDI (ShoppingCartService, ShippingService) - require CDI injection
  3. Stateful/Stateless EJB session beans - require removal and replacement with CDI

## Key Decisions Applied
1. **Reactive Messaging Framework**: Chose SmallRye Reactive Messaging with in-memory channels for the message-driven beans instead of Kafka or AMQP, as the original JMS Topic implementation was for internal application messaging between components. If external messaging is needed later, channels can be reconfigured.

2. **Persistence Configuration**: Moving from persistence.xml to application.properties follows Quarkus best practices. The datasource JNDI name `java:jboss/datasources/CoolstoreDS` will be replaced with Quarkus datasource configuration.

3. **Hibernate ID Generation**: Explicit sequence/table naming will be added to Order and OrderItem entities to maintain compatibility with existing database schema.

4. **Remote EJB**: The ShippingService remote interface pattern will be replaced with local CDI injection, removing the JNDI lookup overhead. This assumes all components will run in the same Quarkus application (monolith-to-monolith migration).

5. **JAX-RS Application**: The RestApplication class will be removed as Quarkus auto-registers JAX-RS resources at the `/` path. The `/services` path will be configured in application.properties.

6. **WebLogic compatibility classes**: The weblogic.* packages will be deleted as they are not used in Quarkus and appear to be legacy code.

## Approach

**Phase 1: Build Configuration** - Migrate pom.xml to Quarkus, replacing Java EE dependencies with Quarkus extensions and configuring the Quarkus Maven plugin.

**Phase 2: Configuration Files** - Convert persistence.xml to application.properties with Quarkus datasource and Hibernate settings. Remove obsolete deployment descriptors (web.xml, beans.xml).

**Phase 3: Persistence Layer** - Update Resources.java to remove @Produces pattern, migrate to @Inject for EntityManager. Update entity classes for Hibernate sequence naming changes.

**Phase 4: Model Layer** - Update JPA entities (Order, OrderItem) to explicitly name sequences/tables for ID generation to maintain database compatibility.

**Phase 5: Service Layer** - Replace EJB annotations (@Stateless, @Stateful, @MessageDriven) with CDI @ApplicationScoped. Add @Transactional annotations. Convert JMS message-driven beans to reactive messaging.

**Phase 6: REST API Layer** - Update JAX-RS endpoints to replace javax.* imports with jakarta.*. Remove RestApplication class. Update utility classes.

**Phase 7: Messaging Layer** - Convert JMS Topic producer/consumer to SmallRye Reactive Messaging with @Incoming/@Outgoing and Emitter pattern.

**Phase 8: Cleanup** - Remove legacy WebLogic classes, obsolete configuration files, and unused dependencies.

## Steps

### Step 1: Update pom.xml packaging to jar
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Change `<packaging>war</packaging>` to `<packaging>jar</packaging>` and update `<finalName>ROOT</finalName>` to `<finalName>coolstore-monolith</finalName>`
- Why: Quarkus applications are packaged as JAR files, not WAR files
- Depends on: none
- Verify: pom.xml contains `<packaging>jar</packaging>`

### Step 2: Adopt Quarkus BOM in pom.xml
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Replace the dependencies section. Remove javaee-web-api, javaee-api, jboss-jms-api_2.0_spec, and jboss-rmi-api_1.0_spec. Add Quarkus BOM in dependencyManagement section with version 3.2.0.Final or later, then add required Quarkus extensions as dependencies: quarkus-resteasy-reactive-jackson, quarkus-hibernate-orm-panache, quarkus-jdbc-postgresql, quarkus-smallrye-reactive-messaging, quarkus-flyway, quarkus-arc
- Why: Quarkus uses a BOM to manage extension versions and provides replacements for Java EE APIs
- Depends on: Step 1
- Verify: pom.xml contains quarkus-bom in dependencyManagement and Quarkus extension dependencies

### Step 3: Adopt Quarkus Maven plugin in pom.xml
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Replace maven-war-plugin with quarkus-maven-plugin. Update maven-compiler-plugin to version 3.11.0 with release=11 (or 17). Add maven-surefire-plugin version 3.0.0 and maven-failsafe-plugin version 3.0.0
- Why: Quarkus requires its Maven plugin for packaging and dev mode, and modern plugin versions
- Depends on: Step 2
- Verify: pom.xml contains quarkus-maven-plugin and does not contain maven-war-plugin

### Step 4: Add Maven profile for Quarkus native build
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add a profile with id=native containing quarkus-maven-plugin execution for native build with goals build and properties quarkus.package.type=native
- Why: Enables optional native compilation for Quarkus applications
- Depends on: Step 3
- Verify: pom.xml contains native profile with quarkus.package.type=native

### Step 5: Create Quarkus application.properties
- Phase: Configuration Files
- File: src/main/resources/application.properties
- Action: CREATE
- What to do: Create application.properties with datasource configuration (quarkus.datasource.db-kind=postgresql, quarkus.datasource.username=postgresUser, quarkus.datasource.password=postgresPW, quarkus.datasource.jdbc.url=jdbc:postgresql://127.0.0.1:5432/postgresDB), Hibernate settings (quarkus.hibernate-orm.database.generation=none, quarkus.hibernate-orm.log.sql=false, quarkus.hibernate-orm.sql-load-script=no-file), Flyway configuration (quarkus.flyway.migrate-at-start=true), and REST path configuration (quarkus.resteasy-reactive.path=/services)
- Why: Quarkus uses application.properties instead of persistence.xml and deployment descriptors
- Depends on: none
- Verify: File exists with datasource and Hibernate properties configured

### Step 6: COMPLEX - Migrate persistence.xml to application.properties
- Phase: Configuration Files
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Delete persistence.xml as configuration has been moved to application.properties in Step 5. The persistence unit "primary", datasource reference, and Hibernate properties are now in application.properties
- Why: Quarkus does not use persistence.xml; configuration is in application.properties
- Depends on: Step 5
- Verify: File no longer exists

### Step 7: Delete beans.xml deployment descriptor
- Phase: Configuration Files
- File: src/main/webapp/WEB-INF/beans.xml
- Action: DELETE
- What to do: Delete beans.xml file - CDI is enabled by default in Quarkus
- Why: Quarkus ignores beans.xml content and enables CDI automatically
- Depends on: none
- Verify: File no longer exists

### Step 8: Delete web.xml deployment descriptor
- Phase: Configuration Files
- File: src/main/webapp/WEB-INF/web.xml
- Action: DELETE
- What to do: Delete web.xml file - no longer needed in Quarkus
- Why: Quarkus does not use web.xml deployment descriptors
- Depends on: none
- Verify: File no longer exists

### Step 9: COMPLEX - Update Resources.java persistence producer
- Phase: Persistence Layer
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do:
  - BEFORE: @PersistenceContext EntityManager with @Produces pattern
  - AFTER: Remove entire class or simplify to just dependency declarations
  - Specific changes:
    1. Remove: @Produces method, @PersistenceContext annotation
    2. EntityManager injection in services will use @Inject directly
    3. Consider deleting this entire file as it's no longer needed
- Why: Quarkus injects EntityManager directly with @Inject; @Produces pattern for EntityManager is not supported
- Depends on: Step 5
- Verify: File is deleted or no longer contains @Produces EntityManager

### Step 10: Update Order.java entity for sequence naming
- Phase: Model Layer
- File: src/main/java/com/redhat/coolstore/model/Order.java
- Action: MODIFY
- What to do: Add explicit sequence name to @GeneratedValue annotation: `@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_seq")` and add `@SequenceGenerator(name = "order_seq", sequenceName = "ORDERS_SEQ", allocationSize = 1)` before the orderId field. Replace `javax.persistence.*` imports with `jakarta.persistence.*`
- Why: Hibernate's implicit sequence naming has changed; explicit naming ensures database compatibility
- Depends on: none
- Verify: Order.java contains explicit @SequenceGenerator and uses jakarta.persistence imports

### Step 11: Update OrderItem.java entity for sequence naming
- Phase: Model Layer
- File: src/main/java/com/redhat/coolstore/model/OrderItem.java
- Action: MODIFY
- What to do: Add explicit sequence name to @GeneratedValue annotation: `@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "orderitem_seq")` and add `@SequenceGenerator(name = "orderitem_seq", sequenceName = "ORDER_ITEM_SEQ", allocationSize = 1)` before the id field. Replace `javax.persistence.*` imports with `jakarta.persistence.*`
- Why: Hibernate's implicit sequence naming has changed; explicit naming ensures database compatibility
- Depends on: none
- Verify: OrderItem.java contains explicit @SequenceGenerator and uses jakarta.persistence imports

### Step 12: Update CatalogItemEntity.java imports
- Phase: Model Layer
- File: src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` imports with `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE 9+ namespace
- Depends on: none
- Verify: File uses jakarta.persistence imports only

### Step 13: Update InventoryEntity.java imports
- Phase: Model Layer
- File: src/main/java/com/redhat/coolstore/model/InventoryEntity.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` imports with `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE 9+ namespace
- Depends on: none
- Verify: File uses jakarta.persistence imports only

### Step 14: Update Product.java imports
- Phase: Model Layer
- File: src/main/java/com/redhat/coolstore/model/Product.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` imports with `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE 9+ namespace
- Depends on: none
- Verify: File uses jakarta.persistence imports only

### Step 15: Update Promotion.java imports
- Phase: Model Layer
- File: src/main/java/com/redhat/coolstore/model/Promotion.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` imports with `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE 9+ namespace
- Depends on: none
- Verify: File uses jakarta.persistence imports only

### Step 16: Update ShoppingCart.java imports
- Phase: Model Layer
- File: src/main/java/com/redhat/coolstore/model/ShoppingCart.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` imports with `jakarta.persistence.*` if present
- Why: Quarkus 3 uses Jakarta EE 9+ namespace
- Depends on: none
- Verify: File uses jakarta.persistence imports or has no persistence imports

### Step 17: Update ShoppingCartItem.java imports
- Phase: Model Layer
- File: src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` imports with `jakarta.persistence.*` if present
- Why: Quarkus 3 uses Jakarta EE 9+ namespace
- Depends on: none
- Verify: File uses jakarta.persistence imports or has no persistence imports

### Step 18: Update CatalogService.java EJB to CDI
- Phase: Service Layer
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do: Replace `@Stateless` with `@ApplicationScoped`. Add `@Transactional` annotation to updateInventoryItems method. Replace `javax.ejb.Stateless` import with `jakarta.enterprise.context.ApplicationScoped`. Add `import jakarta.transaction.Transactional`. Replace `javax.inject.Inject` with `jakarta.inject.Inject`. Replace `javax.persistence.*` imports with `jakarta.persistence.*`
- Why: Quarkus uses CDI instead of EJB; merge operations require explicit @Transactional
- Depends on: Step 9
- Verify: File uses @ApplicationScoped, has @Transactional on updateInventoryItems, uses jakarta imports

### Step 19: Update OrderService.java EJB to CDI
- Phase: Service Layer
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do: Replace `@Stateless` with `@ApplicationScoped`. Add `@Transactional` annotation to the save method (and any other methods that persist entities). Replace `javax.ejb.Stateless` import with `jakarta.enterprise.context.ApplicationScoped`. Add `import jakarta.transaction.Transactional`. Replace all `javax.*` imports with `jakarta.*` equivalents
- Why: Quarkus uses CDI instead of EJB; persist operations require explicit @Transactional
- Depends on: Step 9
- Verify: File uses @ApplicationScoped, has @Transactional on persistence methods, uses jakarta imports

### Step 20: Update ProductService.java EJB to CDI
- Phase: Service Layer
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do: Replace `@Stateless` with `@ApplicationScoped`. Replace `javax.ejb.Stateless` import with `jakarta.enterprise.context.ApplicationScoped`. Replace all `javax.*` imports with `jakarta.*` equivalents
- Why: Quarkus uses CDI instead of EJB
- Depends on: Step 9
- Verify: File uses @ApplicationScoped and jakarta imports

### Step 21: Update ShippingService.java EJB to CDI and remove Remote EJB
- Phase: Service Layer
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do: Replace `@Stateless` with `@ApplicationScoped`. Remove `implements ShippingServiceRemote` if present. Add `@Transactional` to any methods that might need it. Replace `javax.ejb.Stateless` import with `jakarta.enterprise.context.ApplicationScoped`. Replace all `javax.*` imports with `jakarta.*` equivalents
- Why: Quarkus uses CDI instead of EJB; remote EJB is not supported
- Depends on: Step 9
- Verify: File uses @ApplicationScoped, no remote EJB references, uses jakarta imports

### Step 22: Update PromoService.java imports
- Phase: Service Layer
- File: src/main/java/com/redhat/coolstore/service/PromoService.java
- Action: MODIFY
- What to do: Replace all `javax.*` imports with `jakarta.*` equivalents (javax.inject.Inject → jakarta.inject.Inject, etc.)
- Why: Quarkus 3 uses Jakarta EE 9+ namespace
- Depends on: none
- Verify: File uses jakarta imports only

### Step 23: COMPLEX - Update ShoppingCartService.java stateful EJB to CDI
- Phase: Service Layer
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - BEFORE: @Stateful session bean with JNDI lookup for ShippingService
  - AFTER: @ApplicationScoped CDI bean with @Inject for dependencies
  - Specific changes:
    1. Remove: `@Stateful` annotation, `import javax.ejb.Stateful`
    2. Add: `@ApplicationScoped` annotation, `import jakarta.enterprise.context.ApplicationScoped`
    3. Remove: `lookupShippingServiceRemote()` method and all JNDI-related code (InitialContext, Context imports)
    4. Add: `@Inject ShippingService shippingService;` field
    5. Replace: All calls to `lookupShippingServiceRemote()` with `shippingService`
    6. Replace: All `javax.*` imports with `jakarta.*` equivalents
    7. Note: Changing from @Stateful to @ApplicationScoped means cart state management may need revision for production (consider @SessionScoped or external state management)
- Why: Quarkus does not support @Stateful EJBs; JNDI lookup is not supported; use CDI injection
- Depends on: Step 21
- Verify: File uses @ApplicationScoped, @Inject ShippingService, no JNDI code, uses jakarta imports

### Step 24: COMPLEX - Update ShoppingCartOrderProcessor.java JMS to Reactive Messaging
- Phase: Service Layer
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - BEFORE: @Stateless EJB with JMS Topic injection and JMSContext
  - AFTER: @ApplicationScoped with SmallRye Reactive Messaging Emitter
  - Specific changes:
    1. Remove: `@Stateless`, `@Resource(lookup = "java:/topic/orders") Topic`, `@Inject JMSContext`
    2. Add: `@ApplicationScoped`, `@Inject @Channel("orders") Emitter<String> ordersEmitter;`
    3. Replace: `context.createProducer().send(ordersTopic, Transformers.shoppingCartToJson(cart))` with `ordersEmitter.send(Transformers.shoppingCartToJson(cart))`
    4. Remove: `javax.jms.*` imports and `javax.annotation.Resource`
    5. Add: `import org.eclipse.microprofile.reactive.messaging.Channel;`, `import org.eclipse.microprofile.reactive.messaging.Emitter;`
    6. Replace: All `javax.*` imports with `jakarta.*` equivalents
- Why: JMS is not supported in Quarkus; use SmallRye Reactive Messaging with Emitter for sending messages
- Depends on: Step 9
- Verify: File uses @ApplicationScoped, Emitter<String> with @Channel("orders"), no JMS code, uses jakarta imports

### Step 25: COMPLEX - Convert OrderServiceMDB.java to Reactive Messaging
- Phase: Messaging Layer
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: @MessageDriven EJB with MessageListener interface
  - AFTER: @ApplicationScoped with @Incoming reactive method
  - Specific changes:
    1. Remove: `@MessageDriven` annotation with activationConfig, `implements MessageListener`
    2. Add: `@ApplicationScoped` annotation
    3. Replace: `onMessage(Message rcvMessage)` method with `@Incoming("orders") public void processOrder(String orderStr)`
    4. Remove: All JMS-specific code (TextMessage casting, msg.getBody(), JMSException handling)
    5. Simplify: Method body to directly use the String parameter instead of extracting from JMS Message
    6. Add: `@Transactional` annotation to the method
    7. Remove: `javax.jms.*`, `javax.ejb.*` imports
    8. Add: `import org.eclipse.microprofile.reactive.messaging.Incoming;`, `import jakarta.transaction.Transactional;`
    9. Replace: All `javax.inject.*` imports with `jakarta.inject.*`
- Why: @MessageDriven EJBs are not supported in Quarkus; use reactive messaging with @Incoming
- Depends on: Step 19, Step 18
- Verify: File uses @ApplicationScoped, @Incoming("orders"), @Transactional, no JMS/EJB code, uses jakarta imports

### Step 26: COMPLEX - Convert InventoryNotificationMDB.java to Reactive Messaging or Delete
- Phase: Messaging Layer
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: @MessageDriven EJB with MessageListener interface and JNDI lookups
  - AFTER: @ApplicationScoped with @Incoming reactive method
  - Specific changes:
    1. Remove: `@MessageDriven` annotation with activationConfig, `implements MessageListener`
    2. Add: `@ApplicationScoped` annotation
    3. Replace: `onMessage(Message rcvMessage)` method with `@Incoming("inventory")` method accepting String parameter
    4. Remove: All JMS-specific code and JNDI lookup code (InitialContext, lookup())
    5. Replace: Any JNDI-looked-up services with `@Inject` dependencies
    6. Remove: `javax.jms.*`, `javax.ejb.*`, `javax.naming.*` imports
    7. Add: `import org.eclipse.microprofile.reactive.messaging.Incoming;`
    8. Replace: All `javax.*` imports with `jakarta.*` equivalents
- Why: @MessageDriven EJBs and JNDI are not supported in Quarkus; use reactive messaging
- Depends on: Step 9
- Verify: File uses @ApplicationScoped, @Incoming, no JMS/JNDI code, uses jakarta imports

### Step 27: Add reactive messaging channel configuration
- Phase: Messaging Layer
- File: src/main/resources/application.properties
- Action: MODIFY
- What to do: Add SmallRye Reactive Messaging in-memory channel configuration for "orders" channel: `mp.messaging.outgoing.orders.connector=smallrye-in-memory` and `mp.messaging.incoming.orders.connector=smallrye-in-memory`. Add similar configuration for "inventory" channel if InventoryNotificationMDB is kept
- Why: Configure reactive messaging channels to use in-memory connector for internal application messaging
- Depends on: Step 24, Step 25, Step 26
- Verify: application.properties contains mp.messaging channel configurations

### Step 28: Update CartEndpoint.java REST endpoint
- Phase: REST API Layer
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do: Replace all `javax.ws.rs.*` imports with `jakarta.ws.rs.*`. Replace `javax.inject.Inject` with `jakarta.inject.Inject`. Replace any other `javax.*` imports with `jakarta.*` equivalents
- Why: Quarkus 3 uses Jakarta EE 9+ namespace
- Depends on: Step 23
- Verify: File uses jakarta imports only

### Step 29: Update OrderEndpoint.java REST endpoint
- Phase: REST API Layer
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do: Replace all `javax.ws.rs.*` imports with `jakarta.ws.rs.*`. Replace `javax.inject.Inject` with `jakarta.inject.Inject`. Replace any other `javax.*` imports with `jakarta.*` equivalents
- Why: Quarkus 3 uses Jakarta EE 9+ namespace
- Depends on: Step 19
- Verify: File uses jakarta imports only

### Step 30: Update ProductEndpoint.java REST endpoint
- Phase: REST API Layer
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do: Replace all `javax.ws.rs.*` imports with `jakarta.ws.rs.*`. Replace `javax.inject.Inject` with `jakarta.inject.Inject`. Replace any other `javax.*` imports with `jakarta.*` equivalents
- Why: Quarkus 3 uses Jakarta EE 9+ namespace
- Depends on: Step 20
- Verify: File uses jakarta imports only

### Step 31: Delete RestApplication.java
- Phase: REST API Layer
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: DELETE
- What to do: Delete this file - Quarkus auto-registers JAX-RS resources. The /services path is configured in application.properties (Step 5)
- Why: JAX-RS activation via Application class is not necessary in Quarkus
- Depends on: Step 5, Step 28, Step 29, Step 30
- Verify: File no longer exists

### Step 32: Update Transformers.java utility class
- Phase: REST API Layer
- File: src/main/java/com/redhat/coolstore/utils/Transformers.java
- Action: MODIFY
- What to do: Replace `javax.json.*` imports with `jakarta.json.*` equivalents (JSON-P API). Replace any other `javax.*` imports with `jakarta.*`
- Why: Quarkus 3 uses Jakarta EE 9+ namespace
- Depends on: none
- Verify: File uses jakarta.json imports

### Step 33: Update Producers.java utility class
- Phase: REST API Layer
- File: src/main/java/com/redhat/coolstore/utils/Producers.java
- Action: MODIFY
- What to do: Replace all `javax.*` imports with `jakarta.*` equivalents. Review @Produces annotations - remove if producing simple types that Quarkus can auto-inject (e.g., Logger)
- Why: Quarkus 3 uses Jakarta namespace; some @Produces patterns are no longer needed
- Depends on: none
- Verify: File uses jakarta imports, simplified @Produces usage

### Step 34: Update DataBaseMigrationStartup.java utility class
- Phase: REST API Layer
- File: src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- Action: MODIFY
- What to do: Replace all `javax.*` imports with `jakarta.*` equivalents (javax.annotation.PostConstruct → jakarta.annotation.PostConstruct, javax.inject.Inject → jakarta.inject.Inject, etc.)
- Why: Quarkus 3 uses Jakarta EE 9+ namespace
- Depends on: none
- Verify: File uses jakarta imports only

### Step 35: Update StartupListener.java utility class
- Phase: REST API Layer
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do: Replace all `javax.*` imports with `jakarta.*` equivalents. Review servlet listener patterns and consider replacing with Quarkus lifecycle events if needed
- Why: Quarkus 3 uses Jakarta EE 9+ namespace
- Depends on: none
- Verify: File uses jakarta imports only

### Step 36: Delete ApplicationLifecycleEvent.java WebLogic class
- Phase: Cleanup
- File: src/main/java/weblogic/application/ApplicationLifecycleEvent.java
- Action: DELETE
- What to do: Delete this WebLogic-specific compatibility class - not used in Quarkus
- Why: WebLogic classes are not supported or needed in Quarkus
- Depends on: none
- Verify: File no longer exists

### Step 37: Delete ApplicationLifecycleListener.java WebLogic class
- Phase: Cleanup
- File: src/main/java/weblogic/application/ApplicationLifecycleListener.java
- Action: DELETE
- What to do: Delete this WebLogic-specific compatibility class - not used in Quarkus
- Why: WebLogic classes are not supported or needed in Quarkus
- Depends on: none
- Verify: File no longer exists

### Step 38: Delete NonCatalogLogger.java WebLogic class
- Phase: Cleanup
- File: src/main/java/weblogic/i18n/logging/NonCatalogLogger.java
- Action: DELETE
- What to do: Delete this WebLogic-specific compatibility class - not used in Quarkus
- Why: WebLogic classes are not supported or needed in Quarkus
- Depends on: none
- Verify: File no longer exists

### Step 39: Delete ShippingServiceRemote.java interface
- Phase: Cleanup
- File: src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java
- Action: DELETE
- What to do: Delete the remote EJB interface - no longer needed with local CDI injection
- Why: Remote EJB interfaces are not needed in Quarkus; ShippingService is now a local CDI bean
- Depends on: Step 21, Step 23
- Verify: File no longer exists

## Verification

- Build: `mvn clean package`
- Test: Tests are currently skipped (maven.test.skip=true in pom.xml). After migration, enable tests with `mvn test` and create Quarkus tests if needed
- Blackbox: 
  1. Start PostgreSQL database: `podman run --name myPostgresDb -p 5432:5432 -e POSTGRES_USER=postgresUser -e POSTGRES_PASSWORD=postgresPW -e POSTGRES_DB=postgresDB -d postgres`
  2. Run Quarkus in dev mode: `mvn quarkus:dev`
  3. Navigate to http://localhost:8080/services (or configured REST path)
  4. Verify the CoolStore UI loads correctly
  5. Test key flows:
     - Browse products (GET /services/products)
     - Add items to cart (POST /services/cart)
     - Complete checkout process (POST /services/cart/checkout/{cartId})
     - Verify order is created and inventory updated via reactive messaging
  6. Check logs for order processing messages confirming reactive messaging is working

## Notes

1. **Stateful Session State**: The original ShoppingCartService used @Stateful EJB to maintain per-user cart state. The migration to @ApplicationScoped CDI bean will lose this per-session isolation. For production use, consider:
   - Using @SessionScoped instead of @ApplicationScoped if session state is needed
   - Implementing external session storage (Redis, database)
   - Making cart operations stateless with client-side cart ID management

2. **Reactive Messaging Channels**: The in-memory connector is suitable for single-instance deployments. For clustered deployments or microservices, replace with Kafka or AMQP connector and update application.properties accordingly.

3. **Database Schema Compatibility**: The explicit sequence generators in Order and OrderItem entities (Steps 10-11) ensure the migrated application works with existing database schemas. If creating a new database, verify Flyway migrations are compatible with Quarkus Hibernate.

4. **Keycloak Integration**: The original application uses Keycloak. After migration, integrate Quarkus OIDC extension (quarkus-oidc) and configure in application.properties to restore authentication/authorization. The keycloak.json file will need to be converted to Quarkus OIDC properties.

5. **Port Configuration**: JBoss EAP runs on port 8080. Quarkus also defaults to 8080. No changes needed unless port conflict occurs. Configure with quarkus.http.port if needed.

6. **Dependency Versions**: The migration assumes Quarkus 3.2.0.Final or later. Adjust BOM version in pom.xml as needed for latest stable release.

7. **WebLogic Classes**: The weblogic.* package classes appear unused and are likely legacy code from a previous migration. Verify no runtime references before deletion (Steps 36-38).

8. **Flyway Version**: Update Flyway dependency to 9.x or use quarkus-flyway extension's managed version for compatibility with Quarkus.
