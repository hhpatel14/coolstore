# Migration Plan

## Goal
Migrate a Java EE 7 WAR application to Quarkus 3 with native compilation support

## Source → Target
Java EE 7 (WildFly/JBoss EAP) → Quarkus 3

## Scope
- Files affected: 29
- Estimated complexity: High
- Hardest areas: JMS message-driven beans to Reactive Messaging, JNDI lookups to CDI injection, Remote EJB to local services

## Key Decisions Applied
- **JMS to Reactive Messaging**: Using SmallRye Reactive Messaging with in-memory connectors for JMS topics. The `topic/orders` will be migrated to a MicroProfile Reactive Messaging channel.
- **Remote EJB**: The ShippingService remote EJB will be converted to a local CDI bean since Quarkus does not support remote EJBs and the service is called locally via JNDI lookup.
- **Persistence configuration**: Moving from XML-based persistence.xml to Quarkus application.properties for datasource and Hibernate configuration.
- **Web deployment descriptors**: beans.xml and web.xml will be removed as they are not needed in Quarkus.
- **WebLogic stubs**: Files in the weblogic package are legacy stubs and will be deleted.

## Approach
The migration follows a dependency-ordered, phased approach:

**Phase 1 - Build Configuration**: Migrate from Java EE WAR packaging to Quarkus JAR, adopt Quarkus BOM, replace Java EE dependencies with Quarkus extensions (RESTEasy Reactive, Hibernate ORM with Panache, SmallRye Reactive Messaging, CDI).

**Phase 2 - Configuration Files**: Create application.properties with datasource and Hibernate settings, remove legacy XML descriptors (persistence.xml, beans.xml, web.xml).

**Phase 3 - Models**: Update JPA entities to remove Hibernate-specific naming strategies and ensure compatibility with Quarkus Hibernate ORM.

**Phase 4 - Persistence Layer**: Replace @PersistenceContext with @Inject for EntityManager, remove @Produces for EntityManager.

**Phase 5 - Service Layer**: Convert EJBs to CDI beans (@Stateless → @ApplicationScoped, @Stateful → @SessionScoped), add @Transactional annotations for transaction management, replace JNDI lookups with CDI @Inject, remove Remote EJB interface.

**Phase 6 - Messaging Layer**: Convert JMS message-driven beans to Reactive Messaging @Incoming methods, replace JMS Topic producer with @Channel Emitter.

**Phase 7 - REST Layer**: Remove JAX-RS Application class (not needed in Quarkus), update REST endpoints for Quarkus compatibility.

**Phase 8 - Utilities**: Update utility classes to remove @Produces where unnecessary.

**Phase 9 - Cleanup**: Delete legacy WebLogic stub files and deployment descriptors.

## Steps

### Step 1: Update POM packaging type
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Change `<packaging>war</packaging>` to `<packaging>jar</packaging>`
- Why: Quarkus applications are packaged as JARs, not WARs
- Depends on: none
- Verify: pom.xml contains `<packaging>jar</packaging>`

### Step 2: Add Quarkus BOM
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add Quarkus BOM to dependencyManagement section with version 3.2.0.Final
- Why: Centralize Quarkus dependency version management
- Depends on: Step 1
- Verify: pom.xml contains quarkus-bom in dependencyManagement

### Step 3: Replace Java EE dependencies with Quarkus extensions
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: 
  - Remove: javaee-web-api, javaee-api, jboss-jms-api, jboss-rmi-api dependencies
  - Add: quarkus-resteasy-reactive-jackson, quarkus-hibernate-orm, quarkus-jdbc-h2, quarkus-smallrye-reactive-messaging-kafka, quarkus-arc, quarkus-flyway dependencies
- Why: Replace Java EE APIs with Quarkus extensions
- Depends on: Step 2
- Verify: No javax/javaee dependencies remain in pom.xml

### Step 4: Add Quarkus Maven plugin
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add quarkus-maven-plugin to build plugins section
- Why: Required for Quarkus build and dev mode
- Depends on: Step 2
- Verify: quarkus-maven-plugin present in build plugins

