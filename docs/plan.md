# Migration Plan

## Goal
Migrate the CoolStore monolith application from Java EE 7 (JBoss EAP 7.4) to Quarkus 3.

## Source → Target
Java EE 7 (JBoss EAP 7.4) → Quarkus 3.x

## Scope
- Files affected: 45
- Estimated complexity: High
- Hardest areas:
  1. JMS Message-Driven Beans to Reactive Messaging (@MessageDriven → @Incoming/@Outgoing with Emitters)
  2. JNDI lookups to CDI injection (InitialContext and lookup() replacements)
  3. Stateful EJB session management to CDI scopes

## Key Decisions Applied
1. **Stateful EJB Scope**: Converting ShoppingCartService from @Stateful to @SessionScoped requires the quarkus-undertow extension for HTTP session support. However, since this is a REST API with SessionScoped endpoints, we'll use @SessionScoped with the assumption that session management is handled via HTTP sessions.

2. **JMS to Reactive Messaging**: The application uses JMS Topics for order processing. We'll migrate to MicroProfile Reactive Messaging with in-memory channels for the migration, as Quarkus uses SmallRye Reactive Messaging. For production, this can be backed by Kafka or AMQP, but initial migration will use in-memory connectors.

3. **Remote EJB Interface**: ShippingServiceRemote.java appears to be a remote EJB interface that is not supported in Quarkus. Since no remote clients are evident in the codebase, we'll remove this interface and keep only the implementation.

4. **Flyway Integration**: The application uses Flyway 4.x for database migrations. We'll update to use Quarkus Flyway extension with a compatible version.

5. **Session State Management**: The @Stateful ShoppingCartService maintains state. In Quarkus, this will be converted to @SessionScoped to maintain per-user cart state.

## Approach

### Phase 1: Build Configuration
Update Maven POM to use Quarkus BOM, plugins, and dependencies. Change packaging from WAR to JAR.

### Phase 2: Configuration Files
Migrate persistence.xml to application.properties, remove beans.xml and web.xml, create Quarkus-specific configuration.

### Phase 3: Persistence Layer
Update EntityManager injection and producer patterns to use Quarkus CDI.

### Phase 4: Data Models
Update JPA entities for Hibernate 6 compatibility (sequence generation strategy).

### Phase 5: Core Services
Convert EJB annotations (@Stateless, @Stateful) to CDI scopes (@ApplicationScoped, @SessionScoped) and add @Transactional annotations.

### Phase 6: Messaging Layer
Convert JMS Message-Driven Beans to Reactive Messaging with @Incoming, and JMS Topic producers to use Emitters with @Outgoing/@Channel.

### Phase 7: REST API Layer
Update JAX-RS application configuration and REST endpoints for Quarkus compatibility.

### Phase 8: Utilities and Startup
Update utility classes, remove @Produces where necessary, and convert startup hooks to Quarkus lifecycle events.

### Phase 9: Cleanup
Remove obsolete files (web.xml, beans.xml, persistence.xml, remote EJB interfaces).

## Steps

### Step 1: Update POM packaging
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Change `<packaging>war</packaging>` to `<packaging>jar</packaging>`
- Why: Quarkus applications use JAR packaging instead of WAR
- Depends on: none
- Verify: `grep "<packaging>jar</packaging>" pom.xml` shows the change

### Step 2: Add Quarkus properties to POM
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add Quarkus platform properties in the `<properties>` section:
  ```xml
  <quarkus.platform.artifact-id>quarkus-bom</quarkus.platform.artifact-id>
  <quarkus.platform.group-id>io.quarkus.platform</quarkus.platform.group-id>
  <quarkus.platform.version>3.1.0.Final</quarkus.platform.version>
  <compiler-plugin.version>3.11.0</compiler-plugin.version>
  <surefire-plugin.version>3.0.0</surefire-plugin.version>
  ```
- Why: Define Quarkus BOM version and plugin versions
- Depends on: Step 1
- Verify: `grep "quarkus.platform.version" pom.xml` shows the property

### Step 3: Add Quarkus BOM to POM
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add `<dependencyManagement>` section with Quarkus BOM:
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
- Why: Import Quarkus BOM for dependency management
- Depends on: Step 2
- Verify: `grep "quarkus-bom" pom.xml` shows the BOM

