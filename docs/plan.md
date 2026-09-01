# Migration Plan

## Goal
Migrate a Java EE 7 monolithic application to Quarkus 3, replacing EJB, JMS, and JNDI patterns with Quarkus equivalents.

## Source → Target
Java EE 7 (JBoss EAP 7.4) → Quarkus 3

## Scope
- Files affected: 27
- Estimated complexity: High
- Hardest areas: 
  1. JMS message-driven beans migration to Quarkus Reactive Messaging
  2. JNDI lookups replacement with CDI injection
  3. Remote EJB replacement with direct service injection

## Key Decisions Applied

1. **Reactive Messaging Implementation**: Using Quarkus SmallRye Reactive Messaging with in-memory channels to replace JMS Topics. This approach maintains the async messaging pattern while simplifying the infrastructure (no external message broker needed for basic functionality).

2. **EntityManager Injection**: Removing the @Produces pattern for EntityManager in favor of direct @PersistenceContext injection in services, as Quarkus handles this automatically.

3. **Sequence/Table Naming**: Maintaining explicit @SequenceGenerator and @TableGenerator annotations where needed to ensure backward compatibility with existing database schema.

4. **Packaging**: Changing from WAR to JAR packaging as Quarkus uses an embedded server model rather than external application server deployment.

5. **Logger Injection**: Replacing custom Logger producer with Quarkus's built-in JBoss Logging, using constructor-based or field injection patterns.

## Approach

**Phase 1: Build Configuration**
Update Maven POM to replace Java EE dependencies with Quarkus BOM, plugins, and extensions. Change packaging from WAR to JAR.

**Phase 2: Configuration Files**
Create Quarkus application.properties to replace persistence.xml JNDI datasource configuration. Remove obsolete Java EE descriptors (web.xml, beans.xml).

**Phase 3: Persistence Layer**
Remove EntityManager producer pattern and update persistence configuration for Quarkus.

**Phase 4: Model Layer**
Update JPA entity annotations for Quarkus/Hibernate compatibility, specifically sequence generation strategies.

**Phase 5: Service Layer - Simple**
Replace @Stateless/@Stateful annotations with @ApplicationScoped, add @Transactional annotations where EntityManager operations occur.

**Phase 6: Service Layer - Complex (Messaging)**
Replace JMS MessageDriven beans with Reactive Messaging @Incoming/@Outgoing patterns. Replace JMS Topic injection with Emitter pattern.

**Phase 7: Service Layer - JNDI Removal**
Remove all JNDI InitialContext lookups, replacing with direct CDI injection.

**Phase 8: Utilities**
Update startup/lifecycle beans to use Quarkus patterns (@Startup, @PostConstruct). Update logger production.

**Phase 9: REST Layer**
Update REST application configuration for Quarkus RESTEasy.

**Phase 10: Cleanup**
Remove obsolete files (deployment descriptors, WebLogic-specific files).

## Steps

### Step 1: Update POM packaging type
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Change `<packaging>war</packaging>` to `<packaging>jar</packaging>`
- Why: Quarkus uses JAR packaging with embedded server instead of WAR deployment to application servers
- Depends on: none
- Verify: `grep '<packaging>jar</packaging>' pom.xml`

### Step 2: Add Quarkus BOM to POM
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Replace Java EE dependencies with Quarkus BOM
  - Remove: `javaee-web-api`, `javaee-api`, `jboss-jms-api_2.0_spec`, `jboss-rmi-api_1.0_spec` dependencies
  - Add in `<dependencyManagement>` section:
    ```xml
    <dependency>
        <groupId>io.quarkus.platform</groupId>
        <artifactId>quarkus-bom</artifactId>
        <version>3.0.0.Final</version>
        <type>pom</type>
        <scope>import</scope>
    </dependency>
    ```
  - Add dependencies:
    ```xml
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-hibernate-orm-panache</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-jdbc-postgresql</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-resteasy-reactive-jackson</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-smallrye-reactive-messaging</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-smallrye-reactive-messaging-in-memory</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-arc</artifactId>
    </dependency>
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-core</artifactId>
    </dependency>
    ```
- Why: Quarkus uses a BOM for centralized version management and requires Quarkus-specific extensions
- Depends on: Step 1
- Verify: `grep 'quarkus-bom' pom.xml && grep 'quarkus-hibernate-orm-panache' pom.xml`

