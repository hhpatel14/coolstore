# Migration Plan

## Goal
Migrate a Java EE 7 monolith application from WildFly/JBoss EAP to Quarkus 3

## Source → Target
Java EE 7 (WildFly/JBoss EAP 7.4) → Quarkus 3.x

## Scope
- Files affected: 39
- Estimated complexity: High
- Hardest areas:
  1. JMS Message-Driven Beans conversion to Reactive Messaging
  2. Remote EJB migration to REST endpoints
  3. JNDI lookup replacements with CDI injection

## Key Decisions Applied
1. **Messaging approach**: Convert JMS Topic-based messaging to SmallRye Reactive Messaging with in-memory channels for development. This maintains the pub-sub pattern while leveraging Quarkus's reactive capabilities.
2. **Stateful EJB scope**: Convert `@Stateful` ShoppingCartService to `@ApplicationScoped` rather than `@SessionScoped` to align with Quarkus best practices for stateless REST services. State will be managed externally (client-side or future session store).
3. **Remote EJB**: Convert ShippingService remote EJB to a local CDI bean with REST endpoints, removing JNDI lookups and RMI dependencies.
4. **Hibernate sequence naming**: Accept the new Hibernate 6 default sequence naming strategy (`<entity>_seq` per entity) and update database schema accordingly.
5. **EntityManager producer**: Remove the `@Produces` pattern for EntityManager since Quarkus provides EntityManager injection directly via CDI.

## Approach

**Phase 1: Build Configuration**
Modernize the Maven POM to use Quarkus BOM, plugins, and dependencies. Change packaging from WAR to JAR.

**Phase 2: Configuration Files**
Create Quarkus application.properties for datasource, Hibernate, and messaging configuration. Remove Java EE XML descriptors (persistence.xml, beans.xml, web.xml).

**Phase 3: Models/Entities**
Update JPA entity annotations for Hibernate 6 compatibility, ensuring proper sequence generation strategy.

**Phase 4: Persistence Layer**
Replace `@PersistenceContext` with `@Inject` for EntityManager and remove the producer pattern.

**Phase 5: Service Layer - Core Services**
Convert EJB annotations (`@Stateless`, `@Stateful`) to CDI scopes (`@ApplicationScoped`). Add `@Transactional` annotations for transaction management.

**Phase 6: Service Layer - Messaging**
Convert JMS Message-Driven Beans to reactive message consumers using `@Incoming`. Convert JMS producers to reactive emitters using `@Channel` and `Emitter`.

**Phase 7: REST/API Layer**
Update JAX-RS application class and endpoints, remove javax imports, and ensure compatibility with Quarkus REST.

**Phase 8: Utilities and Startup**
Update utility classes, remove JNDI lookups, and convert startup listeners to Quarkus lifecycle events.

**Phase 9: Cleanup**
Remove obsolete files including Java EE descriptors, legacy configuration, and deployment-specific files.

## Steps

### Step 1: Update Maven POM packaging
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Change `<packaging>war</packaging>` to `<packaging>jar</packaging>`
- Why: Quarkus applications package as JAR files, not WAR files
- Depends on: none
- Verify: `grep '<packaging>jar</packaging>' pom.xml`

### Step 2: Add Quarkus BOM to POM
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add Quarkus BOM and properties to POM:
  - Add properties section with:
    - `quarkus.platform.group-id=io.quarkus.platform`
    - `quarkus.platform.artifact-id=quarkus-bom`
    - `quarkus.platform.version=3.1.0.Final`
    - `compiler-plugin.version=3.10.1`
    - `maven.compiler.release=11`
    - `surefire-plugin.version=3.0.0`
  - Add dependencyManagement section importing Quarkus BOM
- Why: Quarkus requires BOM for dependency version management
- Depends on: Step 1
- Verify: `grep 'quarkus-bom' pom.xml`