### Step 4: Replace Java EE dependencies with Quarkus extensions
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Remove Java EE dependencies and add Quarkus extensions:
  - Remove: javaee-web-api, javaee-api, jboss-jms-api_2.0_spec, jboss-rmi-api_1.0_spec
  - Add:
    ```xml
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-resteasy-reactive-jackson</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-hibernate-orm</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-jdbc-postgresql</artifactId>
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
      <artifactId>quarkus-undertow</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-flyway</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-arc</artifactId>
    </dependency>
    ```
  - Update Flyway to version compatible with Quarkus (managed by BOM)
- Why: Replace Java EE APIs with Quarkus extensions
- Depends on: Step 3
- Verify: `grep "quarkus-hibernate-orm" pom.xml` shows Quarkus dependencies

### Step 5: Update Maven compiler plugin
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Update maven-compiler-plugin configuration:
  ```xml
  <plugin>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>${compiler-plugin.version}</version>
    <configuration>
      <parameters>true</parameters>
      <source>11</source>
      <target>11</target>
    </configuration>
  </plugin>
  ```
- Why: Quarkus 3 requires Java 11+ and parameter names for JAX-RS
- Depends on: Step 2
- Verify: `grep "<parameters>true</parameters>" pom.xml` shows the change

### Step 6: Add Quarkus Maven plugin
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add Quarkus Maven plugin to `<build><plugins>`:
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
- Why: Required to build Quarkus applications
- Depends on: Step 2
- Verify: `grep "quarkus-maven-plugin" pom.xml` shows the plugin

### Step 7: Update Maven Surefire plugin
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add or update maven-surefire-plugin:
  ```xml
  <plugin>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>${surefire-plugin.version}</version>
    <configuration>
      <systemPropertyVariables>
        <java.util.logging.manager>org.jboss.logmanager.LogManager</java.util.logging.manager>
        <maven.home>${maven.home}</maven.home>
      </systemPropertyVariables>
    </configuration>
  </plugin>
  ```
- Why: Configure Surefire for Quarkus testing
- Depends on: Step 2
- Verify: `grep "maven-surefire-plugin" pom.xml` shows the plugin

### Step 8: Add Maven Failsafe plugin
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add maven-failsafe-plugin for integration tests:
  ```xml
  <plugin>
    <artifactId>maven-failsafe-plugin</artifactId>
    <version>${surefire-plugin.version}</version>
    <executions>
      <execution>
        <goals>
          <goal>integration-test</goal>
          <goal>verify</goal>
        </goals>
        <configuration>
          <systemPropertyVariables>
            <native.image.path>${project.build.directory}/${project.build.finalName}-runner</native.image.path>
            <java.util.logging.manager>org.jboss.logmanager.LogManager</java.util.logging.manager>
            <maven.home>${maven.home}</maven.home>
          </systemPropertyVariables>
        </configuration>
      </execution>
    </executions>
  </plugin>
  ```
- Why: Support integration testing in Quarkus
- Depends on: Step 2
- Verify: `grep "maven-failsafe-plugin" pom.xml` shows the plugin

### Step 9: Remove Maven WAR plugin
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Remove the maven-war-plugin configuration
- Why: No longer needed with JAR packaging
- Depends on: Step 1
- Verify: `grep -c "maven-war-plugin" pom.xml` returns 0

### Step 10: Add native build profile
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add native profile in `<profiles>` section:
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
- Why: Enable native compilation capability
- Depends on: Step 6
- Verify: `grep "<id>native</id>" pom.xml` shows the profile

### Step 11: Create Quarkus application.properties
- Phase: Configuration Files
- File: src/main/resources/application.properties
- Action: CREATE
- What to do: Create application.properties with datasource and Hibernate configuration:
  ```properties
  # Datasource configuration
  quarkus.datasource.db-kind=postgresql
  quarkus.datasource.username=postgresUser
  quarkus.datasource.password=postgresPW
  quarkus.datasource.jdbc.url=jdbc:postgresql://127.0.0.1:5432/postgresDB
  
  # Hibernate configuration
  quarkus.hibernate-orm.database.generation=none
  quarkus.hibernate-orm.log.sql=false
  quarkus.hibernate-orm.log.format-sql=true
  quarkus.hibernate-orm.jdbc.statement-batch-size=0
  
  # Flyway migration
  quarkus.flyway.migrate-at-start=true
  quarkus.flyway.locations=classpath:db/migration
  
  # JAX-RS path
  quarkus.resteasy-reactive.path=/services
  
  # Reactive Messaging - In-memory connector for orders topic
  mp.messaging.outgoing.orders.connector=smallrye-in-memory
  mp.messaging.incoming.orders.connector=smallrye-in-memory
  
  # HTTP session for stateful beans
  quarkus.servlet.context-path=/
  ```
