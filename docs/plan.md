# Migration Plan

## Goal
Migrate a Java EE 7 monolith application to Quarkus 3

## Source → Target
Java EE 7 (WebLogic/WildFly) → Quarkus 3.1.0.Final

## Scope
- Files affected: 38
- Estimated complexity: High
- Hardest areas: 
  1. JMS Message-Driven Beans to Reactive Messaging (OrderServiceMDB, InventoryNotificationMDB)
  2. Remote EJB to REST API conversion (ShippingService)
  3. JNDI lookups to CDI injection (ShoppingCartService, InventoryNotificationMDB)

## Key Decisions Applied
1. **Session Scope for ShoppingCartService**: Migrating @Stateful ShoppingCartService to @SessionScoped instead of @ApplicationScoped, as it maintains per-user shopping cart state. This requires adding quarkus-undertow extension for session support.
2. **JMS to Reactive Messaging**: Converting JMS Topic-based messaging to MicroProfile Reactive Messaging with SmallRye. Using in-memory connector for development; production would need external broker configuration.
3. **Remote EJB to REST**: Converting ShippingService from Remote EJB to REST endpoint with POST methods for service calls.
4. **Persistence Configuration**: Moving all persistence.xml settings to application.properties using Quarkus conventions.
5. **WebLogic artifacts removal**: Removing all WebLogic-specific packages and dependencies as they are not needed in Quarkus.

## Approach

### Phase 1: Build Configuration
Update pom.xml to use Quarkus 3 BOM, plugins, and dependencies. Change packaging from WAR to JAR.

### Phase 2: Configuration Files
Create application.properties for Quarkus configuration, migrate persistence settings, and remove obsolete Java EE descriptors.

### Phase 3: Data Models
Update entity classes to use Jakarta EE namespace and ensure compatibility with Quarkus Hibernate ORM.

### Phase 4: Persistence Layer
Migrate EntityManager injection from @PersistenceContext to @Inject and remove producer patterns.

### Phase 5: Service Layer - EJB Migration
Convert all EJB annotations (@Stateless, @Stateful, @MessageDriven) to CDI scopes and add @Transactional where needed.

### Phase 6: Service Layer - JMS to Reactive
Replace JMS Topic/Queue usage with MicroProfile Reactive Messaging using @Incoming/@Outgoing and Emitters.

### Phase 7: Service Layer - JNDI Removal
Replace all JNDI lookups with CDI @Inject patterns.

### Phase 8: REST Layer
Update JAX-RS endpoints for Quarkus, remove ApplicationPath class if not needed, and migrate Remote EJB to REST.

### Phase 9: Utilities and Producers
Update CDI producers and remove unnecessary @Produces annotations.

### Phase 10: Cleanup
Remove WebLogic-specific classes, legacy deployment descriptors, and obsolete configuration files.

## Steps

### Step 1: Update Maven packaging type
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Change `<packaging>war</packaging>` to `<packaging>jar</packaging>` on line 9
- Why: Quarkus applications are packaged as JAR files by default, not WAR files
- Depends on: none
- Verify: pom.xml shows `<packaging>jar</packaging>`

### Step 2: Add Quarkus BOM to pom.xml
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add Quarkus platform properties and BOM in dependencyManagement section
  - Add to properties section:
    ```xml
    <quarkus.platform.artifact-id>quarkus-bom</quarkus.platform.artifact-id>
    <quarkus.platform.group-id>io.quarkus.platform</quarkus.platform.group-id>
    <quarkus.platform.version>3.1.0.Final</quarkus.platform.version>
    <compiler-plugin.version>3.10.1</compiler-plugin.version>
    <maven.compiler.release>11</maven.compiler.release>
    <surefire-plugin.version>3.0.0</surefire-plugin.version>
    ```
  - Add dependencyManagement section before dependencies:
    ```xml
    <dependencyManagement>
      <dependencies>
        <dependency>
          <groupId>${quarkus.platform.group-id}</groupId>
          <artifactId>${quarkus.platform.artifact-id}</artifactId>
          <version>${quarkus.platform.version}</version>
          <type>pom</type>
          <scope>import</scope>
        </dependency>
      </dependencies>
    </dependencyManagement>
    ```