### Step 3: Add Quarkus Maven plugin
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Replace maven-war-plugin with quarkus-maven-plugin
  - Remove: `maven-war-plugin`
  - Add:
    ```xml
    <plugin>
        <groupId>io.quarkus.platform</groupId>
        <artifactId>quarkus-maven-plugin</artifactId>
        <version>3.0.0.Final</version>
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
- Why: Quarkus requires its own Maven plugin for building and code generation
- Depends on: Step 2
- Verify: `grep 'quarkus-maven-plugin' pom.xml`

### Step 4: Update Maven Compiler plugin
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Update compiler plugin version and configuration
  - Update version to 3.11.0
  - Update source and target to 11 or 17
  - Add parameters configuration:
    ```xml
    <configuration>
        <encoding>${project.encoding}</encoding>
        <release>11</release>
        <parameters>true</parameters>
    </configuration>
    ```
- Why: Quarkus requires Java 11+ and parameter name preservation for CDI
- Depends on: Step 3
- Verify: `grep '<release>11</release>' pom.xml && grep '<parameters>true</parameters>' pom.xml`

### Step 5: Add Maven Surefire plugin
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add or update maven-surefire-plugin
  ```xml
  <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-surefire-plugin</artifactId>
      <version>3.0.0</version>
      <configuration>
          <systemPropertyVariables>
              <java.util.logging.manager>org.jboss.logmanager.LogManager</java.util.logging.manager>
          </systemPropertyVariables>
      </configuration>
  </plugin>
  ```
- Why: Quarkus tests require specific log manager configuration
- Depends on: Step 4
- Verify: `grep 'maven-surefire-plugin' pom.xml`

### Step 6: Add Maven Failsafe plugin
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add maven-failsafe-plugin for integration tests
  ```xml
  <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-failsafe-plugin</artifactId>
      <version>3.0.0</version>
      <executions>
          <execution>
              <goals>
                  <goal>integration-test</goal>
                  <goal>verify</goal>
              </goals>
          </execution>
      </executions>
      <configuration>
          <systemPropertyVariables>
              <java.util.logging.manager>org.jboss.logmanager.LogManager</java.util.logging.manager>
          </systemPropertyVariables>
      </configuration>
  </plugin>
  ```
- Why: Separate integration tests from unit tests in Quarkus projects
- Depends on: Step 5
- Verify: `grep 'maven-failsafe-plugin' pom.xml`

### Step 7: Add native build profile
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add native profile in profiles section
  ```xml
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
  ```
- Why: Enable native compilation capability for Quarkus
- Depends on: Step 6
- Verify: `grep 'quarkus.package.type' pom.xml`

### Step 8: Create Quarkus application.properties
- Phase: Configuration Files
- File: src/main/resources/application.properties
- Action: CREATE
- What to do: Create Quarkus configuration file with datasource and messaging configuration
  ```properties
  # Datasource configuration
  quarkus.datasource.db-kind=postgresql
  quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/postgresDB
  quarkus.datasource.username=postgresUser
  quarkus.datasource.password=postgresPW
  
  # Hibernate ORM configuration
  quarkus.hibernate-orm.database.generation=none
  quarkus.hibernate-orm.log.sql=false
  quarkus.hibernate-orm.log.format-sql=true
  quarkus.hibernate-orm.jdbc.statement-batch-size=20
  
  # Flyway migration
  quarkus.flyway.migrate-at-start=true
  quarkus.flyway.locations=classpath:db/migration
  
  # Reactive Messaging - in-memory channels
  mp.messaging.incoming.orders.connector=smallrye-in-memory
  mp.messaging.outgoing.orders.connector=smallrye-in-memory
  
  # HTTP configuration
  quarkus.http.port=8080
  
  # Logging
  quarkus.log.level=INFO
  quarkus.log.console.enable=true
  ```
- Why: Quarkus uses application.properties instead of persistence.xml and other XML descriptors
- Depends on: Step 1
- Verify: `test -f src/main/resources/application.properties && grep 'quarkus.datasource' src/main/resources/application.properties`

### Step 9: Remove EntityManager producer
- Phase: Persistence Layer
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: DELETE
- What to do: Delete this file - Quarkus handles EntityManager injection automatically
- Why: @Produces annotation on EntityManager is not supported in Quarkus; @PersistenceContext injection works directly
- Depends on: Step 8
- Verify: `test ! -f src/main/java/com/redhat/coolstore/persistence/Resources.java`

### Step 10: Update Order entity sequence generation
- Phase: Model Layer
- File: src/main/java/com/redhat/coolstore/model/Order.java
- Action: MODIFY
- What to do: Update @GeneratedValue to use explicit strategy
  - BEFORE: `@GeneratedValue`
  - AFTER: 
    ```java
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_seq")
    @SequenceGenerator(name = "order_seq", sequenceName = "order_sequence", allocationSize = 1)
    ```
- Why: Quarkus/Hibernate 6 changed default sequence naming; explicit configuration ensures backward compatibility with existing database
- Depends on: Step 8
- Verify: `grep '@SequenceGenerator' src/main/java/com/redhat/coolstore/model/Order.java`

### Step 11: Update OrderItem entity sequence generation
- Phase: Model Layer
- File: src/main/java/com/redhat/coolstore/model/OrderItem.java
- Action: MODIFY
- What to do: Update @GeneratedValue to use explicit strategy
  - BEFORE: `@GeneratedValue`
  - AFTER:
    ```java
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "orderitem_seq")
    @SequenceGenerator(name = "orderitem_seq", sequenceName = "orderitem_sequence", allocationSize = 1)
    ```
- Why: Ensure consistent sequence naming for backward compatibility
- Depends on: Step 8
- Verify: `grep '@SequenceGenerator' src/main/java/com/redhat/coolstore/model/OrderItem.java`

### Step 12: Update ProductService to ApplicationScoped
- Phase: Service Layer - Simple
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do: Replace EJB annotations with CDI
  - Remove: `import javax.ejb.Stateless;` and `@Stateless`
  - Add: `import jakarta.enterprise.context.ApplicationScoped;` and `@ApplicationScoped`
  - Add: `import jakarta.transaction.Transactional;` and `@Transactional` on methods that use EntityManager
- Why: Quarkus uses CDI @ApplicationScoped instead of @Stateless; @Transactional required for persistence operations
- Depends on: Step 9
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ProductService.java`