- Why: Quarkus uses application.properties instead of XML configuration files
- Depends on: none
- Verify: File exists with datasource configuration

### Step 12: Update persistence Resources class
- Phase: Persistence Layer
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do: Replace @PersistenceContext with @Inject and remove @Produces:
  - BEFORE:
    ```java
    @Dependent
    public class Resources {
        @PersistenceContext
        private EntityManager em;
        
        @Produces
        public EntityManager getEntityManager() {
            return em;
        }
    }
    ```
  - AFTER:
    ```java
    @Dependent
    public class Resources {
        @Inject
        EntityManager em;
        
        public EntityManager getEntityManager() {
            return em;
        }
    }
    ```
- Why: Quarkus uses @Inject for EntityManager injection, @Produces is not needed
- Depends on: Step 11
- Verify: `grep "@Inject" src/main/java/com/redhat/coolstore/persistence/Resources.java` shows the change

### Step 13: Update Order entity for Hibernate 6
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/Order.java
- Action: MODIFY
- What to do: Update @GeneratedValue to specify sequence name:
  - Change from: `@GeneratedValue`
  - Change to: `@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_seq")`
  - Add: `@SequenceGenerator(name = "order_seq", sequenceName = "order_seq", allocationSize = 1)`
- Why: Hibernate 6 changed implicit sequence naming - must be explicit
- Depends on: none
- Verify: `grep "order_seq" src/main/java/com/redhat/coolstore/model/Order.java` shows the sequence

### Step 14: Update OrderItem entity for Hibernate 6
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/OrderItem.java
- Action: MODIFY
- What to do: Update @GeneratedValue to specify sequence name:
  - Change from: `@GeneratedValue`
  - Change to: `@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "orderitem_seq")`
  - Add: `@SequenceGenerator(name = "orderitem_seq", sequenceName = "orderitem_seq", allocationSize = 1)`
- Why: Hibernate 6 changed implicit sequence naming - must be explicit
- Depends on: none
- Verify: `grep "orderitem_seq" src/main/java/com/redhat/coolstore/model/OrderItem.java` shows the sequence

### Step 15: Update CatalogService - EJB to CDI
- Phase: Core Services
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do:
  - Replace: `@Stateless` with `@ApplicationScoped`
  - Add: `@Transactional` annotation at class level
  - Add imports: `import jakarta.enterprise.context.ApplicationScoped;` and `import jakarta.transaction.Transactional;`
  - Update all `javax.*` imports to `jakarta.*`
- Why: Quarkus uses CDI instead of EJB, and requires explicit transaction management
- Depends on: Step 12
- Verify: `grep "@ApplicationScoped" src/main/java/com/redhat/coolstore/service/CatalogService.java` shows the change

### Step 16: Update OrderService - EJB to CDI
- Phase: Core Services
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do:
  - Replace: `@Stateless` with `@ApplicationScoped`
  - Add: `@Transactional` annotation at class level
  - Add imports: `import jakarta.enterprise.context.ApplicationScoped;` and `import jakarta.transaction.Transactional;`
  - Update all `javax.*` imports to `jakarta.*`
- Why: Quarkus uses CDI instead of EJB, and requires explicit transaction management
- Depends on: Step 12
- Verify: `grep "@ApplicationScoped" src/main/java/com/redhat/coolstore/service/OrderService.java` shows the change

### Step 17: Update ProductService - EJB to CDI
- Phase: Core Services
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do:
  - Replace: `@Stateless` with `@ApplicationScoped`
  - Add: `@Transactional` annotation at class level
  - Add imports: `import jakarta.enterprise.context.ApplicationScoped;` and `import jakarta.transaction.Transactional;`
  - Update all `javax.*` imports to `jakarta.*`
- Why: Quarkus uses CDI instead of EJB, and requires explicit transaction management
- Depends on: Step 12
- Verify: `grep "@ApplicationScoped" src/main/java/com/redhat/coolstore/service/ProductService.java` shows the change