- Why: Quarkus BOM manages versions of all Quarkus extensions
- Depends on: Step 1
- Verify: pom.xml contains quarkus-bom in dependencyManagement

### Step 3: Replace Java EE dependencies with Quarkus extensions
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Replace existing Java EE dependencies with Quarkus extensions
  - Remove: javaee-web-api, javaee-api, jboss-jms-api_2.0_spec, jboss-rmi-api_1.0_spec
  - Add Quarkus extensions (without version, managed by BOM):
    ```xml
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-resteasy-reactive-jackson</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-hibernate-orm-panache</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-jdbc-h2</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-smallrye-reactive-messaging</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-undertow</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-arc</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-flyway</artifactId>
    </dependency>
    ```
  - Keep flyway-core but update version to 9.16.0 for Quarkus 3 compatibility
- Why: Quarkus uses its own extension model instead of Java EE APIs
- Depends on: Step 2
- Verify: All quarkus-* dependencies present, no javaee-* dependencies remain

### Step 4: Add Quarkus Maven plugins
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Update build plugins section
  - Update maven-compiler-plugin configuration to use maven.compiler.release property and add -parameters arg
  - Add quarkus-maven-plugin:
    ```xml
    <plugin>
      <groupId>${quarkus.platform.group-id}</groupId>
      <artifactId>quarkus-maven-plugin</artifactId>
      <version>${quarkus.platform.version}</version>
      <extensions>true</extensions>
      <executions>
        <execution>
          <goals>
            <goal>build</goal>
            <goal>generate-code</goal>
            <goal>generate-code-tests</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
    ```
  - Add maven-surefire-plugin with Quarkus configuration
  - Add maven-failsafe-plugin for integration tests
  - Remove maven-war-plugin (no longer needed)
- Why: Quarkus requires specific Maven plugins for building and testing
- Depends on: Step 2
- Verify: quarkus-maven-plugin present in pom.xml, maven-war-plugin removed

### Step 5: Add native build profile
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add profiles section for native build
  ```xml
  <profiles>
    <profile>
      <id>native</id>
      <activation>
        <property>
          <name>native</name>
        </property>
      </activation>
      <properties>
        <skipITs>false</skipITs>
        <quarkus.package.type>native</quarkus.package.type>
      </properties>
    </profile>
  </profiles>
  ```
- Why: Enables Quarkus native compilation support
- Depends on: Step 4
- Verify: Native profile exists in pom.xml

### Step 6: Create Quarkus application.properties
- Phase: Configuration Files
- File: src/main/resources/application.properties
- Action: CREATE
- What to do: Create application.properties with datasource, persistence, and messaging configuration
  ```properties
  # Datasource configuration
  quarkus.datasource.db-kind=h2
  quarkus.datasource.username=sa
  quarkus.datasource.password=sa
  quarkus.datasource.jdbc.url=jdbc:h2:mem:coolstore;DB_CLOSE_ON_EXIT=FALSE;DB_CLOSE_DELAY=-1
  
  # Hibernate ORM configuration
  quarkus.hibernate-orm.database.generation=none
  quarkus.hibernate-orm.log.sql=false
  quarkus.hibernate-orm.log.format-sql=true
  quarkus.hibernate-orm.jdbc.statement-fetch-size=10
  quarkus.hibernate-orm.jdbc.statement-batch-size=10
  
  # Flyway configuration
  quarkus.flyway.migrate-at-start=true
  quarkus.flyway.baseline-on-migrate=true
  
  # Reactive Messaging - in-memory connector for orders topic
  mp.messaging.incoming.orders.connector=smallrye-in-memory
  mp.messaging.outgoing.orders.connector=smallrye-in-memory
  
  # HTTP configuration
  quarkus.http.port=8080
  quarkus.http.test-port=8081
  
  # Session configuration for stateful beans
  quarkus.servlet.context-path=/
  ```
- Why: Quarkus uses application.properties instead of XML descriptors for configuration
- Depends on: Step 5
- Verify: File exists with datasource and ORM properties

### Step 7: Update CatalogItemEntity model
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java
- Action: MODIFY
- What to do: Update imports from javax.persistence.* to jakarta.persistence.*
- Why: Quarkus 3 uses Jakarta EE namespace instead of javax
- Depends on: Step 3
- Verify: All javax.persistence imports replaced with jakarta.persistence