### Step 13: Update PromoService to ApplicationScoped
- Phase: Service Layer - Simple
- File: src/main/java/com/redhat/coolstore/service/PromoService.java
- Action: MODIFY
- What to do: Replace EJB annotation with CDI
  - Remove: `import javax.ejb.Stateless;` and `@Stateless` (if present)
  - Add: `import jakarta.enterprise.context.ApplicationScoped;` and `@ApplicationScoped`
- Why: Quarkus uses CDI @ApplicationScoped instead of @Stateless
- Depends on: Step 9
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/PromoService.java || echo "Check file contents"`

### Step 14: Update CatalogService to ApplicationScoped with Transactional
- Phase: Service Layer - Simple
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do: Replace EJB annotations with CDI and add transaction management
  - Remove: `import javax.ejb.Stateless;` and `@Stateless`
  - Add: `import jakarta.enterprise.context.ApplicationScoped;` and `@ApplicationScoped`
  - Add: `import jakarta.transaction.Transactional;`
  - Add `@Transactional` annotation to `updateInventoryItems()` method (EntityManager.merge requires transaction)
  - Update all `javax.persistence.*` imports to `jakarta.persistence.*`
  - Update all `javax.inject.*` imports to `jakarta.inject.*`
- Why: Quarkus uses CDI; EntityManager merge operations require explicit @Transactional
- Depends on: Step 9
- Verify: `grep '@Transactional' src/main/java/com/redhat/coolstore/service/CatalogService.java && grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/CatalogService.java`

### Step 15: Update OrderService to ApplicationScoped with Transactional
- Phase: Service Layer - Simple
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do: Replace EJB annotations with CDI and add transaction management
  - Remove: `import javax.ejb.Stateless;` and `@Stateless`
  - Add: `import jakarta.enterprise.context.ApplicationScoped;` and `@ApplicationScoped`
  - Add: `import jakarta.transaction.Transactional;`
  - Add `@Transactional` annotation to `save()` method (EntityManager.persist requires transaction)
  - Update all `javax.persistence.*` imports to `jakarta.persistence.*`
  - Update all `javax.inject.*` imports to `jakarta.inject.*`
- Why: EntityManager persist operations require explicit @Transactional in Quarkus
- Depends on: Step 9
- Verify: `grep '@Transactional' src/main/java/com/redhat/coolstore/service/OrderService.java`

### Step 16: COMPLEX - Convert ShoppingCartService from Stateful to ApplicationScoped
- Phase: Service Layer - JNDI Removal
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do: Major refactoring to remove stateful session bean and JNDI lookup
  - BEFORE: `@Stateful` with instance variable `private ShoppingCart cart`
  - AFTER: `@ApplicationScoped` with cart management via session or request scope
  - Remove: `import javax.ejb.Stateful;`, `@Stateful`, JNDI lookup code in `lookupShippingServiceRemote()`, `InitialContext` imports
  - Add: `import jakarta.enterprise.context.ApplicationScoped;`, `@ApplicationScoped`
  - Add: `import jakarta.inject.Inject;` for ShippingService
  - Replace `lookupShippingServiceRemote()` calls with direct injection:
    ```java
    @Inject
    ShippingService shippingService;
    ```
  - Update calls from `lookupShippingServiceRemote().calculateShipping(sc)` to `shippingService.calculateShipping(sc)`
  - Add `@Transactional` to methods performing business logic
  - Update all `javax.*` imports to `jakarta.*`
  - Change cart management: add cartId parameter or use session-scoped cart holder bean
- Why: @Stateful EJBs not supported in Quarkus; JNDI lookups replaced with CDI injection
- Depends on: Step 9
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ShoppingCartService.java && ! grep 'InitialContext' src/main/java/com/redhat/coolstore/service/ShoppingCartService.java`