### Step 18: Update ShippingService - EJB to CDI
- Phase: Core Services
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do:
  - Replace: `@Stateless` with `@ApplicationScoped`
  - Add: `@Transactional` annotation for methods that need it
  - Add imports: `import jakarta.enterprise.context.ApplicationScoped;` and `import jakarta.transaction.Transactional;`
  - Update all `javax.*` imports to `jakarta.*`
- Why: Quarkus uses CDI instead of EJB, and requires explicit transaction management
- Depends on: Step 12
- Verify: `grep "@ApplicationScoped" src/main/java/com/redhat/coolstore/service/ShippingService.java` shows the change

### Step 19: COMPLEX - Update ShoppingCartService - Stateful EJB to SessionScoped CDI
- Phase: Core Services
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - BEFORE: Uses @Stateful EJB with JNDI lookups
  - AFTER: Use @SessionScoped CDI bean with @Inject
  - Specific changes:
    1. Replace: `@Stateful` with `@SessionScoped`
    2. Add: `@Transactional` annotation for methods that modify data
    3. Remove: All JNDI lookup code (InitialContext and lookup() calls around line 119-121)
    4. Update: Inject ShoppingCartOrderProcessor directly (already present)
    5. Add imports: `import jakarta.enterprise.context.SessionScoped;`, `import jakarta.transaction.Transactional;`
    6. Update all `javax.*` imports to `jakarta.*`
    7. Remove imports: `javax.naming.Context`, `javax.naming.InitialContext`, `javax.naming.NamingException`, `java.util.Hashtable`
- Why: @Stateful EJBs are not supported in Quarkus. SessionScoped maintains per-user state. JNDI is not supported, use CDI injection instead.
- Depends on: Step 12
- Verify: `grep "@SessionScoped" src/main/java/com/redhat/coolstore/service/ShoppingCartService.java && ! grep "InitialContext" src/main/java/com/redhat/coolstore/service/ShoppingCartService.java`

### Step 20: Update PromoService - add CDI scope
- Phase: Core Services
- File: src/main/java/com/redhat/coolstore/service/PromoService.java
- Action: MODIFY
- What to do:
  - Add: `@ApplicationScoped` annotation
  - Add import: `import jakarta.enterprise.context.ApplicationScoped;`
  - Update all `javax.*` imports to `jakarta.*`
- Why: Ensure CDI bean discovery and proper scope
- Depends on: none
- Verify: `grep "@ApplicationScoped" src/main/java/com/redhat/coolstore/service/PromoService.java` shows the change

### Step 21: COMPLEX - Update ShoppingCartOrderProcessor - JMS Topic to Emitter
- Phase: Messaging Layer
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - BEFORE: Uses @Stateless with JMS Topic injection and JMSContext
    ```java
    @Stateless
    public class ShoppingCartOrderProcessor {
        @Inject
        private transient JMSContext context;
        
        @Resource(lookup = "java:/topic/orders")
        private Topic ordersTopic;
        
        public void process(ShoppingCart cart) {
            context.createProducer().send(ordersTopic, Transformers.shoppingCartToJson(cart));
        }
    }
    ```
  - AFTER: Use @ApplicationScoped with Reactive Messaging Emitter
    ```java
    @ApplicationScoped
    public class ShoppingCartOrderProcessor {
        @Inject
        Logger log;
        
        @Channel("orders")
        Emitter<String> ordersEmitter;
        
        public void process(ShoppingCart cart) {
            log.info("Sending order from processor");
            ordersEmitter.send(Transformers.shoppingCartToJson(cart));
        }
    }
    ```
  - Specific changes:
    1. Replace: `@Stateless` with `@ApplicationScoped`
    2. Remove: `@Inject private transient JMSContext context;`
    3. Remove: `@Resource(lookup = "java:/topic/orders") private Topic ordersTopic;`
    4. Add: `@Inject @Channel("orders") Emitter<String> ordersEmitter;`
    5. Replace: `context.createProducer().send(ordersTopic, ...)` with `ordersEmitter.send(...)`
    6. Update imports: Remove JMS imports, add `import org.eclipse.microprofile.reactive.messaging.Channel;`, `import org.eclipse.microprofile.reactive.messaging.Emitter;`, `import jakarta.enterprise.context.ApplicationScoped;`
    7. Update all `javax.*` imports to `jakarta.*`
