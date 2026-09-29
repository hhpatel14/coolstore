# Migration Plan

## Goal
Migrate the CoolStore monolith from Java EE 7 to Quarkus 3

## Source → Target
Java EE 7 (JBoss EAP 7.4, WAR packaging) → Quarkus 3 (JAR packaging)

## Scope
- Files affected: 29
- Estimated complexity: High
- Hardest areas:
  1. JMS message-driven beans (MDBs) to Reactive Messaging with SmallRye
  2. JNDI lookups and WebLogic-specific code in InventoryNotificationMDB
  3. Stateful shopping cart service session management

## Key Decisions Applied

1. **Reactive Messaging Channel Names**: Using "orders" as the channel name for the Reactive Messaging topic to maintain semantic alignment with the original JMS topic "topic/orders"

2. **InventoryNotificationMDB Handling**: This MDB uses WebLogic-specific JNDI context and manual JMS setup. Decision is to convert it to a Reactive Messaging @Incoming listener like OrderServiceMDB, removing all JNDI and manual connection management

3. **ShoppingCartService State Management**: Converting from @Stateful EJB to @ApplicationScoped. Session state will need to be managed at a higher level (likely in the REST layer or through user context) rather than relying on EJB stateful session beans

4. **Database Configuration**: Using Quarkus datasource properties with the existing PostgreSQL database, maintaining the same database name "postgresDB" and connection parameters

5. **SmallRye Reactive Messaging Implementation**: Using in-memory connector for development/testing since the original application uses JMS topics for inter-component messaging within the same monolith

## Approach

**Phase 1: Build Configuration** - Convert Maven pom.xml from Java EE WAR to Quarkus JAR packaging, add Quarkus BOM, plugins, and required extensions (RESTEasy Reactive, Hibernate ORM with Panache, SmallRye Reactive Messaging, PostgreSQL driver)

**Phase 2: Persistence Layer** - Migrate JPA configuration from persistence.xml to application.properties, update EntityManager injection pattern, remove the Resources producer class

**Phase 3: Data Models** - No structural changes required for JPA entities, but verify annotations are compatible with Quarkus

**Phase 4: Service Layer** - Replace EJB annotations (@Stateless, @Stateful, @MessageDriven) with CDI scopes, add @Transactional to methods with persistence operations, convert JMS MDBs to Reactive Messaging @Incoming listeners, replace JMS Topic publishing with Reactive Messaging @Channel Emitters

**Phase 5: REST API Layer** - Remove JAX-RS ApplicationPath activation class (not needed in Quarkus), verify REST endpoints work with Quarkus RESTEasy Reactive

**Phase 6: Utilities and Configuration** - Update CDI producers, add @Transactional where needed, remove WebLogic-specific classes

**Phase 7: Cleanup** - Remove Java EE deployment descriptors (beans.xml, persistence.xml, web.xml) and WebLogic-specific source files

## Steps

### Step 1: Change packaging to JAR
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Change `<packaging>war</packaging>` to `<packaging>jar</packaging>`
- Why: Quarkus applications use JAR packaging, not WAR
- Depends on: none
- Verify: `grep '<packaging>jar</packaging>' pom.xml` shows the change

### Step 2: Add Quarkus BOM
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add Quarkus BOM to dependencyManagement section:
  ```xml
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>io.quarkus.platform</groupId>
        <artifactId>quarkus-bom</artifactId>
        <version>3.2.9.Final</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
  ```
- Why: Quarkus BOM manages versions of all Quarkus dependencies
- Depends on: Step 1
- Verify: `grep 'quarkus-bom' pom.xml` shows the BOM

### Step 3: Replace Java EE dependencies with Quarkus extensions
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Remove Java EE dependencies (javaee-web-api, javaee-api, jboss-jms-api, jboss-rmi-api) and add Quarkus extensions:
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
    <artifactId>quarkus-arc</artifactId>
  </dependency>
  <dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-narayana-jta</artifactId>
  </dependency>
  ```
  Keep flyway-core dependency but update to compatible version
- Why: Quarkus uses extensions instead of Java EE APIs
- Depends on: Step 2
- Verify: `grep 'quarkus-resteasy-reactive-jackson\|quarkus-hibernate-orm' pom.xml` shows extensions

### Step 4: Add Quarkus Maven plugin
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Replace maven-war-plugin with quarkus-maven-plugin:
  ```xml
  <plugin>
    <groupId>io.quarkus.platform</groupId>
    <artifactId>quarkus-maven-plugin</artifactId>
    <version>3.2.9.Final</version>
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
- Why: Quarkus Maven plugin handles Quarkus-specific build tasks
- Depends on: Step 3
- Verify: `grep 'quarkus-maven-plugin' pom.xml` shows the plugin