### Step 5: Update Maven Compiler plugin
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Update maven-compiler-plugin configuration to use Java 11 (or 17), add annotation processor paths for Quarkus
- Why: Quarkus requires Java 11+ and uses annotation processors
- Depends on: Step 4
- Verify: Compiler plugin configured for Java 11+

### Step 6: Update Maven Surefire plugin
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add/update maven-surefire-plugin with Quarkus test configuration (system properties for test scope)
- Why: Required for Quarkus test execution
- Depends on: Step 4
- Verify: Surefire plugin configured with Quarkus settings

### Step 7: Update Maven Failsafe plugin
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add maven-failsafe-plugin for integration tests
- Why: Support Quarkus integration testing
- Depends on: Step 4
- Verify: Failsafe plugin present in build plugins

### Step 8: Add native build profile
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add Maven profile named 'native' with native-image configuration and quarkus.package.type=native
- Why: Enable Quarkus native compilation
- Depends on: Step 4
- Verify: Profile 'native' exists in pom.xml

### Step 9: Create Quarkus application.properties
- Phase: Configuration Files
- File: src/main/resources/application.properties
- Action: CREATE
- What to do: Create application.properties with:
  - quarkus.datasource.db-kind=h2
  - quarkus.datasource.jdbc.url=jdbc:h2:mem:coolstore
  - quarkus.hibernate-orm.database.generation=none
  - quarkus.flyway.migrate-at-start=true
  - quarkus.hibernate-orm.log.sql=false
  - mp.messaging.outgoing.orders.connector=smallrye-in-memory
  - mp.messaging.incoming.orders.connector=smallrye-in-memory
- Why: Quarkus uses application.properties for configuration instead of XML files
- Depends on: Step 3
- Verify: File exists with datasource and messaging properties

### Step 10: Update Order entity
- Phase: Models
- File: src/main/java/com/redhat/coolstore/model/Order.java
- Action: MODIFY
- What to do: 
  - Add explicit sequence/table generator with @SequenceGenerator or @TableGenerator for @GeneratedValue
  - Specify strategy explicitly: @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_seq")
- Why: Hibernate identifier generation naming changed; explicit naming prevents migration issues
- Depends on: Step 9
- Verify: @GeneratedValue has explicit strategy and generator

### Step 11: Update OrderItem entity
- Phase: Models
- File: src/main/java/com/redhat/coolstore/model/OrderItem.java
- Action: MODIFY
- What to do:
  - Add explicit sequence/table generator with @SequenceGenerator or @TableGenerator for @GeneratedValue
  - Specify strategy explicitly: @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "orderitem_seq")
- Why: Hibernate identifier generation naming changed; explicit naming prevents migration issues
- Depends on: Step 9
- Verify: @GeneratedValue has explicit strategy and generator

### Step 12: COMPLEX - Migrate EntityManager injection in Resources
- Phase: Persistence Layer
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do:
  - BEFORE: @PersistenceContext EntityManager with @Produces method
  - AFTER: Direct @Inject EntityManager (no producer needed)
  - Specific changes:
    1. Remove: @PersistenceContext annotation
    2. Remove: @Produces annotation from getEntityManager method
    3. Change: Use @Inject for EntityManager field
    4. Consider: Removing this class entirely if it only produces EntityManager
- Why: Quarkus provides EntityManager directly via CDI; @PersistenceContext and @Produces patterns are unnecessary
- Depends on: Step 9
- Verify: grep -n "@PersistenceContext" shows no matches in file

### Step 13: Convert CatalogService from @Stateless to @ApplicationScoped
- Phase: Service Layer
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do:
  - Replace: @Stateless with @ApplicationScoped
  - Add: import javax.enterprise.context.ApplicationScoped
  - Remove: import javax.ejb.Stateless
  - Add: @Transactional annotation to updateInventoryItems method
- Why: Quarkus uses CDI instead of EJBs; methods modifying database need explicit @Transactional
- Depends on: Step 12
- Verify: Class has @ApplicationScoped and updateInventoryItems has @Transactional