- Why: JMS is not supported in Quarkus. Use Reactive Messaging with Emitter to publish messages.
- Depends on: Step 11
- Verify: `grep "@Channel" src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java && grep "Emitter<String>" src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java`

### Step 22: COMPLEX - Update OrderServiceMDB - MessageDriven to Reactive Messaging
- Phase: Messaging Layer
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: Uses @MessageDriven with MessageListener
    ```java
    @MessageDriven(name = "OrderServiceMDB", activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "topic/orders"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
        @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge")})
    public class OrderServiceMDB implements MessageListener {
        @Override
        public void onMessage(Message rcvMessage) {
            // Process message
        }
    }
    ```
  - AFTER: Use @ApplicationScoped with @Incoming
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
  - Specific changes:
    1. Remove: `@MessageDriven` annotation and all `@ActivationConfigProperty`
    2. Remove: `implements MessageListener`
    3. Add: `@ApplicationScoped` at class level
    4. Replace: `public void onMessage(Message rcvMessage)` with `public void onMessage(String orderStr)`
    5. Add: `@Incoming("orders")` annotation on the onMessage method
    6. Add: `@Transactional` annotation on the onMessage method
    7. Remove: All JMS message unwrapping code (TextMessage, msg.getBody, try-catch for JMSException)
    8. Update: Method body to directly use the String parameter
    9. Update imports: Remove JMS imports (`javax.ejb.*`, `javax.jms.*`), add `import org.eclipse.microprofile.reactive.messaging.Incoming;`, `import jakarta.enterprise.context.ApplicationScoped;`, `import jakarta.transaction.Transactional;`
    10. Update all remaining `javax.*` imports to `jakarta.*`
- Why: @MessageDriven EJBs are not supported in Quarkus. Use Reactive Messaging @Incoming to consume messages.
- Depends on: Step 11, Step 16
- Verify: `grep "@Incoming" src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java && ! grep "@MessageDriven" src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java`

### Step 23: COMPLEX - Update InventoryNotificationMDB - Remove or refactor JNDI/JMS code
- Phase: Messaging Layer
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: Uses MessageListener with JNDI lookups for WebLogic
  - AFTER: Convert to @ApplicationScoped with @Incoming if this component is active, or mark as deprecated
  - Specific changes:
    1. Add: `@ApplicationScoped` annotation
    2. Remove: All JNDI-related code (InitialContext, lookup(), WebLogic-specific code)
    3. Update: If this is meant to listen to orders topic, add `@Incoming("orders")` annotation
    4. Replace: `public void onMessage(Message rcvMessage)` with `public void onMessage(String orderStr)`
    5. Add: `@Transactional` if needed
    6. Remove: `implements MessageListener`
    7. Remove: All connection setup code (JNDI_FACTORY, JMS_FACTORY, TopicConnection, etc.)
    8. Update imports: Remove JMS and JNDI imports, add Reactive Messaging imports
    9. Update all `javax.*` imports to `jakarta.*`
  - NOTE: This class appears to be WebLogic-specific legacy code. May need to determine if it's still needed.
- Why: JNDI and JMS are not supported in Quarkus. Convert to Reactive Messaging or remove if obsolete.
- Depends on: Step 11
- Verify: `grep "@ApplicationScoped" src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java && ! grep "InitialContext" src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java`

### Step 24: Update DataBaseMigrationStartup - Singleton to ApplicationScoped
- Phase: Utilities and Startup
- File: src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- Action: MODIFY
- What to do:
  - Replace: Any EJB annotations with `@ApplicationScoped`
  - Add: `@Transactional` annotation for methods that access the database
  - Update: Startup hook to use Quarkus lifecycle events if using @Startup/@PostConstruct
  - Add imports: `import jakarta.enterprise.context.ApplicationScoped;`, `import jakarta.transaction.Transactional;`
  - Update all `javax.*` imports to `jakarta.*`
- Why: EJB Singleton not supported in Quarkus, use CDI with proper transaction management
- Depends on: Step 12
- Verify: `grep "@ApplicationScoped" src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java` shows the change

### Step 25: Update Producers utility class
- Phase: Utilities and Startup
- File: src/main/java/com/redhat/coolstore/utils/Producers.java
- Action: MODIFY
- What to do:
  - Review @Produces annotations - in Quarkus, some may need to be simplified or removed
  - Add: `@ApplicationScoped` or `@Dependent` scope annotation if not present
  - Update: If producing Logger, can be simplified for Quarkus
  - Update all `javax.*` imports to `jakarta.*`