### Step 17: Update ShippingService - remove Remote EJB
- Phase: Service Layer - Simple
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do: Replace EJB annotations with CDI
  - Remove: `import javax.ejb.Stateless;`, `import javax.ejb.Remote;`, `@Stateless`, `@Remote`
  - Add: `import jakarta.enterprise.context.ApplicationScoped;` and `@ApplicationScoped`
  - Keep implementing ShippingServiceRemote interface
  - Add `@Transactional` to calculation methods
- Why: Remote EJBs not supported in Quarkus; use standard CDI beans
- Depends on: Step 9
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ShippingService.java && ! grep '@Remote' src/main/java/com/redhat/coolstore/service/ShippingService.java`

### Step 18: COMPLEX - Convert ShoppingCartOrderProcessor to use Emitter
- Phase: Service Layer - Complex (Messaging)
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do: Replace JMS Topic with Reactive Messaging Emitter
  - BEFORE: JMS pattern with `@Resource` Topic and `JMSContext`
  - AFTER: Reactive Messaging Emitter pattern
  - Remove: `import javax.ejb.Stateless;`, `@Stateless`, `import javax.annotation.Resource;`, `@Resource`, `import javax.jms.*;`, JMS-related fields
  - Add: `import jakarta.enterprise.context.ApplicationScoped;`, `@ApplicationScoped`
  - Add: `import org.eclipse.microprofile.reactive.messaging.Channel;`
  - Add: `import org.eclipse.microprofile.reactive.messaging.Emitter;`
  - Add: `import jakarta.transaction.Transactional;`
  - Replace:
    ```java
    @Inject
    private transient JMSContext context;
    
    @Resource(lookup = "java:/topic/orders")
    private Topic ordersTopic;
    
    public void process(ShoppingCart cart) {
        log.info("Sending order from processor: ");
        context.createProducer().send(ordersTopic, Transformers.shoppingCartToJson(cart));
    }
    ```
  - With:
    ```java
    @Inject
    @Channel("orders")
    Emitter<String> ordersEmitter;
    
    @Transactional
    public void process(ShoppingCart cart) {
        log.info("Sending order from processor: ");
        ordersEmitter.send(Transformers.shoppingCartToJson(cart));
    }
    ```
  - Update all `javax.*` imports to `jakarta.*`
- Why: JMS not directly supported in Quarkus; Reactive Messaging with Emitter provides similar async messaging
- Depends on: Step 8
- Verify: `grep '@Channel' src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java && grep 'Emitter' src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java`

### Step 19: COMPLEX - Convert OrderServiceMDB to Reactive Messaging
- Phase: Service Layer - Complex (Messaging)
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do: Replace MessageDriven bean with Reactive Messaging @Incoming method
  - BEFORE: `@MessageDriven` with `MessageListener` interface
  - AFTER: `@ApplicationScoped` with `@Incoming` method
  - Remove: `import javax.ejb.*;`, `@MessageDriven`, `ActivationConfigProperty`, `implements MessageListener`, `import javax.jms.*;`, all JMS imports
  - Add: `import jakarta.enterprise.context.ApplicationScoped;`, `@ApplicationScoped`
  - Add: `import org.eclipse.microprofile.reactive.messaging.Incoming;`
  - Add: `import jakarta.transaction.Transactional;`
  - Replace:
    ```java
    @MessageDriven(name = "OrderServiceMDB", activationConfig = {...})
    public class OrderServiceMDB implements MessageListener {
        @Override
        public void onMessage(Message rcvMessage) {
            TextMessage msg = null;
            try {
                if (rcvMessage instanceof TextMessage) {
                    msg = (TextMessage) rcvMessage;
                    String orderStr = msg.getBody(String.class);
                    // process order
                }
            } catch (JMSException e) {
                throw new RuntimeException(e);
            }
        }
    }
    ```
  - With:
    ```java
    @ApplicationScoped
    public class OrderServiceMDB {
        @Inject
        OrderService orderService;
        
        @Inject
        CatalogService catalogService;
        
        @Incoming("orders")
        @Transactional
        public void onMessage(String orderStr) {
            System.out.println("\nMessage recd !");
            System.out.println("Received order: " + orderStr);
            Order order = Transformers.jsonToOrder(orderStr);
            System.out.println("Order object is " + order);
            orderService.save(order);
            order.getItemList().forEach(orderItem -> {
                catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity());
            });
        }
    }
    ```
  - Update all `javax.inject.*` imports to `jakarta.inject.*`
- Why: @MessageDriven EJBs not supported in Quarkus; Reactive Messaging @Incoming provides message consumption
- Depends on: Step 8, Step 18
- Verify: `grep '@Incoming' src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java && ! grep 'MessageDriven' src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java`

### Step 20: COMPLEX - Convert InventoryNotificationMDB to Reactive Messaging
- Phase: Service Layer - Complex (Messaging)
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do: Replace MessageListener with Reactive Messaging, remove JNDI/WebLogic code
  - BEFORE: Manual JMS setup with WebLogic JNDI, `implements MessageListener`
  - AFTER: `@ApplicationScoped` with `@Incoming` method
  - Remove: All JNDI code, `init()`, `close()`, `getInitialContext()`, WebLogic-specific constants, JMS imports, `implements MessageListener`
  - Add: `import jakarta.enterprise.context.ApplicationScoped;`, `@ApplicationScoped`
  - Add: `import org.eclipse.microprofile.reactive.messaging.Incoming;`
  - Add: `import jakarta.transaction.Transactional;`
  - Replace entire class body with:
    ```java
    @ApplicationScoped
    public class InventoryNotificationMDB {
        private static final int LOW_THRESHOLD = 50;
        
        @Inject
        private CatalogService catalogService;
        
        @Incoming("orders")
        @Transactional
        public void onMessage(String orderStr) {
            System.out.println("received message inventory");
            Order order = Transformers.jsonToOrder(orderStr);
            order.getItemList().forEach(orderItem -> {
                int old_quantity = catalogService.getCatalogItemById(orderItem.getProductId()).getInventory().getQuantity();
                int new_quantity = old_quantity - orderItem.getQuantity();
                if (new_quantity < LOW_THRESHOLD) {
                    System.out.println("Inventory for item " + orderItem.getProductId() + " is below threshold (" + LOW_THRESHOLD + "), contact supplier!");
                } else {
                    orderItem.setQuantity(new_quantity);
                }
            });
        }
    }
    ```
  - Update all `javax.inject.*` imports to `jakarta.*`
  - Remove all `javax.naming.*`, `javax.rmi.*` imports
- Why: JNDI and JMS not supported in Quarkus; Reactive Messaging replaces both
- Depends on: Step 8, Step 18
- Verify: `grep '@Incoming' src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java && ! grep 'InitialContext' src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java`

### Step 21: Update DataBaseMigrationStartup to Quarkus lifecycle
- Phase: Utilities
- File: src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- Action: MODIFY
- What to do: Update EJB lifecycle to Quarkus/CDI pattern and use Quarkus Flyway
  - Remove: `import javax.ejb.*;`, `@Singleton`, `@Startup`, `@TransactionManagement`, manual Flyway initialization
  - Add: `import io.quarkus.runtime.Startup;`, `@Startup`
  - Add: `import jakarta.enterprise.context.ApplicationScoped;`, `@ApplicationScoped`
  - Simplify to use Quarkus-managed Flyway (configured in application.properties):
    ```java
    @ApplicationScoped
    @Startup
    public class DataBaseMigrationStartup {
        // Quarkus Flyway auto-migration enabled via application.properties
        // This class can be simplified or removed as Quarkus handles migration
        // Keep for logging or custom migration logic if needed
    }
    ```
  - Update all `javax.*` imports to `jakarta.*`
  - Remove `@Resource` datasource injection as Quarkus Flyway handles it
- Why: Quarkus has built-in Flyway support configured via application.properties; EJB lifecycle annotations not needed
- Depends on: Step 8
- Verify: `grep 'io.quarkus.runtime.Startup' src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java`

### Step 22: Update Producers utility for Quarkus logging
- Phase: Utilities
- File: src/main/java/com/redhat/coolstore/utils/Producers.java
- Action: MODIFY
- What to do: Update Logger producer to use JBoss Logging
  - Remove custom Logger producer (optional in Quarkus, can use direct injection)
  - Update to:
    ```java
    package com.redhat.coolstore.utils;
    
    import jakarta.enterprise.inject.Produces;
    import jakarta.enterprise.inject.spi.InjectionPoint;
    import org.jboss.logging.Logger;
    
    public class Producers {
        @Produces
        public Logger produceLog(InjectionPoint injectionPoint) {
            return Logger.getLogger(injectionPoint.getMember().getDeclaringClass().getName());
        }
    }
    ```
  - Or remove entirely and use `@Inject Logger log;` directly with Quarkus auto-injection
  - Update `javax.enterprise.*` imports to `jakarta.enterprise.*`
- Why: Quarkus uses JBoss Logging; Jakarta namespace required
- Depends on: none
- Verify: `grep 'jakarta.enterprise' src/main/java/com/redhat/coolstore/utils/Producers.java || ! test -f src/main/java/com/redhat/coolstore/utils/Producers.java`

### Step 23: Update StartupListener to Quarkus lifecycle
- Phase: Utilities
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do: Replace ServletContextListener with Quarkus lifecycle events
  - Remove: `import javax.servlet.*;`, `implements ServletContextListener`
  - Add: `import io.quarkus.runtime.StartupEvent;`
  - Add: `import jakarta.enterprise.context.ApplicationScoped;`, `@ApplicationScoped`
  - Add: `import jakarta.enterprise.event.Observes;`
  - Replace:
    ```java
    public class StartupListener implements ServletContextListener {
        @Override
        public void contextInitialized(ServletContextEvent event) {
            // startup logic
        }
    }
    ```
  - With:
    ```java
    @ApplicationScoped
    public class StartupListener {
        void onStart(@Observes StartupEvent event) {
            // startup logic
        }
    }
    ```
- Why: Servlet listeners not used in Quarkus; use CDI lifecycle events
- Depends on: none
- Verify: `grep '@Observes StartupEvent' src/main/java/com/redhat/coolstore/utils/StartupListener.java`

### Step 24: Update RestApplication for Quarkus
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: MODIFY
- What to do: Update JAX-RS application configuration for Quarkus
  - Update `javax.ws.rs.*` imports to `jakarta.ws.rs.*`
  - Keep `@ApplicationPath("/services")` annotation
  - Quarkus auto-configures JAX-RS, minimal changes needed
- Why: Quarkus uses Jakarta namespace; JAX-RS application class still valid
- Depends on: none
- Verify: `grep 'jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/RestApplication.java`