### Step 3: Replace Java EE dependencies with Quarkus extensions
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Replace all Java EE dependencies with Quarkus equivalents:
  - Remove: javaee-web-api, javaee-api, jboss-jms-api_2.0_spec, jboss-rmi-api_1.0_spec
  - Add: quarkus-hibernate-orm-panache, quarkus-jdbc-postgresql, quarkus-resteasy-reactive-jackson, quarkus-arc (CDI), quarkus-smallrye-reactive-messaging-in-memory, quarkus-flyway, quarkus-hibernate-validator
  - Keep: flyway-core (already present)
- Why: Quarkus uses its own extensions instead of Java EE APIs
- Depends on: Step 2
- Verify: `grep 'quarkus-hibernate-orm' pom.xml && ! grep 'javaee-api' pom.xml`

### Step 4: Add Quarkus Maven Plugin
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add quarkus-maven-plugin to build/plugins section with executions for build, generate-code, and generate-code-tests goals
- Why: Required for Quarkus application build and dev mode
- Depends on: Step 2
- Verify: `grep 'quarkus-maven-plugin' pom.xml`

### Step 5: Update Maven Compiler Plugin
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Update maven-compiler-plugin configuration to use version from properties and add `-parameters` compiler argument
- Why: Quarkus requires parameter names for CDI injection
- Depends on: Step 2
- Verify: `grep -A5 'maven-compiler-plugin' pom.xml | grep 'parameters'`

### Step 6: Add Maven Surefire Plugin configuration
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add/update maven-surefire-plugin with systemPropertyVariables for JBoss LogManager and maven.home
- Why: Quarkus tests require JBoss LogManager
- Depends on: Step 2
- Verify: `grep -A5 'maven-surefire-plugin' pom.xml`

### Step 7: Add Maven Failsafe Plugin configuration
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add maven-failsafe-plugin for integration tests with native image path configuration
- Why: Enables integration testing including native builds
- Depends on: Step 2
- Verify: `grep 'maven-failsafe-plugin' pom.xml`

### Step 8: Add native build profile
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add Maven profile with id="native" for native compilation with property quarkus.package.type=native
- Why: Enables Quarkus native image compilation
- Depends on: Step 2
- Verify: `grep -A3 'profile.*native' pom.xml`

### Step 9: Create Quarkus application.properties
- Phase: Configuration Files
- File: src/main/resources/application.properties
- Action: CREATE
- What to do: Create application.properties with:
  - Datasource configuration (quarkus.datasource.db-kind=postgresql, jdbc.url, username, password)
  - Hibernate ORM configuration (quarkus.hibernate-orm.database.generation=drop-and-create, dialect, log.sql)
  - Flyway configuration (quarkus.flyway.migrate-at-start=true)
  - Reactive messaging channel configuration for orders topic
  - HTTP port and other Quarkus settings
- Why: Quarkus uses application.properties instead of XML configuration files
- Depends on: Step 1
- Verify: File exists with datasource configuration

### Step 10: Update Order entity for Hibernate 6
- Phase: Models/Entities
- File: src/main/java/com/redhat/coolstore/model/Order.java
- Action: MODIFY
- What to do: Update `@GeneratedValue` to specify strategy and generator name for Hibernate 6 compatibility:
  - Change from: `@GeneratedValue(strategy = GenerationType.AUTO)`
  - Change to: `@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_seq")`
  - Add: `@SequenceGenerator(name = "order_seq", sequenceName = "order_seq", allocationSize = 1)`
- Why: Hibernate 6 changed default sequence naming; explicit configuration prevents issues
- Depends on: Step 9
- Verify: `grep '@SequenceGenerator' src/main/java/com/redhat/coolstore/model/Order.java`