### Step 8: Update InventoryEntity model
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/InventoryEntity.java
- Action: MODIFY
- What to do: Update imports from javax.persistence.* to jakarta.persistence.*
- Why: Quarkus 3 uses Jakarta EE namespace instead of javax
- Depends on: Step 3
- Verify: All javax.persistence imports replaced with jakarta.persistence

### Step 9: Update Order model
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/Order.java
- Action: MODIFY
- What to do: Update imports from javax.persistence.* to jakarta.persistence.*
- Why: Quarkus 3 uses Jakarta EE namespace instead of javax
- Depends on: Step 3
- Verify: All javax.persistence imports replaced with jakarta.persistence

### Step 10: Update OrderItem model
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/OrderItem.java
- Action: MODIFY
- What to do: Update imports from javax.persistence.* to jakarta.persistence.*
- Why: Quarkus 3 uses Jakarta EE namespace instead of javax
- Depends on: Step 3
- Verify: All javax.persistence imports replaced with jakarta.persistence

### Step 11: COMPLEX - Migrate EntityManager producer in Resources
- Phase: Persistence Layer
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do:
  - BEFORE: @PersistenceContext private EntityManager em with @Produces getter
  - AFTER: Remove entire class or convert to simple utility if needed
  - Specific changes:
    1. Remove: @PersistenceContext annotation
    2. Remove: @Produces annotation and getter method
    3. Update all imports from javax.* to jakarta.*
    4. Note: EntityManager will be injected directly via @Inject in consuming classes
- Why: Quarkus automatically provides EntityManager beans via CDI, @Produces pattern is not needed and conflicts with Quarkus bean management
- Depends on: Step 6
- Verify: @Produces annotation removed from EntityManager

### Step 12: Update Producers utility
- Phase: Utilities and Producers
- File: src/main/java/com/redhat/coolstore/utils/Producers.java
- Action: MODIFY
- What to do:
  - Update imports from javax.enterprise.* to jakarta.enterprise.*
  - Update javax.enterprise.inject.Produces to jakarta.enterprise.inject.Produces
  - Note: Keep Logger producer but can optionally remove @Produces if adding @Named qualifier
- Why: Jakarta namespace migration and Quarkus CDI optimization
- Depends on: Step 11
- Verify: All javax imports replaced with jakarta

### Step 13: Migrate CatalogService from @Stateless to @ApplicationScoped
- Phase: Service Layer - EJB Migration
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do:
  - Replace: `import javax.ejb.Stateless;` with `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace: `@Stateless` with `@ApplicationScoped`
  - Update all other javax imports to jakarta (inject, persistence)
  - Add: `import jakarta.transaction.Transactional;`
  - Add: `@Transactional` annotation to the class (for updateInventoryItems method which uses merge)
- Why: Stateless EJBs convert to ApplicationScoped CDI beans; transactions must be explicit in Quarkus
- Depends on: Step 11
- Verify: @ApplicationScoped annotation present, @Transactional on class, no @Stateless

### Step 14: Migrate OrderService from @Stateless to @ApplicationScoped
- Phase: Service Layer - EJB Migration
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do:
  - Replace: `import javax.ejb.Stateless;` with `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace: `@Stateless` with `@ApplicationScoped`
  - Update all other javax imports to jakarta
  - Add: `import jakarta.transaction.Transactional;`
  - Add: `@Transactional` annotation to save() method (uses persist)
- Why: Stateless EJBs convert to ApplicationScoped CDI beans; persist operations require @Transactional
- Depends on: Step 11
- Verify: @ApplicationScoped annotation present, @Transactional on save method