### Step 14: Convert OrderService from @Stateless to @ApplicationScoped
- Phase: Service Layer
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do:
  - Replace: @Stateless with @ApplicationScoped
  - Add: @Transactional annotation to save method (EntityManager.persist requires transaction)
- Why: Quarkus uses CDI instead of EJBs; persist operations require explicit @Transactional
- Depends on: Step 12
- Verify: Class has @ApplicationScoped and save method has @Transactional

### Step 15: Convert ProductService from @Stateless to @ApplicationScoped
- Phase: Service Layer
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do:
  - Replace: @Stateless with @ApplicationScoped
  - Add: import javax.enterprise.context.ApplicationScoped
  - Remove: import javax.ejb.Stateless
- Why: Quarkus uses CDI instead of EJBs
- Depends on: Step 12
- Verify: Class has @ApplicationScoped annotation

### Step 16: COMPLEX - Convert ShoppingCartService from @Stateful to @SessionScoped
- Phase: Service Layer
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - BEFORE: @Stateful EJB with JNDI lookup for ShippingServiceRemote
  - AFTER: @SessionScoped CDI bean with @Inject ShippingService
  - Specific changes:
    1. Remove: @Stateful annotation and import javax.ejb.Stateful
    2. Add: @SessionScoped annotation and import javax.enterprise.context.SessionScoped
    3. Remove: lookupShippingServiceRemote() method entirely
    4. Remove: JNDI-related imports (javax.naming.*)
    5. Add: @Inject ShippingService shippingService field
    6. Replace: All calls to lookupShippingServiceRemote() with shippingService
- Why: Quarkus does not support Stateful EJBs or JNDI lookups; use CDI @SessionScoped and @Inject instead
- Depends on: Step 12
- Verify: No @Stateful, no JNDI imports, ShippingService is injected

### Step 17: Convert ShippingService from Remote EJB to local CDI bean
- Phase: Service Layer
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do:
  - Remove: @Stateless, @Remote annotations
  - Add: @ApplicationScoped annotation
  - Keep: implements ShippingServiceRemote (interface becomes local)
- Why: Quarkus does not support Remote EJBs; service is used locally so convert to CDI bean
- Depends on: Step 12
- Verify: Class has @ApplicationScoped, no @Remote or @Stateless

### Step 18: COMPLEX - Convert ShoppingCartOrderProcessor JMS Topic to Reactive Messaging
- Phase: Messaging Layer
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - BEFORE: @Stateless with @Inject JMSContext and @Resource Topic
  - AFTER: @ApplicationScoped with @Channel Emitter
  - Specific changes:
    1. Remove: @Stateless annotation
    2. Add: @ApplicationScoped annotation
    3. Remove: @Inject JMSContext and @Resource Topic fields
    4. Remove: All JMS imports (javax.jms.*, javax.annotation.Resource)
    5. Add: @Inject @Channel("orders") Emitter<String> ordersEmitter
    6. Add: import org.eclipse.microprofile.reactive.messaging.*
    7. Replace: context.createProducer().send() with ordersEmitter.send()
- Why: Quarkus replaces JMS with MicroProfile Reactive Messaging
- Depends on: Step 9, Step 15
- Verify: Uses @Channel Emitter, no JMS imports

### Step 19: COMPLEX - Convert OrderServiceMDB to Reactive Messaging
- Phase: Messaging Layer
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: @MessageDriven EJB implementing MessageListener
  - AFTER: @ApplicationScoped CDI bean with @Incoming method
  - Specific changes:
    1. Remove: @MessageDriven annotation and all @ActivationConfigProperty
    2. Remove: implements MessageListener
    3. Add: @ApplicationScoped annotation
    4. Remove: All JMS imports (javax.jms.*, javax.ejb.*)
    5. Add: import org.eclipse.microprofile.reactive.messaging.Incoming
    6. Change method signature: void onMessage(Message rcvMessage) → void onMessage(String orderStr)
    7. Add: @Incoming("orders") annotation to onMessage method
    8. Simplify: Method receives String directly, no need for TextMessage casting
    9. Add: @Transactional annotation (for EntityManager operations)