### Step 11: Update OrderItem entity for Hibernate 6
- Phase: Models/Entities
- File: src/main/java/com/redhat/coolstore/model/OrderItem.java
- Action: MODIFY
- What to do: Update `@GeneratedValue` to specify strategy and generator name for Hibernate 6 compatibility:
  - Change from: `@GeneratedValue(strategy = GenerationType.AUTO)`
  - Change to: `@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "orderitem_seq")`
  - Add: `@SequenceGenerator(name = "orderitem_seq", sequenceName = "orderitem_seq", allocationSize = 1)`
- Why: Hibernate 6 changed default sequence naming; explicit configuration prevents issues
- Depends on: Step 9
- Verify: `grep '@SequenceGenerator' src/main/java/com/redhat/coolstore/model/OrderItem.java`

### Step 12: Update imports in all entity classes
- Phase: Models/Entities
- File: src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` imports with `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespaces instead of javax
- Depends on: Step 9
- Verify: `grep 'jakarta.persistence' src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java`

### Step 13: Update imports in InventoryEntity
- Phase: Models/Entities
- File: src/main/java/com/redhat/coolstore/model/InventoryEntity.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` imports with `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespaces instead of javax
- Depends on: Step 9
- Verify: `grep 'jakarta.persistence' src/main/java/com/redhat/coolstore/model/InventoryEntity.java`

### Step 14: Update imports in Order entity
- Phase: Models/Entities
- File: src/main/java/com/redhat/coolstore/model/Order.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` imports with `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespaces instead of javax
- Depends on: Step 10
- Verify: `grep 'jakarta.persistence' src/main/java/com/redhat/coolstore/model/Order.java`

### Step 15: Update imports in OrderItem entity
- Phase: Models/Entities
- File: src/main/java/com/redhat/coolstore/model/OrderItem.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` imports with `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespaces instead of javax
- Depends on: Step 11
- Verify: `grep 'jakarta.persistence' src/main/java/com/redhat/coolstore/model/OrderItem.java`

### Step 16: COMPLEX - Remove EntityManager producer
- Phase: Persistence Layer
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: DELETE
- What to do: Delete the entire Resources.java file as Quarkus provides EntityManager injection directly
- Why: Quarkus automatically provides EntityManager beans; producer pattern is not needed and causes conflicts
- Depends on: Step 9
- Verify: `! test -f src/main/java/com/redhat/coolstore/persistence/Resources.java`

### Step 17: COMPLEX - Convert CatalogService from EJB to CDI
- Phase: Service Layer - Core Services
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless;` with `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
  - Replace `import javax.persistence.*` with `import jakarta.persistence.*`
  - Replace `@Stateless` annotation with `@ApplicationScoped`
  - Add `@Transactional` annotation to the class (import `jakarta.transaction.Transactional;`)
  - Update EntityManager injection from `@Inject` to standard CDI injection (already uses @Inject, just update imports)
- Why: Quarkus uses CDI instead of EJB; @ApplicationScoped provides singleton behavior; @Transactional needed for EntityManager operations
- Depends on: Step 16
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/CatalogService.java && grep '@Transactional' src/main/java/com/redhat/coolstore/service/CatalogService.java`

### Step 18: Convert OrderService from EJB to CDI
- Phase: Service Layer - Core Services
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless;` with `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
  - Replace `import javax.persistence.*` with `import jakarta.persistence.*`
  - Replace `@Stateless` with `@ApplicationScoped`
  - Add `import jakarta.transaction.Transactional;`
  - Add `@Transactional` annotation to methods using EntityManager (save method)
- Why: Quarkus uses CDI instead of EJB; @Transactional required for persist operations
- Depends on: Step 16
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/OrderService.java`

### Step 19: Convert ProductService from EJB to CDI
- Phase: Service Layer - Core Services
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless;` with `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
  - Replace `import javax.persistence.*` with `import jakarta.persistence.*`
  - Replace `@Stateless` with `@ApplicationScoped`
  - Add `@Transactional` annotation to the class