### Step 15: Migrate ProductService from @Stateless to @ApplicationScoped
- Phase: Service Layer - EJB Migration
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do:
  - Replace: `import javax.ejb.Stateless;` with `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace: `@Stateless` with `@ApplicationScoped`
  - Update all other javax imports to jakarta
  - Add @Transactional if any database write operations exist
- Why: Stateless EJBs convert to ApplicationScoped CDI beans
- Depends on: Step 11
- Verify: @ApplicationScoped annotation present, no @Stateless

### Step 16: COMPLEX - Migrate ShoppingCartService from @Stateful to @SessionScoped
- Phase: Service Layer - EJB Migration
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - BEFORE: @Stateful EJB with JNDI lookup for ShippingService
  - AFTER: @SessionScoped CDI bean with @Inject for ShippingService
  - Specific changes:
    1. Remove: `import javax.ejb.Stateful;`
    2. Add: `import jakarta.enterprise.context.SessionScoped;`
    3. Add: `import java.io.Serializable;`
    4. Replace: `@Stateful` with `@SessionScoped`
    5. Update: class declaration to `implements Serializable`
    6. Update all javax imports to jakarta
    7. Remove: entire `lookupShippingServiceRemote()` method
    8. Remove: imports for InitialContext, Context, NamingException, Hashtable
    9. Add: `@Inject ShippingService shippingService;` field
    10. Replace: `lookupShippingServiceRemote().calculateShipping(sc)` with `shippingService.calculateShipping(sc)`
    11. Replace: `lookupShippingServiceRemote().calculateShippingInsurance(sc)` with `shippingService.calculateShippingInsurance(sc)`
- Why: Stateful EJBs with session state convert to SessionScoped CDI beans; JNDI lookups not supported in Quarkus
- Depends on: Step 11
- Verify: @SessionScoped present, implements Serializable, no JNDI lookups, ShippingService injected

### Step 17: COMPLEX - Convert ShippingService from Remote EJB to REST endpoint
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do:
  - BEFORE: @Stateless @Remote EJB implementing ShippingServiceRemote
  - AFTER: @ApplicationScoped REST service with JAX-RS endpoints
  - Specific changes:
    1. Remove: `import javax.ejb.Remote;`
    2. Remove: `import javax.ejb.Stateless;`
    3. Add: `import jakarta.enterprise.context.ApplicationScoped;`
    4. Add: `import jakarta.ws.rs.*;`
    5. Add: `import jakarta.ws.rs.core.MediaType;`
    6. Replace: `@Stateless @Remote` with `@ApplicationScoped @Path("/shipping")`
    7. Remove: `implements ShippingServiceRemote`
    8. Add: `@POST @Path("/calculate")` and `@Consumes(MediaType.APPLICATION_JSON) @Produces(MediaType.APPLICATION_JSON)` to calculateShipping method
    9. Add: `@POST @Path("/insurance")` and `@Consumes(MediaType.APPLICATION_JSON) @Produces(MediaType.APPLICATION_JSON)` to calculateShippingInsurance method
    10. Note: Keep method signatures and logic unchanged, only add REST annotations
- Why: Remote EJBs not supported in Quarkus; must convert to REST endpoints for remote access
- Depends on: Step 11
- Verify: @Path annotation on class and methods, no @Remote or @Stateless

### Step 18: Migrate ShoppingCartOrderProcessor from @Stateless to @ApplicationScoped
- Phase: Service Layer - EJB Migration
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - Replace: `import javax.ejb.Stateless;` with `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace: `@Stateless` with `@ApplicationScoped`
  - Update all other javax imports to jakarta
  - Note: JMS conversion will happen in a later step
- Why: Stateless EJBs convert to ApplicationScoped CDI beans
- Depends on: Step 11
- Verify: @ApplicationScoped annotation present, no @Stateless

### Step 19: COMPLEX - Convert ShoppingCartOrderProcessor JMS Topic to Emitter
- Phase: Service Layer - JMS to Reactive
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - BEFORE: JMS Topic with JMSContext to send messages
  - AFTER: MicroProfile Reactive Messaging Emitter
  - Specific changes:
    1. Remove: `import javax.annotation.Resource;`
    2. Remove: `import javax.jms.JMSContext;`
    3. Remove: `import javax.jms.Topic;`
    4. Add: `import org.eclipse.microprofile.reactive.messaging.Channel;`
    5. Add: `import org.eclipse.microprofile.reactive.messaging.Emitter;`
    6. Remove: `@Inject private transient JMSContext context;` field
    7. Remove: `@Resource(lookup = "java:/topic/orders") private Topic ordersTopic;` field
    8. Add: `@Inject @Channel("orders") Emitter<String> ordersEmitter;` field
    9. Replace: `context.createProducer().send(ordersTopic, Transformers.shoppingCartToJson(cart));`
    10. With: `ordersEmitter.send(Transformers.shoppingCartToJson(cart));`