- Why: Quarkus does not support Message-Driven Beans; use Reactive Messaging @Incoming
- Depends on: Step 9, Step 13, Step 14
- Verify: Has @ApplicationScoped, @Incoming("orders"), @Transactional, no JMS imports

### Step 20: COMPLEX - Convert InventoryNotificationMDB to Reactive Messaging
- Phase: Messaging Layer
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: MessageListener with manual JNDI topic subscription and WebLogic-specific code
  - AFTER: @ApplicationScoped CDI bean with @Incoming method
  - Specific changes:
    1. Add: @ApplicationScoped annotation at class level
    2. Remove: implements MessageListener
    3. Remove: All WebLogic constants (JNDI_FACTORY, JMS_FACTORY, etc.)
    4. Remove: All connection fields (tcon, tsession, tsubscriber)
    5. Remove: init() and close() methods
    6. Remove: getInitialContext() method
    7. Remove: All JNDI and JMS imports (javax.jms.*, javax.naming.*, javax.rmi.*)
    8. Change: onMessage(Message rcvMessage) → void onMessage(String orderStr)
    9. Add: @Incoming("orders") annotation to onMessage method
    10. Simplify: Remove try-catch for JMS, work directly with String orderStr
    11. Add: @Transactional annotation (for database access)
- Why: Quarkus does not support JNDI or JMS; WebLogic-specific code must be removed
- Depends on: Step 9, Step 13
- Verify: Has @ApplicationScoped, @Incoming("orders"), no JNDI/JMS imports, no WebLogic code

### Step 21: Remove JAX-RS Application class
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: DELETE
- What to do: Delete this file - JAX-RS activation not needed in Quarkus
- Why: Quarkus auto-configures JAX-RS; @ApplicationPath can be set via quarkus.resteasy.path property if needed
- Depends on: Step 9
- Verify: File no longer exists

### Step 22: Update CartEndpoint for Quarkus
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do:
  - Verify @SessionScoped, @Path, @Inject ShoppingCartService are compatible
  - No major changes needed, but ensure imports use javax.enterprise.context.SessionScoped
- Why: Verify compatibility with Quarkus RESTEasy Reactive
- Depends on: Step 16, Step 21
- Verify: File compiles without errors

### Step 23: Update OrderEndpoint for Quarkus
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do:
  - Verify @RequestScoped, @Path, @Inject annotations are compatible
  - No major changes expected
- Why: Verify compatibility with Quarkus RESTEasy Reactive
- Depends on: Step 21
- Verify: File compiles without errors

### Step 24: Update ProductEndpoint for Quarkus
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do:
  - Verify @RequestScoped, @Path, @Inject annotations are compatible
  - No major changes expected
- Why: Verify compatibility with Quarkus RESTEasy Reactive
- Depends on: Step 21
- Verify: File compiles without errors

### Step 25: Update Producers utility class
- Phase: Utilities
- File: src/main/java/com/redhat/coolstore/utils/Producers.java
- Action: MODIFY
- What to do:
  - Review @Produces Logger - can be kept or removed (Quarkus can inject Logger directly)
  - If keeping, ensure it follows Quarkus CDI patterns
- Why: Quarkus handles some producers automatically; verify compatibility
- Depends on: Step 9
- Verify: File compiles, Logger injection works

### Step 26: Update DataBaseMigrationStartup
- Phase: Utilities
- File: src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- Action: MODIFY
- What to do:
  - Review startup code for Quarkus compatibility
  - If using @Singleton @Startup pattern, replace with Quarkus lifecycle events or keep as-is
- Why: Ensure Flyway migration works with Quarkus
- Depends on: Step 9
- Verify: File compiles without errors

### Step 27: Update StartupListener
- Phase: Utilities
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do:
  - If using ServletContextListener, replace with Quarkus @Observes StartupEvent
  - Review for any Java EE specific patterns