- Why: Ensure CDI producer compatibility with Quarkus
- Depends on: none
- Verify: Producers still work with proper CDI scopes

### Step 26: Update StartupListener
- Phase: Utilities and Startup
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do:
  - If using ServletContextListener, convert to Quarkus lifecycle event
  - Add: `@ApplicationScoped` and use `@Observes StartupEvent` if needed
  - Update all `javax.*` imports to `jakarta.*`
- Why: Align with Quarkus application lifecycle
- Depends on: none
- Verify: `grep "jakarta" src/main/java/com/redhat/coolstore/utils/StartupListener.java` shows Jakarta imports

### Step 27: Update Transformers utility
- Phase: Utilities and Startup
- File: src/main/java/com/redhat/coolstore/utils/Transformers.java
- Action: MODIFY
- What to do:
  - Update all `javax.*` imports to `jakarta.*` (especially javax.json, javax.persistence)
  - Ensure class has proper CDI scope if injected, or is utility class with static methods
- Why: Jakarta namespace migration
- Depends on: none
- Verify: `grep "jakarta" src/main/java/com/redhat/coolstore/utils/Transformers.java` shows Jakarta imports if needed

### Step 28: Update CatalogItemEntity model
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java
- Action: MODIFY
- What to do:
  - Update all `javax.persistence.*` imports to `jakarta.persistence.*`
- Why: Jakarta namespace migration for JPA
- Depends on: none
- Verify: `grep "jakarta.persistence" src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java` shows Jakarta imports

### Step 29: Update InventoryEntity model
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/InventoryEntity.java
- Action: MODIFY
- What to do:
  - Update all `javax.persistence.*` imports to `jakarta.persistence.*`
- Why: Jakarta namespace migration for JPA
- Depends on: none
- Verify: `grep "jakarta.persistence" src/main/java/com/redhat/coolstore/model/InventoryEntity.java` shows Jakarta imports

### Step 30: Update Product model
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/Product.java
- Action: MODIFY
- What to do:
  - Update all `javax.*` imports to `jakarta.*`
- Why: Jakarta namespace migration
- Depends on: none
- Verify: `grep "jakarta" src/main/java/com/redhat/coolstore/model/Product.java` shows Jakarta imports if any javax imports exist

### Step 31: Update Promotion model
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/Promotion.java
- Action: MODIFY
- What to do:
  - Update all `javax.*` imports to `jakarta.*`
- Why: Jakarta namespace migration
- Depends on: none
- Verify: `grep "jakarta" src/main/java/com/redhat/coolstore/model/Promotion.java` shows Jakarta imports if any javax imports exist

### Step 32: Update ShoppingCart model
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/ShoppingCart.java
- Action: MODIFY
- What to do:
  - Update all `javax.*` imports to `jakarta.*`
- Why: Jakarta namespace migration
- Depends on: none
- Verify: `grep "jakarta" src/main/java/com/redhat/coolstore/model/ShoppingCart.java` shows Jakarta imports if any javax imports exist

### Step 33: Update ShoppingCartItem model
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java
- Action: MODIFY
- What to do:
  - Update all `javax.*` imports to `jakarta.*`
- Why: Jakarta namespace migration
- Depends on: none
- Verify: `grep "jakarta" src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java` shows Jakarta imports if any javax imports exist

### Step 34: Update CartEndpoint REST API
- Phase: REST API Layer
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do:
  - Update all `javax.ws.rs.*` imports to `jakarta.ws.rs.*`
  - Update all `javax.enterprise.*` imports to `jakarta.enterprise.*`
  - Update all `javax.inject.*` imports to `jakarta.inject.*`
- Why: Jakarta namespace migration for JAX-RS and CDI
- Depends on: Step 19
- Verify: `grep "jakarta.ws.rs" src/main/java/com/redhat/coolstore/rest/CartEndpoint.java` shows Jakarta imports

### Step 35: Update OrderEndpoint REST API
- Phase: REST API Layer
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do:
  - Update all `javax.ws.rs.*` imports to `jakarta.ws.rs.*`
  - Update all `javax.inject.*` imports to `jakarta.inject.*`
- Why: Jakarta namespace migration for JAX-RS
- Depends on: Step 16
- Verify: `grep "jakarta.ws.rs" src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java` shows Jakarta imports