### Step 5: Update Maven Compiler plugin
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Update compiler plugin configuration to use Java 17 and add annotation processor paths:
  ```xml
  <plugin>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.11.0</version>
    <configuration>
      <release>17</release>
      <parameters>true</parameters>
    </configuration>
  </plugin>
  ```
- Why: Quarkus 3 requires Java 17 minimum, parameters flag enables better CDI support
- Depends on: Step 4
- Verify: `grep '<release>17</release>' pom.xml` shows Java 17

### Step 6: Add Maven Surefire plugin
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add or update Surefire plugin for Quarkus testing:
  ```xml
  <plugin>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.1.2</version>
    <configuration>
      <systemPropertyVariables>
        <java.util.logging.manager>org.jboss.logmanager.LogManager</java.util.logging.manager>
        <maven.home>${maven.home}</maven.home>
      </systemPropertyVariables>
    </configuration>
  </plugin>
  ```
- Why: Proper test execution with Quarkus log manager
- Depends on: Step 5
- Verify: `grep 'maven-surefire-plugin' pom.xml` shows the plugin

### Step 7: Add native build profile
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add native profile in profiles section:
  ```xml
  <profile>
    <id>native</id>
    <properties>
      <quarkus.package.type>native</quarkus.package.type>
    </properties>
  </profile>
  ```
- Why: Enables optional native compilation with GraalVM
- Depends on: Step 6
- Verify: `grep 'quarkus.package.type' pom.xml` shows native profile

### Step 8: Create Quarkus application.properties
- Phase: Persistence Layer
- File: src/main/resources/application.properties
- Action: CREATE
- What to do: Create application.properties with datasource and Hibernate configuration:
  ```properties
  # Database configuration
  quarkus.datasource.db-kind=postgresql
  quarkus.datasource.username=postgresUser
  quarkus.datasource.password=postgresPW
  quarkus.datasource.jdbc.url=jdbc:postgresql://127.0.0.1:5432/postgresDB
  
  # Hibernate configuration
  quarkus.hibernate-orm.database.generation=none
  quarkus.hibernate-orm.log.sql=false
  quarkus.hibernate-orm.log.format-sql=true
  
  # REST configuration
  quarkus.http.port=8080
  quarkus.resteasy-reactive.path=/services
  
  # Reactive Messaging configuration
  mp.messaging.incoming.orders.connector=smallrye-in-memory
  mp.messaging.outgoing.orders.connector=smallrye-in-memory
  ```
- Why: Quarkus uses application.properties instead of persistence.xml for configuration
- Depends on: Step 7
- Verify: File exists with datasource properties

### Step 9: Migrate CatalogItemEntity
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java
- Action: MODIFY
- What to do: Update imports from javax.persistence.* to jakarta.persistence.*, verify JPA annotations are compatible
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 8
- Verify: `grep 'jakarta.persistence' src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java`

### Step 10: Migrate InventoryEntity
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/InventoryEntity.java
- Action: MODIFY
- What to do: Update imports from javax.persistence.* to jakarta.persistence.*
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 8
- Verify: `grep 'jakarta.persistence' src/main/java/com/redhat/coolstore/model/InventoryEntity.java`

### Step 11: Migrate Order
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/Order.java
- Action: MODIFY
- What to do: Update imports from javax.persistence.* to jakarta.persistence.*
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 8
- Verify: `grep 'jakarta.persistence' src/main/java/com/redhat/coolstore/model/Order.java`

### Step 12: Migrate OrderItem
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/OrderItem.java
- Action: MODIFY
- What to do: Update imports from javax.persistence.* to jakarta.persistence.*
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 8
- Verify: `grep 'jakarta.persistence' src/main/java/com/redhat/coolstore/model/OrderItem.java`

### Step 13: Migrate Product
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/Product.java
- Action: MODIFY
- What to do: Update imports from javax.persistence.* to jakarta.persistence.* (if used)
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 8
- Verify: No javax imports remain

### Step 14: Migrate Promotion
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/Promotion.java
- Action: MODIFY
- What to do: Update imports from javax.persistence.* to jakarta.persistence.* (if used)
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 8
- Verify: No javax imports remain