### Step 25: Update CartEndpoint REST resource
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do: Update to Jakarta namespace and Quarkus CDI
  - Update all `javax.ws.rs.*` imports to `jakarta.ws.rs.*`
  - Update `javax.inject.*` to `jakarta.inject.*`
  - Update `javax.enterprise.*` to `jakarta.enterprise.*`
  - Consider replacing `@SessionScoped` with `@ApplicationScoped` and using request parameters for cart management
- Why: Jakarta namespace required; session management may need adjustment for Quarkus
- Depends on: Step 16
- Verify: `grep 'jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/CartEndpoint.java`

### Step 26: Update OrderEndpoint REST resource
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do: Update to Jakarta namespace
  - Update all `javax.ws.rs.*` imports to `jakarta.ws.rs.*`
  - Update all `javax.inject.*` imports to `jakarta.inject.*`
- Why: Jakarta namespace required for Quarkus
- Depends on: Step 15
- Verify: `grep 'jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java`

### Step 27: Update ProductEndpoint REST resource
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do: Update to Jakarta namespace
  - Update all `javax.ws.rs.*` imports to `jakarta.ws.rs.*`
  - Update all `javax.inject.*` imports to `jakarta.inject.*`
- Why: Jakarta namespace required for Quarkus
- Depends on: Step 12
- Verify: `grep 'jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java`