- Why: Quarkus uses CDI lifecycle events instead of servlet listeners
- Depends on: Step 9
- Verify: File compiles and startup logic executes

### Step 28: Delete WebLogic ApplicationLifecycleEvent stub
- Phase: Cleanup
- File: src/main/java/weblogic/application/ApplicationLifecycleEvent.java
- Action: DELETE
- What to do: Delete this WebLogic stub file
- Why: WebLogic-specific code not needed in Quarkus
- Depends on: Step 27
- Verify: File no longer exists

### Step 29: Delete WebLogic ApplicationLifecycleListener stub
- Phase: Cleanup
- File: src/main/java/weblogic/application/ApplicationLifecycleListener.java
- Action: DELETE
- What to do: Delete this WebLogic stub file
- Why: WebLogic-specific code not needed in Quarkus
- Depends on: Step 27
- Verify: File no longer exists

### Step 30: Delete WebLogic NonCatalogLogger stub
- Phase: Cleanup
- File: src/main/java/weblogic/i18n/logging/NonCatalogLogger.java
- Action: DELETE
- What to do: Delete this WebLogic stub file
- Why: WebLogic-specific code not needed in Quarkus
- Depends on: Step 27
- Verify: File no longer exists

### Step 31: Remove persistence.xml
- Phase: Cleanup
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Delete this file - configuration moved to application.properties
- Why: Quarkus uses application.properties for persistence configuration
- Depends on: Step 9
- Verify: File no longer exists

### Step 32: Remove beans.xml
- Phase: Cleanup
- File: src/main/webapp/WEB-INF/beans.xml
- Action: DELETE
- What to do: Delete this file - CDI is enabled by default in Quarkus
- Why: beans.xml descriptor content is ignored in Quarkus
- Depends on: Step 9
- Verify: File no longer exists

### Step 33: Remove web.xml
- Phase: Cleanup
- File: src/main/webapp/WEB-INF/web.xml
- Action: DELETE
- What to do: Delete this file - not needed in Quarkus
- Why: Quarkus does not use deployment descriptors
- Depends on: Step 9
- Verify: File no longer exists

## Verification

- Build: `mvn clean compile`
- Test: Tests are currently disabled (maven.test.skip=true). After migration, enable and run: `mvn test`
- Blackbox: 
  1. Start application: `mvn quarkus:dev`
  2. Verify REST endpoints respond:
     - GET http://localhost:8080/services/products - should return product catalog
     - GET http://localhost:8080/services/cart/{cartId} - should return shopping cart
  3. Test shopping cart flow:
     - Add item: POST http://localhost:8080/services/cart/{cartId}/{itemId}/{quantity}
     - View cart: GET http://localhost:8080/services/cart/{cartId}
     - Checkout: POST http://localhost:8080/services/cart/checkout/{cartId}
  4. Verify order processing via messaging (check logs for order messages)
  5. Test native build: `mvn clean package -Pnative` (requires GraalVM)

## Notes

- **JMS to Reactive Messaging**: The in-memory connector is used for development. For production, configure Kafka or AMQP brokers in application.properties and update connector properties.
- **Database**: Currently using H2 in-memory. Flyway migrations (V1_1 and V1_2) will run at startup.
- **Session State**: ShoppingCartService uses @SessionScoped which works for single-instance deployment. For clustered deployments, consider external session storage or stateless design.
- **WebLogic Cleanup**: Three WebLogic stub files are legacy code and unused - safe to delete.
- **Hibernate Sequence Generation**: The implicit naming change in Hibernate requires explicit @SequenceGenerator or @TableGenerator. Verify generated table/sequence names match your database schema.
- **Remote EJB Removal**: ShippingService was marked @Remote but is only called locally via JNDI. After migration, it's a standard CDI bean injected directly.
- **Static Resources**: The webapp directory contains frontend assets (Angular/PatternFly). These will be served from src/main/resources/META-INF/resources in Quarkus. May need to relocate index.jsp and other web content.
- **Health Endpoints**: The health.jsp should be replaced with Quarkus SmallRye Health extension and proper health check beans.