- Why: JMS not supported in Quarkus; use MicroProfile Reactive Messaging instead
- Depends on: Step 18
- Verify: Emitter injected with @Channel("orders"), no JMS imports

### Step 20: COMPLEX - Convert OrderServiceMDB from @MessageDriven to @Incoming
- Phase: Service Layer - JMS to Reactive
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: @MessageDriven MDB implementing MessageListener
  - AFTER: @ApplicationScoped CDI bean with @Incoming method
  - Specific changes:
    1. Remove: `import javax.ejb.ActivationConfigProperty;`
    2. Remove: `import javax.ejb.MessageDriven;`
    3. Remove: `import javax.jms.*;`
    4. Add: `import jakarta.enterprise.context.ApplicationScoped;`
    5. Add: `import org.eclipse.microprofile.reactive.messaging.Incoming;`
    6. Remove: entire `@MessageDriven` annotation block
    7. Add: `@ApplicationScoped` to class
    8. Remove: `implements MessageListener`
    9. Replace: `@Override public void onMessage(Message rcvMessage)` method signature
    10. With: `@Incoming("orders") public void onMessage(String orderStr)`
    11. Simplify method body: remove TextMessage casting, directly use orderStr parameter
    12. Remove: JMSException handling
    13. Update: javax.inject to jakarta.inject
- Why: MDBs not supported in Quarkus; use Reactive Messaging @Incoming instead
- Depends on: Step 14
- Verify: @ApplicationScoped and @Incoming("orders") present, no @MessageDriven, parameter is String

### Step 21: COMPLEX - Convert InventoryNotificationMDB from JMS to Reactive Messaging
- Phase: Service Layer - JMS to Reactive
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: MessageListener with WebLogic JNDI and manual topic subscription
  - AFTER: @ApplicationScoped CDI bean with @Incoming reactive method
  - Specific changes:
    1. Remove: all JMS imports (javax.jms.*)
    2. Remove: all JNDI imports (javax.naming.*, javax.rmi.*)
    3. Remove: java.util.Hashtable import
    4. Add: `import jakarta.enterprise.context.ApplicationScoped;`
    5. Add: `import org.eclipse.microprofile.reactive.messaging.Incoming;`
    6. Add: `@ApplicationScoped` to class
    7. Remove: `implements MessageListener`
    8. Remove: all static JNDI constants (JNDI_FACTORY, JMS_FACTORY, TOPIC)
    9. Remove: all TopicConnection/TopicSession/TopicSubscriber fields
    10. Replace: `public void onMessage(Message rcvMessage)` with `@Incoming("orders") public void onMessage(String orderStr)`
    11. Simplify method body to directly parse orderStr, remove TextMessage casting
    12. Remove: init(), close(), and getInitialContext() methods entirely
    13. Update: javax.inject to jakarta.inject
- Why: JMS and JNDI not supported in Quarkus; WebLogic-specific code must be removed
- Depends on: Step 13
- Verify: @ApplicationScoped and @Incoming("orders") present, no JMS/JNDI imports, no init/close methods

### Step 22: Update CartEndpoint REST service
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do:
  - Update all javax.ws.rs imports to jakarta.ws.rs
  - Update javax.inject to jakarta.inject
  - Update javax.ejb to jakarta.enterprise.context if any EJB references exist
- Why: Jakarta namespace migration for JAX-RS
- Depends on: Step 16
- Verify: All javax imports replaced with jakarta

### Step 23: Update OrderEndpoint REST service
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do:
  - Update all javax.ws.rs imports to jakarta.ws.rs
  - Update javax.inject to jakarta.inject
- Why: Jakarta namespace migration for JAX-RS
- Depends on: Step 14
- Verify: All javax imports replaced with jakarta

### Step 24: Update ProductEndpoint REST service
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do:
  - Update all javax.ws.rs imports to jakarta.ws.rs
  - Update javax.inject to jakarta.inject
- Why: Jakarta namespace migration for JAX-RS
- Depends on: Step 15
- Verify: All javax imports replaced with jakarta

### Step 25: Update or remove RestApplication
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: MODIFY
- What to do:
  - Update javax.ws.rs imports to jakarta.ws.rs
  - Note: Can optionally remove this file and configure path in application.properties using `quarkus.resteasy-reactive.path=/services`
  - If keeping: update imports and keep @ApplicationPath("/services")