### Step 28: Update Transformers utility
- Phase: Utilities
- File: src/main/java/com/redhat/coolstore/utils/Transformers.java
- Action: MODIFY
- What to do: Update any Java EE imports to Jakarta if present
  - Check for and update any `javax.*` imports to `jakarta.*`
- Why: Consistency with Jakarta namespace across project
- Depends on: none
- Verify: `! grep 'import javax\\.' src/main/java/com/redhat/coolstore/utils/Transformers.java || echo "Manual check needed"`

### Step 29: Update all model entities to Jakarta namespace
- Phase: Model Layer
- File: src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java
- Action: MODIFY
- What to do: Update all `javax.persistence.*` imports to `jakarta.persistence.*`
- Why: Jakarta namespace required for Quarkus
- Depends on: Step 8
- Verify: `grep 'jakarta.persistence' src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java`

### Step 30: Update InventoryEntity to Jakarta namespace
- Phase: Model Layer
- File: src/main/java/com/redhat/coolstore/model/InventoryEntity.java
- Action: MODIFY
- What to do: Update all `javax.persistence.*` imports to `jakarta.persistence.*`
- Why: Jakarta namespace required for Quarkus
- Depends on: Step 8
- Verify: `grep 'jakarta.persistence' src/main/java/com/redhat/coolstore/model/InventoryEntity.java`