### Step 15: Migrate ShoppingCart
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/ShoppingCart.java
- Action: MODIFY
- What to do: Update imports from javax.persistence.* to jakarta.persistence.* (if used)
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 8
- Verify: No javax imports remain

### Step 16: Migrate ShoppingCartItem
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java
- Action: MODIFY
- What to do: Update imports from javax.persistence.* to jakarta.persistence.* (if used)
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 8
- Verify: No javax imports remain

### Step 17: COMPLEX - Migrate CatalogService
- Phase: Service Layer
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do:
  - BEFORE: `@Stateless` EJB with `@PersistenceContext` EntityManager
  - AFTER: `@ApplicationScoped` CDI bean with `@Inject` EntityManager and `@Transactional` methods
  - Specific changes:
    1. Remove: `import javax.ejb.Stateless;`
    2. Add: `import jakarta.enterprise.context.ApplicationScoped;`, `import jakarta.transaction.Transactional;`
    3. Replace: `@Stateless` with `@ApplicationScoped`
    4. Update: All javax imports to jakarta
    5. Add: `@Transactional` annotation to methods that perform persistence operations (save, merge, etc.)
- Why: Quarkus does not support @Stateless EJBs; use CDI @ApplicationScoped instead. EntityManager operations require explicit @Transactional
- Depends on: Step 9, Step 10, Step 11, Step 12
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/CatalogService.java` and `grep '@Transactional'`

### Step 18: COMPLEX - Migrate ProductService
- Phase: Service Layer
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do:
  - BEFORE: `@Stateless` EJB
  - AFTER: `@ApplicationScoped` CDI bean with `@Transactional`
  - Specific changes:
    1. Remove: `import javax.ejb.Stateless;`
    2. Add: `import jakarta.enterprise.context.ApplicationScoped;`, `import jakarta.transaction.Transactional;`
    3. Replace: `@Stateless` with `@ApplicationScoped`
    4. Update: All javax imports to jakarta
    5. Add: `@Transactional` to methods with persistence operations
- Why: Quarkus does not support @Stateless EJBs
- Depends on: Step 13
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ProductService.java`

### Step 19: COMPLEX - Migrate OrderService
- Phase: Service Layer
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do:
  - BEFORE: `@Stateless` EJB with persistence operations
  - AFTER: `@ApplicationScoped` CDI bean with `@Transactional`
  - Specific changes:
    1. Remove: `import javax.ejb.Stateless;`
    2. Add: `import jakarta.enterprise.context.ApplicationScoped;`, `import jakarta.transaction.Transactional;`
    3. Replace: `@Stateless` with `@ApplicationScoped`
    4. Update: All javax imports to jakarta
    5. Add: `@Transactional` to save and other persistence methods
- Why: Quarkus does not support @Stateless EJBs; persistence operations require @Transactional
- Depends on: Step 11, Step 12
- Verify: `grep '@ApplicationScoped\|@Transactional' src/main/java/com/redhat/coolstore/service/OrderService.java`

### Step 20: COMPLEX - Migrate PromoService
- Phase: Service Layer
- File: src/main/java/com/redhat/coolstore/service/PromoService.java
- Action: MODIFY
- What to do:
  - BEFORE: May use @Stateless
  - AFTER: `@ApplicationScoped` CDI bean
  - Specific changes:
    1. Replace: `@Stateless` with `@ApplicationScoped` if present
    2. Update: All javax imports to jakarta
- Why: Consistent CDI usage across services
- Depends on: Step 14
- Verify: No @Stateless annotation remains

### Step 21: COMPLEX - Migrate ShippingService
- Phase: Service Layer
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do:
  - BEFORE: `@Stateless` EJB with `@Transactional` methods
  - AFTER: `@ApplicationScoped` CDI bean with `@Transactional`
  - Specific changes:
    1. Remove: `import javax.ejb.Stateless;`
    2. Add: `import jakarta.enterprise.context.ApplicationScoped;`, `import jakarta.transaction.Transactional;`
    3. Replace: `@Stateless` with `@ApplicationScoped`
    4. Update: All javax imports to jakarta
    5. Ensure: `@Transactional` on persistence methods