- Why: JAX-RS activation optional in Quarkus but can be kept for compatibility
- Depends on: Step 6
- Verify: Jakarta imports, or file removed with property in application.properties

### Step 26: Update PromoService
- Phase: Service Layer - EJB Migration
- File: src/main/java/com/redhat/coolstore/service/PromoService.java
- Action: MODIFY
- What to do:
  - Update javax.inject to jakarta.inject if present
  - Update javax.enterprise to jakarta.enterprise if present
  - Add @ApplicationScoped if not already annotated
- Why: Ensure CDI bean is properly scoped
- Depends on: Step 11
- Verify: Jakarta imports, proper CDI scope

### Step 27: Update StartupListener
- Phase: Utilities and Producers
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do:
  - Update all javax imports to jakarta (inject, enterprise, servlet if present)
  - If using @WebListener or ServletContextListener, these need jakarta.servlet imports
  - Consider replacing with Quarkus @Observes StartupEvent pattern if using ApplicationLifecycleListener
- Why: Jakarta namespace migration and Quarkus lifecycle event model
- Depends on: Step 6
- Verify: All javax imports replaced with jakarta or Quarkus lifecycle

### Step 28: Update DataBaseMigrationStartup
- Phase: Utilities and Producers
- File: src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- Action: MODIFY
- What to do:
  - Update all javax imports to jakarta
  - If using @Singleton or @Startup, replace with jakarta.enterprise.context.ApplicationScoped
  - Add `@Observes StartupEvent` pattern if needed for startup execution
  - Update to use Quarkus Flyway integration if directly invoking Flyway
- Why: Jakarta namespace and Quarkus startup lifecycle integration
- Depends on: Step 6
- Verify: Jakarta imports, Quarkus lifecycle pattern

### Step 29: Update Transformers utility
- Phase: Utilities and Producers
- File: src/main/java/com/redhat/coolstore/utils/Transformers.java
- Action: MODIFY
- What to do:
  - Update any javax imports to jakarta (likely none if pure utility)
  - Verify JSON parsing works with Quarkus Jackson integration
- Why: Ensure compatibility with Quarkus
- Depends on: Step 3
- Verify: Works with Quarkus Jackson

### Step 30: Update Product model
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/Product.java
- Action: MODIFY
- What to do: Update any javax imports to jakarta if present
- Why: Jakarta namespace migration
- Depends on: Step 3
- Verify: All javax imports replaced with jakarta

### Step 31: Update Promotion model
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/Promotion.java
- Action: MODIFY
- What to do: Update any javax imports to jakarta if present
- Why: Jakarta namespace migration
- Depends on: Step 3
- Verify: All javax imports replaced with jakarta

### Step 32: Update ShoppingCart model
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/ShoppingCart.java
- Action: MODIFY
- What to do: Update any javax imports to jakarta if present, ensure Serializable if used with @SessionScoped
- Why: Jakarta namespace migration and session compatibility
- Depends on: Step 3
- Verify: All javax imports replaced with jakarta

### Step 33: Update ShoppingCartItem model
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java
- Action: MODIFY
- What to do: Update any javax imports to jakarta if present
- Why: Jakarta namespace migration
- Depends on: Step 3
- Verify: All javax imports replaced with jakarta

### Step 34: Delete ShippingServiceRemote interface
- Phase: Cleanup
- File: src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java
- Action: DELETE
- What to do: Delete this file — no longer needed as ShippingService is now a REST endpoint
- Why: Remote EJB interfaces not used in Quarkus REST architecture
- Depends on: Step 17
- Verify: File no longer exists

### Step 35: Delete beans.xml
- Phase: Cleanup
- File: src/main/webapp/WEB-INF/beans.xml
- Action: DELETE
- What to do: Delete this file — content is ignored in Quarkus, CDI is auto-configured
- Why: Quarkus ignores beans.xml descriptor content and auto-configures CDI
- Depends on: Step 20, Step 21
- Verify: File no longer exists

### Step 36: Delete persistence.xml
- Phase: Cleanup
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Delete this file — configuration moved to application.properties
- Why: Quarkus uses application.properties for persistence configuration
- Depends on: Step 6
- Verify: File no longer exists