### Step 31: Update Product model to Jakarta namespace
- Phase: Model Layer
- File: src/main/java/com/redhat/coolstore/model/Product.java
- Action: MODIFY
- What to do: Update all `javax.persistence.*` imports to `jakarta.persistence.*` if present
- Why: Jakarta namespace required for Quarkus
- Depends on: Step 8
- Verify: `! grep 'import javax\\.persistence' src/main/java/com/redhat/coolstore/model/Product.java || echo "Manual check needed"`

### Step 32: Update Promotion model to Jakarta namespace
- Phase: Model Layer
- File: src/main/java/com/redhat/coolstore/model/Promotion.java
- Action: MODIFY
- What to do: Update all `javax.*` imports to `jakarta.*` if present
- Why: Jakarta namespace required for Quarkus
- Depends on: Step 8
- Verify: `! grep 'import javax\\.' src/main/java/com/redhat/coolstore/model/Promotion.java || echo "Manual check needed"`

### Step 33: Update ShoppingCart model to Jakarta namespace
- Phase: Model Layer
- File: src/main/java/com/redhat/coolstore/model/ShoppingCart.java
- Action: MODIFY
- What to do: Update all `javax.*` imports to `jakarta.*` if present
- Why: Jakarta namespace required for Quarkus
- Depends on: Step 8
- Verify: `! grep 'import javax\\.' src/main/java/com/redhat/coolstore/model/ShoppingCart.java || echo "Manual check needed"`

### Step 34: Update ShoppingCartItem model to Jakarta namespace
- Phase: Model Layer
- File: src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java
- Action: MODIFY
- What to do: Update all `javax.*` imports to `jakarta.*` if present
- Why: Jakarta namespace required for Quarkus
- Depends on: Step 8
- Verify: `! grep 'import javax\\.' src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java || echo "Manual check needed"`

### Step 35: Delete web.xml deployment descriptor
- Phase: Cleanup
- File: src/main/webapp/WEB-INF/web.xml
- Action: DELETE
- What to do: Delete this file - not needed in Quarkus
- Why: Quarkus doesn't use web.xml; configuration done via annotations and application.properties
- Depends on: Step 24, Step 25, Step 26, Step 27
- Verify: `test ! -f src/main/webapp/WEB-INF/web.xml`

### Step 36: Delete or update beans.xml
- Phase: Cleanup
- File: src/main/webapp/WEB-INF/beans.xml
- Action: MODIFY
- What to do: Move to src/main/resources/META-INF/beans.xml with minimal content or delete
  - If keeping: create empty beans.xml in src/main/resources/META-INF/ with:
    ```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <beans xmlns="https://jakarta.ee/xml/ns/jakartaee"
           xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
           xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/beans_3_0.xsd"
           version="3.0"
           bean-discovery-mode="all">
    </beans>
    ```
  - Then delete src/main/webapp/WEB-INF/beans.xml
- Why: Quarkus ignores beans.xml descriptor content but may need empty marker; location changes for JAR packaging
- Depends on: Step 1
- Verify: `test ! -f src/main/webapp/WEB-INF/beans.xml`