- Why: Quarkus uses CDI instead of EJB; @ApplicationScoped provides singleton behavior
- Depends on: Step 16
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ProductService.java`

### Step 20: COMPLEX - Convert ShippingService from Remote EJB to CDI
- Phase: Service Layer - Core Services
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do:
  - Remove `import javax.ejb.Stateless;` and `import javax.ejb.Remote;`
  - Add `import jakarta.enterprise.context.ApplicationScoped;`
  - Add `import jakarta.ws.rs.*;`
  - Remove `@Stateless` and `@Remote(ShippingServiceRemote.class)` annotations
  - Add `@ApplicationScoped` annotation
  - Add `@Path("/shipping")` annotation to expose as REST service
  - Add `@GET` and `@Path` annotations to public methods (calculateShipping, calculateShippingInsurance)
  - Add `@QueryParam` annotations to method parameters as needed for REST exposure
- Why: Remote EJBs are not supported in Quarkus; converting to REST service maintains functionality
- Depends on: Step 16
- Verify: `grep '@Path' src/main/java/com/redhat/coolstore/service/ShippingService.java`

### Step 21: COMPLEX - Convert ShoppingCartService from Stateful EJB to CDI
- Phase: Service Layer - Core Services
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateful;` with `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
  - Replace `@Stateful` with `@ApplicationScoped`
  - Remove JNDI lookup method `lookupShippingServiceRemote()` and all JNDI-related imports (javax.naming.*)
  - Add `@Inject ShippingService shippingService;` field
  - Replace all calls to `lookupShippingServiceRemote()` with `shippingService`
  - Add `@Transactional` annotation to the class
- Why: Stateful EJBs not supported in Quarkus; CDI injection replaces JNDI lookups; state management moves to application level
- Depends on: Step 20
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ShoppingCartService.java && ! grep 'InitialContext' src/main/java/com/redhat/coolstore/service/ShoppingCartService.java`

### Step 22: COMPLEX - Convert ShoppingCartOrderProcessor from EJB to CDI with Reactive Messaging
- Phase: Service Layer - Messaging
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless;` with `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `import javax.annotation.Resource;` and `import javax.inject.Inject;`
  - Remove all JMS imports (javax.jms.*)
  - Add `import jakarta.inject.Inject;`
  - Add `import org.eclipse.microprofile.reactive.messaging.Channel;`
  - Add `import org.eclipse.microprofile.reactive.messaging.Emitter;`
  - Replace `@Stateless` with `@ApplicationScoped`
  - Remove `@Resource(lookup = "java:/topic/orders") private Topic ordersTopic;`
  - Remove `@Inject private transient JMSContext context;`
  - Add `@Inject @Channel("orders") Emitter<String> ordersEmitter;`
  - Replace `context.createProducer().send(ordersTopic, Transformers.shoppingCartToJson(cart));` with `ordersEmitter.send(Transformers.shoppingCartToJson(cart));`
- Why: JMS not supported in Quarkus; SmallRye Reactive Messaging provides modern alternative
- Depends on: Step 9
- Verify: `grep 'Emitter<String>' src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java`

### Step 23: COMPLEX - Convert OrderServiceMDB from Message-Driven Bean to Reactive Consumer
- Phase: Service Layer - Messaging
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
  - Remove all JMS and EJB imports (javax.ejb.*, javax.jms.*)
  - Add `import jakarta.enterprise.context.ApplicationScoped;`
  - Add `import jakarta.inject.Inject;`
  - Add `import org.eclipse.microprofile.reactive.messaging.Incoming;`
  - Remove `@MessageDriven` annotation and all `@ActivationConfigProperty` annotations
  - Remove `implements MessageListener`
  - Add `@ApplicationScoped` annotation to the class
  - Replace `public void onMessage(Message rcvMessage)` with `@Incoming("orders") public void onMessage(String orderStr)`
  - Remove JMS message processing logic (TextMessage casting, getBody)
  - Directly use the String parameter as the order JSON
  - Add `@Transactional` annotation to the method