- Why: Quarkus does not support @Stateless EJBs
- Depends on: none
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ShippingService.java`

### Step 22: COMPLEX - Migrate ShoppingCartService
- Phase: Service Layer
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - BEFORE: `@Stateful` EJB with JNDI lookups for remote EJB
  - AFTER: `@ApplicationScoped` CDI bean with direct injection
  - Specific changes:
    1. Remove: `import javax.ejb.Stateful;`, JNDI InitialContext code, javax.naming imports
    2. Add: `import jakarta.enterprise.context.ApplicationScoped;`, `import jakarta.inject.Inject;`
    3. Replace: `@Stateful` with `@ApplicationScoped`
    4. Replace: JNDI lookup of ShippingService with `@Inject ShippingService shippingService;`
    5. Update: All javax imports to jakarta
    6. Add: `@Transactional` to methods with persistence operations
- Why: Quarkus does not support @Stateful EJBs or JNDI lookups; use CDI injection
- Depends on: Step 21
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ShoppingCartService.java` and no JNDI code remains

### Step 23: COMPLEX - Migrate ShoppingCartOrderProcessor
- Phase: Service Layer
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - BEFORE: `@Stateless` EJB with JMS Topic injection and JMSContext
  - AFTER: `@ApplicationScoped` CDI bean with Reactive Messaging @Channel Emitter
  - Specific changes:
    1. Remove: `import javax.ejb.Stateless;`, `import javax.annotation.Resource;`, `import javax.jms.JMSContext;`, `import javax.jms.Topic;`
    2. Add: `import jakarta.enterprise.context.ApplicationScoped;`, `import org.eclipse.microprofile.reactive.messaging.Channel;`, `import org.eclipse.microprofile.reactive.messaging.Emitter;`
    3. Replace: `@Stateless` with `@ApplicationScoped`
    4. Replace: 
       ```java
       @Inject
       private transient JMSContext context;
       @Resource(lookup = "java:/topic/orders")
       private Topic ordersTopic;
       ```
       with:
       ```java
       @Channel("orders")
       Emitter<String> ordersEmitter;
       ```
    5. Replace: `context.createProducer().send(ordersTopic, Transformers.shoppingCartToJson(cart));` with `ordersEmitter.send(Transformers.shoppingCartToJson(cart));`
    6. Update: All javax imports to jakarta
- Why: Quarkus uses Reactive Messaging with Emitters instead of JMS API
- Depends on: Step 8, Step 15
- Verify: `grep '@Channel.*orders' src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java`

### Step 24: COMPLEX - Migrate OrderServiceMDB
- Phase: Service Layer
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: `@MessageDriven` EJB with MessageListener interface
  - AFTER: `@ApplicationScoped` CDI bean with `@Incoming` reactive messaging
  - Specific changes:
    1. Remove: `import javax.ejb.*;`, `import javax.jms.*;`, `implements MessageListener`
    2. Add: `import jakarta.enterprise.context.ApplicationScoped;`, `import jakarta.transaction.Transactional;`, `import org.eclipse.microprofile.reactive.messaging.Incoming;`, `import io.smallrye.common.annotation.Blocking;`
    3. Replace: `@MessageDriven(...)` with `@ApplicationScoped`
    4. Replace: `public void onMessage(Message rcvMessage)` method signature with:
       ```java
       @Incoming("orders")
       @Blocking
       @Transactional
       public void onMessage(String orderStr)
       ```
    5. Remove: All JMS message unwrapping code (`TextMessage msg`, `msg.getBody(String.class)`)
    6. Use: `orderStr` directly instead of extracting from JMS message
    7. Update: All javax imports to jakarta
- Why: Quarkus uses Reactive Messaging @Incoming instead of @MessageDriven MDBs
- Depends on: Step 8, Step 17, Step 19
- Verify: `grep '@Incoming.*orders' src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java` and no @MessageDriven remains

### Step 25: COMPLEX - Migrate InventoryNotificationMDB
- Phase: Service Layer
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: Manual JMS setup with WebLogic JNDI, MessageListener interface
  - AFTER: `@ApplicationScoped` CDI bean with `@Incoming` reactive messaging
  - Specific changes:
    1. Remove: All imports for javax.jms.*, javax.naming.*, javax.rmi.*, WebLogic classes
    2. Add: `import jakarta.enterprise.context.ApplicationScoped;`, `import org.eclipse.microprofile.reactive.messaging.Incoming;`, `import io.smallrye.common.annotation.Blocking;`
    3. Replace: Class declaration to add `@ApplicationScoped`
    4. Remove: All JNDI-related fields, constants (JNDI_FACTORY, JMS_FACTORY, TOPIC, connection fields), init(), close(), getInitialContext() methods
    5. Replace: `public void onMessage(Message rcvMessage)` with:
       ```java
       @Incoming("orders")
       @Blocking
       public void onMessage(String orderStr)
       ```
    6. Remove: JMS message unwrapping, use orderStr directly
    7. Update: All javax imports to jakarta