### Step 37: Delete WebLogic stub classes
- Phase: Cleanup
- File: src/main/java/weblogic/application/ApplicationLifecycleEvent.java
- Action: DELETE
- What to do: Delete this WebLogic-specific stub file
- Why: WebLogic classes not needed in Quarkus
- Depends on: Step 27
- Verify: File no longer exists

### Step 38: Delete WebLogic ApplicationLifecycleListener
- Phase: Cleanup
- File: src/main/java/weblogic/application/ApplicationLifecycleListener.java
- Action: DELETE
- What to do: Delete this WebLogic-specific stub file
- Why: WebLogic classes not needed in Quarkus
- Depends on: Step 27
- Verify: File no longer exists

### Step 39: Delete WebLogic NonCatalogLogger
- Phase: Cleanup
- File: src/main/java/weblogic/i18n/logging/NonCatalogLogger.java
- Action: DELETE
- What to do: Delete this WebLogic-specific stub file
- Why: WebLogic classes not needed in Quarkus
- Depends on: Step 27
- Verify: File no longer exists

### Step 40: Delete or update web.xml
- Phase: Cleanup
- File: src/main/webapp/WEB-INF/web.xml
- Action: DELETE
- What to do: Delete this file — deployment descriptors not needed in Quarkus
- Why: Quarkus does not use web.xml; configuration moved to application.properties and annotations
- Depends on: Step 6, Step 25
- Verify: File no longer exists

## Verification

- Build: `mvn clean compile`
- Test: Tests are currently disabled (maven.test.skip=true in pom.xml). After migration, enable tests and run: `mvn test`
- Blackbox: 
  1. Start the application: `mvn quarkus:dev`
  2. Verify application starts on port 8080
  3. Test REST endpoints:
     - GET http://localhost:8080/services/products - should return product catalog
     - GET http://localhost:8080/services/cart/{cartId} - should return shopping cart
     - POST http://localhost:8080/services/cart/{cartId}/{itemId}/{quantity} - should add item to cart
     - POST http://localhost:8080/services/cart/checkout/{cartId} - should process order and trigger reactive messaging
  4. Monitor logs for reactive messaging activity when orders are processed
  5. Verify database operations work (Flyway migrations, entity CRUD)
  6. Check health endpoint: http://localhost:8080/q/health

## Notes

1. **Session State**: ShoppingCartService uses @SessionScoped which requires quarkus-undertow extension. For a truly cloud-native approach, consider externalizing session state to Redis or a database.

2. **Reactive Messaging**: The migration uses in-memory connector for simplicity. For production:
   - Configure external message broker (Kafka, AMQP, etc.)
   - Update application.properties with broker connection details
   - Add appropriate connector dependencies (quarkus-smallrye-reactive-messaging-kafka, etc.)

3. **Flyway Version**: Updated Flyway to 9.16.0 for Quarkus 3 compatibility. Migration scripts in src/main/resources/db/migration should work without changes.

4. **Native Compilation**: The native profile is configured but may require additional reflection/resource configuration for entities, JSON serialization, and Flyway SQL scripts. Add to application.properties if needed:
   ```
   quarkus.native.additional-build-args=-H:ResourceConfigurationFiles=resources-config.json
   ```

5. **ShippingService REST API**: Changed from Remote EJB to REST. Clients (like ShoppingCartService) now use CDI injection for local calls. If remote access is needed, clients must use REST client with @RestClient interface.

6. **Transaction Boundaries**: All database write operations now explicitly marked with @Transactional. Review business logic to ensure proper transaction boundaries.

7. **WebLogic Artifacts**: All WebLogic-specific stub classes removed. If StartupListener uses these, it needs refactoring to use Quarkus lifecycle events (@Observes StartupEvent).

8. **Entity ID Generation**: The Kantra analysis flagged potential issues with Hibernate ID generation sequence naming. Review entity @GeneratedValue strategies and test thoroughly.

9. **JAX-RS Path**: Current configuration keeps /services as the base path via RestApplication. Can be simplified by removing RestApplication and using `quarkus.resteasy-reactive.path=/services` property.

10. **Development Mode**: Quarkus dev mode (`mvn quarkus:dev`) provides live reload, dev UI at /q/dev, and enhanced developer experience compared to Java EE application servers.