- Why: Message-Driven Beans not supported in Quarkus; @Incoming provides reactive message consumption
- Depends on: Step 9, Step 18, Step 17
- Verify: `grep '@Incoming("orders")' src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java`

### Step 24: COMPLEX - Convert InventoryNotificationMDB to Reactive Consumer
- Phase: Service Layer - Messaging
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
  - Remove all JMS, JNDI, and RMI imports (javax.jms.*, javax.naming.*, javax.rmi.*)
  - Add `import jakarta.enterprise.context.ApplicationScoped;`
  - Add `import jakarta.inject.Inject;`
  - Add `import org.eclipse.microprofile.reactive.messaging.Incoming;`
  - Remove all JNDI and JMS-related fields and methods (init, close, getInitialContext)
  - Remove `implements MessageListener`
  - Add `@ApplicationScoped` annotation
  - Replace `public void onMessage(Message rcvMessage)` with `@Incoming("orders") public void onMessage(String orderStr)`
  - Simplify message processing to work with String directly (remove TextMessage casting)
  - Add `@Transactional` annotation to the method
- Why: Message-Driven Beans and JNDI not supported in Quarkus; reactive messaging provides modern alternative
- Depends on: Step 9, Step 17
- Verify: `grep '@Incoming("orders")' src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java`

### Step 25: Update Producers utility class
- Phase: Utilities and Startup
- File: src/main/java/com/redhat/coolstore/utils/Producers.java
- Action: MODIFY
- What to do:
  - Replace `import javax.enterprise.inject.Produces;` with `import jakarta.enterprise.inject.Produces;`
  - Replace `import javax.enterprise.inject.spi.InjectionPoint;` with `import jakarta.enterprise.inject.spi.InjectionPoint;`
  - Update any other javax imports to jakarta
  - Consider removing @Produces if using Quarkus logging (Quarkus can inject Logger directly)
- Why: Quarkus 3 uses Jakarta EE namespaces
- Depends on: Step 9
- Verify: `grep 'jakarta.enterprise' src/main/java/com/redhat/coolstore/utils/Producers.java`

### Step 26: Update Transformers utility class
- Phase: Utilities and Startup
- File: src/main/java/com/redhat/coolstore/utils/Transformers.java
- Action: MODIFY
- What to do: Replace all `javax.json.*` imports with `jakarta.json.*`
- Why: Quarkus 3 uses Jakarta EE namespaces
- Depends on: Step 9
- Verify: `grep 'jakarta.json' src/main/java/com/redhat/coolstore/utils/Transformers.java`

### Step 27: COMPLEX - Convert DataBaseMigrationStartup to Quarkus lifecycle
- Phase: Utilities and Startup
- File: src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- Action: MODIFY
- What to do:
  - Remove `import javax.annotation.PostConstruct;` and `import javax.ejb.Singleton;` and `import javax.ejb.Startup;`
  - Add `import jakarta.enterprise.context.ApplicationScoped;`
  - Add `import io.quarkus.runtime.StartupEvent;`
  - Add `import jakarta.enterprise.event.Observes;`
  - Replace `@Singleton` and `@Startup` with `@ApplicationScoped`
  - Replace `@PostConstruct` method with: `void onStart(@Observes StartupEvent ev)`
  - Add `@Transactional` annotation to the startup method
- Why: EJB Singleton startup beans replaced with Quarkus lifecycle events
- Depends on: Step 9
- Verify: `grep 'StartupEvent' src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java`

### Step 28: Update StartupListener utility
- Phase: Utilities and Startup
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do:
  - Replace `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
  - Update other javax imports to jakarta as needed
- Why: Quarkus 3 uses Jakarta EE namespaces
- Depends on: Step 9
- Verify: `grep 'jakarta.inject' src/main/java/com/redhat/coolstore/utils/StartupListener.java`

### Step 29: Update RestApplication JAX-RS config
- Phase: REST/API Layer
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ws.rs.ApplicationPath;` with `import jakarta.ws.rs.ApplicationPath;`
  - Replace `import javax.ws.rs.core.Application;` with `import jakarta.ws.rs.core.Application;`
  - Class can remain as-is or be simplified (JAX-RS activation not strictly needed in Quarkus)