- Why: Quarkus does not support JNDI or manual JMS setup; use Reactive Messaging
- Depends on: Step 8, Step 17
- Verify: `grep '@Incoming.*orders' src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java` and no JNDI code

### Step 26: Migrate ShippingServiceRemote
- Phase: Service Layer
- File: src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java
- Action: MODIFY
- What to do: Update imports from javax to jakarta (if this is a Remote interface, consider if still needed)
- Why: Quarkus 3 uses Jakarta EE namespace; Remote EJB interfaces may not be needed if no remote clients
- Depends on: none
- Verify: All javax imports replaced with jakarta

### Step 27: Migrate Producers utility class
- Phase: Utilities and Configuration
- File: src/main/java/com/redhat/coolstore/utils/Producers.java
- Action: MODIFY
- What to do: Update imports from javax to jakarta, keep or remove @Produces as appropriate for Logger production
- Why: Quarkus 3 uses Jakarta EE; @Produces for Logger is acceptable in Quarkus
- Depends on: none
- Verify: All javax imports replaced with jakarta

### Step 28: Migrate DataBaseMigrationStartup
- Phase: Utilities and Configuration
- File: src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- Action: MODIFY
- What to do:
  - Update imports from javax to jakarta
  - Add `@Transactional` annotation to methods that perform database operations
  - Verify startup mechanism is compatible with Quarkus (may need to use Quarkus lifecycle events)
- Why: Quarkus 3 uses Jakarta EE; database operations need @Transactional
- Depends on: Step 8
- Verify: `grep '@Transactional' src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java`

### Step 29: Migrate StartupListener
- Phase: Utilities and Configuration
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do: Update imports from javax to jakarta, verify compatibility with Quarkus startup mechanisms
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: none
- Verify: All javax imports replaced with jakarta

### Step 30: Migrate Transformers
- Phase: Utilities and Configuration
- File: src/main/java/com/redhat/coolstore/utils/Transformers.java
- Action: MODIFY
- What to do: Update imports from javax to jakarta (if any)
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 7, Step 11, Step 15
- Verify: No javax imports remain

### Step 31: COMPLEX - Remove or refactor Resources
- Phase: Persistence Layer
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: DELETE
- What to do: Delete this file - EntityManager injection works directly with @Inject in Quarkus without a producer
- Why: Quarkus provides EntityManager directly via CDI; custom @Produces for EntityManager is not needed and conflicts with Quarkus's built-in support
- Depends on: Step 17, Step 18, Step 19, Step 21, Step 22
- Verify: File no longer exists; all services use `@Inject EntityManager em;` directly

### Step 32: Migrate CartEndpoint
- Phase: REST API Layer
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do: Update all imports from javax.ws.rs.* to jakarta.ws.rs.*, javax.inject.* to jakarta.inject.*
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 22, Step 23
- Verify: `grep 'jakarta.ws.rs\|jakarta.inject' src/main/java/com/redhat/coolstore/rest/CartEndpoint.java`

### Step 33: Migrate OrderEndpoint
- Phase: REST API Layer
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do: Update all imports from javax.ws.rs.* to jakarta.ws.rs.*, javax.inject.* to jakarta.inject.*
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 19
- Verify: `grep 'jakarta.ws.rs\|jakarta.inject' src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java`

### Step 34: Migrate ProductEndpoint
- Phase: REST API Layer
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do: Update all imports from javax.ws.rs.* to jakarta.ws.rs.*, javax.inject.* to jakarta.inject.*
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 17, Step 18
- Verify: `grep 'jakarta.ws.rs\|jakarta.inject' src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java`

### Step 35: Remove RestApplication
- Phase: REST API Layer
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: DELETE
- What to do: Delete this file - JAX-RS activation via ApplicationPath is not needed in Quarkus
- Why: Quarkus auto-configures JAX-RS; explicit Application subclass is unnecessary. REST path is configured in application.properties
- Depends on: Step 8, Step 32, Step 33, Step 34
- Verify: File no longer exists