### Step 37: Delete persistence.xml
- Phase: Cleanup
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Delete this file - replaced by application.properties configuration
- Why: Quarkus uses application.properties for datasource and persistence configuration
- Depends on: Step 8
- Verify: `test ! -f src/main/resources/META-INF/persistence.xml`

### Step 38: Delete WebLogic application files
- Phase: Cleanup
- File: src/main/java/weblogic/application/ApplicationLifecycleListener.java
- Action: DELETE
- What to do: Delete entire weblogic package directory - not applicable to Quarkus
- Why: WebLogic-specific lifecycle listeners not needed in Quarkus
- Depends on: Step 23
- Verify: `test ! -d src/main/java/weblogic`

### Step 39: Delete WebLogic ApplicationLifecycleEvent
- Phase: Cleanup
- File: src/main/java/weblogic/application/ApplicationLifecycleEvent.java
- Action: DELETE
- What to do: Already deleted as part of Step 38 (entire weblogic directory)
- Why: WebLogic-specific code not applicable
- Depends on: Step 38
- Verify: `test ! -d src/main/java/weblogic`

### Step 40: Delete or update ShippingServiceRemote interface
- Phase: Cleanup
- File: src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java
- Action: MODIFY
- What to do: Remove @Remote annotation if present, keep as plain interface
  - Remove: `import javax.ejb.Remote;`, `@Remote`
  - Keep interface definition for ShippingService implementation
- Why: Remote EJB interfaces not needed but can keep as service contract interface
- Depends on: Step 17
- Verify: `! grep '@Remote' src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java || echo "Check file"`

## Verification

- Build: `mvn clean package`
- Test: Tests can be added later; current project has `maven.test.skip=true`
- Blackbox:
  1. Start PostgreSQL database: `podman run --name myPostgresDb -p 5432:5432 -e POSTGRES_USER=postgresUser -e POSTGRES_PASSWORD=postgresPW -e POSTGRES_DB=postgresDB -d postgres`
  2. Run Quarkus application: `mvn quarkus:dev`
  3. Navigate to http://localhost:8080
  4. Verify the coolstore UI loads
  5. Add items to cart
  6. Complete checkout process
  7. Verify order processing in application logs (both OrderServiceMDB and InventoryNotificationMDB should process messages)
  8. Check database for persisted orders: `podman exec -it myPostgresDb psql -U postgresUser -d postgresDB -c "SELECT * FROM orders;"`

## Notes

### Reactive Messaging Channel Configuration
The migration uses SmallRye Reactive Messaging with in-memory connector for the "orders" channel. This maintains the async messaging pattern without requiring an external message broker. For production with multiple instances or guaranteed delivery, consider:
- Adding SmallRye Reactive Messaging Kafka extension
- Updating application.properties to use Kafka connector
- Or using SmallRye Reactive Messaging AMQP for JMS-like semantics

### Session Management
The original CartEndpoint uses `@SessionScoped` for per-user cart management. Quarkus supports this, but consider:
- Using `@ApplicationScoped` with cart management via database or distributed cache
- Implementing proper session management if running multiple instances
- Using Quarkus session extension if sticky sessions needed

### Stateful to Stateless Migration
The ShoppingCartService migration from `@Stateful` to `@ApplicationScoped` requires careful handling of cart state. Current implementation uses instance variable which won't work for multiple users. Recommend:
- Store carts in database with cartId as key
- Use request/session scoped bean for cart holder
- Or implement distributed cache (Redis/Infinispan) for cart state

### Transaction Boundaries
All EntityManager operations now require explicit `@Transactional` annotation. Verify transaction boundaries are correct for:
- Batch operations
- Cross-service calls
- Message processing methods

### Keycloak Integration
The application includes Keycloak configuration (realm-export.json, keycloak.json). Quarkus has native Keycloak/OIDC support via `quarkus-oidc` extension. This migration maintains the frontend integration but may need:
- Adding `quarkus-oidc` extension to pom.xml
- Moving keycloak.json configuration to application.properties
- Updating OIDC endpoints if using Quarkus native security

### Logging Changes
Migration from java.util.logging to JBoss Logging. All Logger injections work but consider:
- Using SLF4J facade for compatibility
- Updating log configurations in application.properties
- Adding structured logging with Quarkus logging extensions

### Native Compilation Considerations
For native builds (`mvn clean package -Pnative`):
- Reflection configuration may be needed for dynamic features
- Flyway native support included in Quarkus
- Test thoroughly before production native deployment