- Why: Quarkus 3 uses Jakarta EE namespaces; JAX-RS activation automatic in Quarkus
- Depends on: Step 9
- Verify: `grep 'jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/RestApplication.java`

### Step 30: Update CartEndpoint REST resource
- Phase: REST/API Layer
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do:
  - Replace all `javax.ws.rs.*` imports with `jakarta.ws.rs.*`
  - Replace `javax.inject.Inject` with `jakarta.inject.Inject`
  - Ensure all JAX-RS annotations use jakarta namespace
- Why: Quarkus 3 uses Jakarta EE namespaces
- Depends on: Step 21
- Verify: `grep 'jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/CartEndpoint.java`

### Step 31: Update OrderEndpoint REST resource
- Phase: REST/API Layer
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do:
  - Replace all `javax.ws.rs.*` imports with `jakarta.ws.rs.*`
  - Replace `javax.inject.Inject` with `jakarta.inject.Inject`
- Why: Quarkus 3 uses Jakarta EE namespaces
- Depends on: Step 18
- Verify: `grep 'jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java`

### Step 32: Update ProductEndpoint REST resource
- Phase: REST/API Layer
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do:
  - Replace all `javax.ws.rs.*` imports with `jakarta.ws.rs.*`
  - Replace `javax.inject.Inject` with `jakarta.inject.Inject`
- Why: Quarkus 3 uses Jakarta EE namespaces
- Depends on: Step 19
- Verify: `grep 'jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java`

### Step 33: Delete ShippingServiceRemote interface
- Phase: Cleanup
- File: src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java
- Action: DELETE
- What to do: Delete this file as remote EJB interface no longer needed
- Why: Remote EJB pattern replaced with REST endpoints and local CDI injection
- Depends on: Step 20, Step 21
- Verify: `! test -f src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java`

### Step 34: Delete persistence.xml
- Phase: Cleanup
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Delete this file as configuration moved to application.properties
- Why: Quarkus uses application.properties for persistence configuration
- Depends on: Step 9
- Verify: `! test -f src/main/resources/META-INF/persistence.xml`

### Step 35: Delete beans.xml
- Phase: Cleanup
- File: src/main/webapp/WEB-INF/beans.xml
- Action: DELETE
- What to do: Delete this file as CDI configuration automatic in Quarkus
- Why: beans.xml content is ignored in Quarkus; CDI enabled by default
- Depends on: Step 9
- Verify: `! test -f src/main/webapp/WEB-INF/beans.xml`

### Step 36: Delete web.xml
- Phase: Cleanup
- File: src/main/webapp/WEB-INF/web.xml
- Action: DELETE
- What to do: Delete this file as deployment descriptor not used in Quarkus
- Why: Quarkus does not use web.xml; configuration in application.properties
- Depends on: Step 9
- Verify: `! test -f src/main/webapp/WEB-INF/web.xml`

### Step 37: Move static web resources
- Phase: Cleanup
- File: src/main/webapp/
- Action: MODIFY
- What to do: Move all static web content from src/main/webapp/ to src/main/resources/META-INF/resources/ (excluding WEB-INF directory which should be deleted). This includes: app/, bower_components/, partials/, *.jsp, *.json files, and all other static assets.
- Why: Quarkus serves static resources from META-INF/resources instead of webapp
- Depends on: Step 35, Step 36
- Verify: `test -d src/main/resources/META-INF/resources/app`