### Step 36: Remove persistence.xml
- Phase: Persistence Layer
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Delete this file - configuration moved to application.properties
- Why: Quarkus uses application.properties for persistence configuration
- Depends on: Step 8
- Verify: File no longer exists; configuration is in application.properties

### Step 37: Remove beans.xml
- Phase: Utilities and Configuration
- File: src/main/webapp/WEB-INF/beans.xml
- Action: DELETE
- What to do: Delete this file - CDI is auto-enabled in Quarkus, beans.xml content is ignored
- Why: Quarkus enables CDI by default and ignores beans.xml discovery mode settings
- Depends on: none
- Verify: File no longer exists

### Step 38: Remove web.xml (if present)
- Phase: Utilities and Configuration
- File: src/main/webapp/WEB-INF/web.xml
- Action: DELETE
- What to do: Delete this file if present - not needed for Quarkus JAR packaging
- Why: Quarkus does not use web.xml deployment descriptors
- Depends on: Step 1
- Verify: File no longer exists

### Step 39: Remove WebLogic ApplicationLifecycleEvent
- Phase: Utilities and Configuration
- File: src/main/java/weblogic/application/ApplicationLifecycleEvent.java
- Action: DELETE
- What to do: Delete this WebLogic-specific class
- Why: WebLogic-specific APIs are not supported in Quarkus
- Depends on: none
- Verify: File no longer exists

### Step 40: Remove WebLogic ApplicationLifecycleListener
- Phase: Utilities and Configuration
- File: src/main/java/weblogic/application/ApplicationLifecycleListener.java
- Action: DELETE
- What to do: Delete this WebLogic-specific class
- Why: WebLogic-specific APIs are not supported in Quarkus
- Depends on: none
- Verify: File no longer exists

### Step 41: Remove WebLogic NonCatalogLogger
- Phase: Utilities and Configuration
- File: src/main/java/weblogic/i18n/logging/NonCatalogLogger.java
- Action: DELETE
- What to do: Delete this WebLogic-specific class
- Why: WebLogic-specific APIs are not supported in Quarkus
- Depends on: none
- Verify: File no longer exists

## Verification

- Build: `mvn clean compile`
- Test: Tests are currently skipped (maven.test.skip=true in pom.xml), but after migration can run `mvn test` once tests are updated
- Blackbox: 
  1. Start PostgreSQL database: `podman run --name myPostgresDb -p 5432:5432 -e POSTGRES_USER=postgresUser -e POSTGRES_PASSWORD=postgresPW -e POSTGRES_DB=postgresDB -d postgres`
  2. Run Quarkus application: `mvn quarkus:dev`
  3. Navigate to http://localhost:8080
  4. Verify the application UI loads correctly
  5. Test product catalog endpoint: `curl http://localhost:8080/services/products`
  6. Test shopping cart functionality through the UI
  7. Complete a test checkout to verify end-to-end flow including Reactive Messaging
  8. Monitor application logs to see order processing messages from both OrderServiceMDB and InventoryNotificationMDB

## Notes

1. **Keycloak Integration**: The original application uses Keycloak for authentication. After migration, Quarkus OIDC extension may be needed (`quarkus-oidc`) to integrate with Keycloak. This is not covered in the analysis.json but should be considered for production deployment.

2. **Reactive Messaging In-Memory**: The migration uses SmallRye in-memory connector for Reactive Messaging since the original JMS topics were used for inter-component communication within the monolith. For production with multiple instances, consider using Kafka or another message broker.

3. **Stateful to Stateless**: Converting ShoppingCartService from @Stateful to @ApplicationScoped changes session management semantics. The application may need session state management at the REST layer or use a distributed cache for shopping cart state.

4. **Static Resources**: The webapp directory contains static resources (HTML, JSP, JavaScript). These should be moved to src/main/resources/META-INF/resources for Quarkus to serve them correctly.

5. **Flyway Migration**: The application uses Flyway for database migrations. Quarkus has built-in Flyway support - ensure the flyway-core dependency is updated to a version compatible with Quarkus 3.

6. **Health Checks**: Original application has health.jsp. Consider replacing with Quarkus SmallRye Health extension for proper health check endpoints.

7. **Transaction Boundaries**: Carefully review all methods marked with @Transactional to ensure transaction boundaries are appropriate for the business logic.

8. **Testing**: The original pom.xml has tests skipped. After migration, update or create new tests using Quarkus test framework (@QuarkusTest).