### Step 36: Update ProductEndpoint REST API
- Phase: REST API Layer
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do:
  - Update all `javax.ws.rs.*` imports to `jakarta.ws.rs.*`
  - Update all `javax.inject.*` imports to `jakarta.inject.*`
- Why: Jakarta namespace migration for JAX-RS
- Depends on: Step 17
- Verify: `grep "jakarta.ws.rs" src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java` shows Jakarta imports

### Step 37: Update or simplify RestApplication
- Phase: REST API Layer
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: MODIFY
- What to do:
  - Update: `javax.ws.rs.*` imports to `jakarta.ws.rs.*`
  - Note: The @ApplicationPath("/services") is still valid in Quarkus, or it can be removed in favor of quarkus.resteasy-reactive.path in application.properties (already set in Step 11)
  - Decision: Keep the class but update imports, as it's harmless and documents the API path
- Why: Jakarta namespace migration, JAX-RS application class still works in Quarkus
- Depends on: Step 11
- Verify: `grep "jakarta.ws.rs" src/main/java/com/redhat/coolstore/rest/RestApplication.java` shows Jakarta imports

### Step 38: Remove ShippingServiceRemote interface
- Phase: Cleanup
- File: src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java
- Action: DELETE
- What to do: Delete this file - remote EJB interfaces are not supported in Quarkus
- Why: Remote EJBs are not supported in Quarkus, and no remote clients are evident
- Depends on: Step 18
- Verify: File no longer exists

### Step 39: Remove beans.xml
- Phase: Cleanup
- File: src/main/webapp/WEB-INF/beans.xml
- Action: DELETE
- What to do: Delete this file - Quarkus uses annotation-based CDI discovery by default
- Why: beans.xml content is ignored in Quarkus, CDI beans are discovered automatically
- Depends on: All service migrations complete
- Verify: File no longer exists

### Step 40: Remove web.xml
- Phase: Cleanup
- File: src/main/webapp/WEB-INF/web.xml
- Action: DELETE
- What to do: Delete this file - not needed for Quarkus JAR packaging
- Why: Quarkus does not use web.xml, configuration is in application.properties
- Depends on: Step 1
- Verify: File no longer exists

### Step 41: Remove persistence.xml
- Phase: Cleanup
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Delete this file - configuration moved to application.properties
- Why: Quarkus uses application.properties for persistence configuration
- Depends on: Step 11
- Verify: File no longer exists

### Step 42: Update weblogic ApplicationLifecycleListener
- Phase: Utilities and Startup
- File: weblogic/application/ApplicationLifecycleListener.java
- Action: MODIFY
- What to do:
  - This appears to be WebLogic-specific code - evaluate if still needed
  - If needed: Update to Quarkus lifecycle events using `@Observes StartupEvent` and `@Observes ShutdownEvent`
  - Update all `javax.*` imports to `jakarta.*`
  - Add: `@ApplicationScoped` if converting to CDI bean
  - If not needed: Mark for deletion or disable
- Why: WebLogic-specific APIs not available in Quarkus
- Depends on: none
- Verify: WebLogic imports removed or class disabled

### Step 43: Update weblogic ApplicationLifecycleEvent
- Phase: Utilities and Startup
- File: weblogic/application/ApplicationLifecycleEvent.java
- Action: MODIFY
- What to do:
  - This is WebLogic-specific - likely needs to be removed or refactored
  - If used by ApplicationLifecycleListener, update to use Quarkus lifecycle events
  - Update all `javax.*` imports to `jakarta.*` if keeping
- Why: WebLogic-specific APIs not available in Quarkus
- Depends on: Step 42
- Verify: WebLogic APIs removed or class disabled

### Step 44: Update or remove weblogic NonCatalogLogger
- Phase: Utilities and Startup
- File: weblogic/i18n/logging/NonCatalogLogger.java
- Action: MODIFY
- What to do:
  - This is WebLogic-specific logging - replace with standard Jakarta or JBoss logging
  - Update to use `java.util.logging.Logger` or JBoss Logging
  - Update all `javax.*` imports to `jakarta.*`
- Why: WebLogic-specific logging APIs not available in Quarkus
- Depends on: none
- Verify: WebLogic logging APIs removed