### Step 38: Delete WebLogic-specific files
- Phase: Cleanup
- File: src/main/java/weblogic/
- Action: DELETE
- What to do: Delete the entire weblogic/ directory tree as these are WebLogic-specific lifecycle listeners not needed in Quarkus
- Why: WebLogic-specific code not compatible with Quarkus
- Depends on: none
- Verify: `! test -d src/main/java/weblogic`

### Step 39: Update .gitignore for Quarkus
- Phase: Cleanup
- File: .gitignore
- Action: MODIFY
- What to do: Add Quarkus-specific ignores: .quarkus/, target/quarkus-app/, *.log files in target/
- Why: Quarkus generates additional build artifacts that should not be committed
- Depends on: none
- Verify: `grep '.quarkus/' .gitignore`

## Verification

- Build: `mvn clean package`
- Test: Tests currently skipped in original POM (maven.test.skip=true); after migration consider enabling with `mvn test`
- Blackbox: 
  1. Start PostgreSQL database: `podman run --name myPostgresDb -p 5432:5432 -e POSTGRES_USER=postgresUser -e POSTGRES_PASSWORD=postgresPW -e POSTGRES_DB=postgresDB -d postgres`
  2. Start Quarkus in dev mode: `mvn quarkus:dev`
  3. Access application at http://localhost:8080
  4. Test REST endpoints:
     - GET http://localhost:8080/services/products - should return product catalog
     - POST http://localhost:8080/services/cart/cartId/itemId/quantity - should add item to cart
     - GET http://localhost:8080/services/cart/cartId - should retrieve cart
     - POST http://localhost:8080/services/cart/checkout/cartId - should process checkout and trigger messaging
  5. Verify messaging: Check logs for order processing messages from both OrderServiceMDB and InventoryNotificationMDB
  6. Verify database: Check that orders are persisted and inventory updated
  7. Note: Keycloak integration will need additional configuration with Quarkus OIDC extension (not in current scope)

## Notes

1. **Keycloak/Security**: The original application uses Keycloak for authentication. This migration plan focuses on the core Java EE to Quarkus conversion. Security integration will require adding `quarkus-oidc` extension and configuring it separately.

2. **Database schema changes**: Due to Hibernate 6's sequence naming changes, database sequences need updating:
   - Old: Single `hibernate_sequence` for all entities
   - New: Individual sequences per entity (`order_seq`, `orderitem_seq`, etc.)
   - Consider running Flyway migration or using Hibernate DDL generation to create sequences

3. **Messaging topology**: The migration converts from JMS Topics to in-memory Reactive Messaging channels. For production:
   - Consider using external message broker (Kafka, AMQP) with appropriate SmallRye Reactive Messaging connectors
   - Update application.properties with connector configuration
   - Multiple consumer pattern (OrderServiceMDB, InventoryNotificationMDB both consuming from "orders") works with broadcast in-memory connector

4. **State management**: ShoppingCartService converted from @Stateful to @ApplicationScoped means:
   - Shopping cart state no longer managed by container
   - Current implementation uses instance variable (not thread-safe for concurrent users)
   - For production, implement proper session management (client tokens, Redis cache, database-backed sessions)

5. **WebLogic files**: The weblogic/ package contains application lifecycle listeners specific to WebLogic and can be safely removed as they're not referenced by the core application.

6. **InventoryNotificationMDB**: This MDB contains WebLogic-specific JNDI configuration and appears to be alternative/legacy code. Verify if it's actively used before migration. The init() method is never called in current setup.

7. **PromoService**: Not analyzed in detail but appears to be a simple CDI bean already - will only need javax to jakarta import updates.

8. **Testing**: The original POM has tests disabled. After migration, create integration tests using `@QuarkusTest` to verify REST endpoints and messaging functionality.

9. **Clustering**: Original application uses JBoss clustering with ActiveMQ. Quarkus clustering requires different approach (Infinispan for distributed caching, external message broker for messaging).

10. **Performance**: Quarkus provides significantly faster startup and lower memory footprint than traditional Java EE. Test and tune memory settings as needed.