### Step 45: Verify and update finalName in POM
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do:
  - Update `<finalName>ROOT</finalName>` to `<finalName>coolstore-monolith</finalName>` or similar
  - Reason: ROOT was for WAR deployment context, with JAR packaging a descriptive name is better
- Why: Better naming convention for Quarkus JAR artifacts
- Depends on: Step 1
- Verify: `grep "<finalName>coolstore-monolith</finalName>" pom.xml` shows the change

## Verification

- Build: `mvn clean package`
  - Should complete without errors
  - Should produce a JAR file in target/ (not WAR)
  - Should see Quarkus build output

- Test: `mvn test`
  - Run any existing tests (currently skipped with maven.test.skip=true, may need to update this property)

- Blackbox: 
  1. Start PostgreSQL database:
     ```
     podman run --name myPostgresDb -p 5432:5432 \
       -e POSTGRES_USER=postgresUser \
       -e POSTGRES_PASSWORD=postgresPW \
       -e POSTGRES_DB=postgresDB \
       -d postgres
     ```
  
  2. Run the application:
     ```
     mvn quarkus:dev
     ```
  
  3. Access the application at http://localhost:8080
  
  4. Verify key business flows:
     - Browse products (GET /services/products)
     - Add items to cart (POST /services/cart/{cartId}/{itemId}/{quantity})
     - View cart (GET /services/cart/{cartId})
     - Checkout cart (POST /services/cart/checkout/{cartId})
     - Verify order is saved (GET /services/orders)
     - Check logs to confirm message processing (order topic publishing and consumption)
  
  5. Verify reactive messaging:
     - After checkout, check console logs for "Message recd !" from OrderServiceMDB
     - Verify order is persisted in database
     - Verify inventory is updated

  6. Test session management:
     - Verify shopping cart maintains state across requests with same session

## Notes

### Critical Migration Considerations:

1. **Session State**: The @Stateful ShoppingCartService is migrated to @SessionScoped, which requires HTTP session support via quarkus-undertow extension. This maintains cart state per user session. Ensure session cookies are properly handled by the client.

2. **Message Ordering**: The in-memory connector for Reactive Messaging in the initial migration processes messages sequentially. For production, consider using Kafka or AMQP with proper ordering guarantees if required.

3. **Database Sequences**: Hibernate 6 sequence naming changes mean the database must have sequences named `order_seq` and `orderitem_seq`. The Flyway migrations (V1_1__CreateSchema.sql) may need updating to create these sequences explicitly.

4. **WebLogic-specific Code**: The `weblogic` package contains WebLogic-specific code that may not be needed in Quarkus. The ApplicationLifecycleListener and NonCatalogLogger should be reviewed and either removed or refactored to use Quarkus equivalents.

5. **InventoryNotificationMDB**: This class appears to be legacy WebLogic code with JNDI lookups. It may need significant refactoring or removal depending on whether this functionality is still required.

6. **Remote EJB**: ShippingServiceRemote.java is a remote EJB interface. Since Quarkus doesn't support remote EJBs and no remote clients are evident, this interface is removed. If remote access is needed, consider REST API or gRPC.

7. **Transaction Management**: All service classes that were EJBs now need explicit @Transactional annotations. This is added at the class level for most services, but specific methods can be annotated if fine-grained control is needed.

8. **Flyway Version**: The application uses Flyway 4.1.2, but Quarkus 3 supports newer versions. The quarkus-flyway extension will use a compatible version managed by the BOM.

9. **REST Path**: The JAX-RS application path `/services` is maintained via application.properties setting `quarkus.resteasy-reactive.path=/services` for consistency with the original application.

10. **Testing**: The original POM has `maven.test.skip=true`. After migration, this should be reviewed and tests should be enabled/created to verify functionality.

### Post-Migration Recommendations:

1. **Native Compilation**: Test native compilation with `mvn package -Pnative` after JVM mode is working
2. **Performance Testing**: Compare startup time and memory usage between Java EE and Quarkus versions
3. **Health Checks**: Add Quarkus health check endpoints with `quarkus-smallrye-health` extension
4. **Metrics**: Add metrics with `quarkus-micrometer` extension
5. **Production Messaging**: Replace in-memory connector with Kafka or AMQP for production deployments
6. **Security**: Migrate Keycloak integration to use quarkus-oidc extension
7. **Database Migration Review**: Review and potentially update Flyway migration scripts for Hibernate 6 sequence requirements
