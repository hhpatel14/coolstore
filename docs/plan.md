# Migration Plan

## Goal
Migrate a Java EE 7 monolithic application (CoolStore) from JBoss EAP 7.4 to Quarkus 3.

## Source → Target
Java EE 7 (JBoss EAP 7.4) → Quarkus 3

## Scope
- Files affected: 38
- Estimated complexity: High
- Hardest areas: 
  1. JMS message-driven beans migration to reactive messaging (OrderServiceMDB, InventoryNotificationMDB)
  2. Remote EJB to REST service conversion (ShippingService)
  3. JNDI lookups replacement with CDI injection

## Key Decisions Applied
1. **JMS to Reactive Messaging**: Convert JMS Topics to SmallRye Reactive Messaging with in-memory channels for development, as Quarkus does not support JMS. Production deployments can use AMQP or Kafka connectors.
2. **Remote EJB to REST**: Convert the Remote EJB (ShippingService) to a REST endpoint using JAX-RS, as Quarkus does not support remote EJBs.
3. **Session-scoped beans**: Convert @Stateful ShoppingCartService to @ApplicationScoped and recommend externalizing session state to a cache or database for production use.
4. **Persistence configuration**: Move from persistence.xml to application.properties for centralized Quarkus configuration.
5. **Packaging**: Change from WAR to JAR packaging as Quarkus uses JAR by default.

## Approach

### Phase 1: Build Configuration
Update pom.xml to use Quarkus BOM, plugins, and dependencies. Change packaging from WAR to JAR.

### Phase 2: Configuration Files
- Create Quarkus application.properties with datasource and persistence settings
- Delete legacy Java EE descriptor files (web.xml, beans.xml, persistence.xml)

### Phase 3: Persistence Layer
- Update Resources.java to use @Inject instead of @PersistenceContext
- Remove @Produces from EntityManager producer

### Phase 4: Model/Entity Layer
No changes required - JPA entities are compatible with Quarkus.

### Phase 5: Service Layer - Basic EJB to CDI
- Convert @Stateless EJBs to @ApplicationScoped CDI beans
- Convert @Stateful EJB to @ApplicationScoped (with note about externalizing state)
- Add @Transactional annotations where needed

### Phase 6: Service Layer - Message-Driven Beans
- Convert @MessageDriven beans to reactive messaging consumers
- Replace JMS Topic injection with Emitter for publishers
- Update JNDI lookups to CDI injection

### Phase 7: Service Layer - Remote EJB
Convert Remote EJB to REST service

### Phase 8: REST Layer
- Simplify JAX-RS application class
- Add @Transactional to REST methods that need transactions

### Phase 9: Utilities
- Update startup listeners to use Quarkus lifecycle events
- Remove Producers that are no longer needed
- Update DataBaseMigrationStartup

### Phase 10: Cleanup
- Remove weblogic compatibility classes
- Delete obsolete configuration files

## Steps

### Step 1: Update pom.xml packaging to JAR
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Change `<packaging>war</packaging>` to `<packaging>jar</packaging>`
- Why: Quarkus uses JAR packaging by default
- Depends on: none
- Verify: `grep '<packaging>jar</packaging>' pom.xml` returns a match

### Step 2: Add Quarkus properties to pom.xml
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add the following properties after the existing `<properties>` section:
  ```xml
  <quarkus.platform.artifact-id>quarkus-bom</quarkus.platform.artifact-id>
  <quarkus.platform.group-id>io.quarkus.platform</quarkus.platform.group-id>
  <quarkus.platform.version>3.1.0.Final</quarkus.platform.version>
  <compiler-plugin.version>3.10.1</compiler-plugin.version>
  <maven.compiler.release>11</maven.compiler.release>
  <surefire-plugin.version>3.0.0</surefire-plugin.version>
  ```
- Why: Define Quarkus platform version and plugin versions
- Depends on: Step 1
- Verify: `grep 'quarkus.platform.version' pom.xml` returns a match

### Step 3: Add Quarkus BOM to pom.xml
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add dependencyManagement section before `<dependencies>`:
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
- Why: Import Quarkus BOM for consistent dependency versions
- Depends on: Step 2
- Verify: `grep 'quarkus-bom' pom.xml` returns a match

### Step 4: Replace Java EE dependencies with Quarkus extensions
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Replace all existing dependencies with:
  ```xml
  <dependencies>
    <!-- Quarkus extensions -->
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
      <artifactId>quarkus-flyway</artifactId>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-core</artifactId>
    </dependency>
  </dependencies>
  ```
- Why: Replace Java EE APIs with Quarkus extensions
- Depends on: Step 3
- Verify: `grep 'quarkus-resteasy-reactive-jackson' pom.xml` returns a match

### Step 5: Update Maven Compiler plugin in pom.xml
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Replace the existing maven-compiler-plugin configuration with:
  ```xml
  <plugin>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>${compiler-plugin.version}</version>
    <configuration>
      <compilerArgs>
        <arg>-parameters</arg>
      </compilerArgs>
    </configuration>
  </plugin>
  ```
- Why: Update compiler plugin for Quarkus compatibility
- Depends on: Step 4
- Verify: `grep '<arg>-parameters</arg>' pom.xml` returns a match

### Step 6: Add Quarkus Maven plugin to pom.xml
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add after the maven-compiler-plugin:
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
- Why: Enable Quarkus build and code generation
- Depends on: Step 5
- Verify: `grep 'quarkus-maven-plugin' pom.xml` returns a match

### Step 7: Add Maven Surefire plugin to pom.xml
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add after quarkus-maven-plugin:
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
- Why: Configure test execution for Quarkus
- Depends on: Step 6
- Verify: `grep 'maven-surefire-plugin' pom.xml` returns a match

### Step 8: Add Maven Failsafe plugin to pom.xml
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add after maven-surefire-plugin:
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
- Why: Configure integration test execution
- Depends on: Step 7
- Verify: `grep 'maven-failsafe-plugin' pom.xml` returns a match

### Step 9: Remove maven-war-plugin from pom.xml
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Remove the maven-war-plugin configuration
- Why: No longer needed as we're using JAR packaging
- Depends on: Step 8
- Verify: `grep -c 'maven-war-plugin' pom.xml` returns 0

### Step 10: Add native profile to pom.xml
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Replace the empty profiles section with:
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
- Why: Enable native compilation support
- Depends on: Step 9
- Verify: `grep 'quarkus.package.type' pom.xml` returns a match

### Step 11: Create application.properties
- Phase: Configuration Files
- File: src/main/resources/application.properties
- Action: CREATE
- What to do: Create file with the following content:
  ```properties
  # Datasource configuration
  quarkus.datasource.db-kind=postgresql
  quarkus.datasource.username=postgresUser
  quarkus.datasource.password=postgresPW
  quarkus.datasource.jdbc.url=jdbc:postgresql://127.0.0.1:5432/postgresDB
  
  # Hibernate ORM configuration
  quarkus.hibernate-orm.database.generation=none
  quarkus.hibernate-orm.log.sql=false
  quarkus.hibernate-orm.log.format-sql=true
  quarkus.hibernate-orm.jdbc.statement-batch-size=0
  
  # Flyway configuration
  quarkus.flyway.migrate-at-start=true
  
  # RESTEasy configuration
  quarkus.resteasy-reactive.path=/services
  
  # Reactive Messaging - In-memory channels for orders
  mp.messaging.outgoing.orders.connector=smallrye-in-memory
  mp.messaging.incoming.orders.connector=smallrye-in-memory
  ```
- Why: Centralize all configuration in Quarkus application.properties
- Depends on: Step 10
- Verify: File exists and contains quarkus.datasource configuration

### Step 12: Update Resources.java to use @Inject
- Phase: Persistence Layer
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do:
  - Replace `import javax.persistence.PersistenceContext;` with `import jakarta.inject.Inject;`
  - Replace `@PersistenceContext` with `@Inject`
  - Remove the `@Produces` annotation
  - Remove the `@Dependent` annotation
  - Change imports from `javax.enterprise` to `jakarta.enterprise` if needed
- Why: Quarkus uses @Inject for EntityManager injection and produces EntityManager automatically
- Depends on: Step 11
- Verify: `grep '@Inject' src/main/java/com/redhat/coolstore/persistence/Resources.java` returns a match

### Step 13: Migrate CatalogService from @Stateless to @ApplicationScoped
- Phase: Service Layer - Basic EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do:
  - Remove `import javax.ejb.Stateless;`
  - Add `import jakarta.enterprise.context.ApplicationScoped;`
  - Add `import jakarta.transaction.Transactional;`
  - Replace `@Stateless` with `@ApplicationScoped`
  - Add `@Transactional` annotation to the class
  - Update all javax imports to jakarta (javax.persistence → jakarta.persistence, javax.inject → jakarta.inject)
- Why: Quarkus uses CDI beans instead of EJBs; @Transactional replaces container-managed transactions
- Depends on: Step 12
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/CatalogService.java` returns a match

### Step 14: Migrate OrderService from @Stateless to @ApplicationScoped
- Phase: Service Layer - Basic EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do:
  - Remove `import javax.ejb.Stateless;`
  - Add `import jakarta.enterprise.context.ApplicationScoped;`
  - Add `import jakarta.transaction.Transactional;`
  - Replace `@Stateless` with `@ApplicationScoped`
  - Add `@Transactional` annotation to the class
  - Update all javax imports to jakarta
- Why: Quarkus uses CDI beans instead of EJBs; @Transactional replaces container-managed transactions
- Depends on: Step 12
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/OrderService.java` returns a match

### Step 15: Migrate ProductService from @Stateless to @ApplicationScoped
- Phase: Service Layer - Basic EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do:
  - Remove `import javax.ejb.Stateless;`
  - Add `import jakarta.enterprise.context.ApplicationScoped;`
  - Add `import jakarta.transaction.Transactional;`
  - Replace `@Stateless` with `@ApplicationScoped`
  - Add `@Transactional` annotation to the class
  - Update all javax imports to jakarta
- Why: Quarkus uses CDI beans instead of EJBs; @Transactional replaces container-managed transactions
- Depends on: Step 12
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ProductService.java` returns a match

### Step 16: COMPLEX - Migrate ShoppingCartService from @Stateful to @ApplicationScoped
- Phase: Service Layer - Basic EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - BEFORE: @Stateful EJB with session state
  - AFTER: @ApplicationScoped CDI bean
  - Specific changes:
    1. Remove: `import javax.ejb.Stateful;`
    2. Add: `import jakarta.enterprise.context.ApplicationScoped;`, `import jakarta.transaction.Transactional;`
    3. Replace: `@Stateful` with `@ApplicationScoped`
    4. Add: `@Transactional` annotation to the class
    5. Update all javax imports to jakarta
    6. Replace JNDI lookup at line 119-121 with CDI injection:
       - Remove InitialContext usage
       - Add `@Inject ShippingService shippingService;` field
       - Use `shippingService` directly instead of lookup
  - Add comment: "// TODO: For production, externalize session state to a cache (e.g., Infinispan) or database"
- Why: Quarkus doesn't support @Stateful EJBs; session state should be externalized for scalability
- Depends on: Step 12
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ShoppingCartService.java` returns a match

### Step 17: COMPLEX - Convert ShoppingCartOrderProcessor from JMS to Reactive Messaging
- Phase: Service Layer - Message-Driven Beans
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - BEFORE: @Stateless with JMS Topic injection
  - AFTER: @ApplicationScoped with reactive messaging Emitter
  - Specific changes:
    1. Remove: `import javax.ejb.Stateless;`, `import javax.annotation.Resource;`, `import javax.jms.JMSContext;`, `import javax.jms.Topic;`
    2. Add: `import jakarta.enterprise.context.ApplicationScoped;`, `import org.eclipse.microprofile.reactive.messaging.Channel;`, `import org.eclipse.microprofile.reactive.messaging.Emitter;`, `import jakarta.inject.Inject;`
    3. Replace: `@Stateless` with `@ApplicationScoped`
    4. Replace JMS Topic injection:
       - Remove: `@Inject private transient JMSContext context;` and `@Resource(lookup = "java:/topic/orders") private Topic ordersTopic;`
       - Add: `@Inject @Channel("orders") Emitter<String> ordersEmitter;`
    5. Update process method:
       - Replace: `context.createProducer().send(ordersTopic, Transformers.shoppingCartToJson(cart));`
       - With: `ordersEmitter.send(Transformers.shoppingCartToJson(cart));`
    6. Update all javax imports to jakarta
- Why: Quarkus uses reactive messaging instead of JMS
- Depends on: Step 11, Step 12
- Verify: `grep 'Emitter<String>' src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java` returns a match

### Step 18: COMPLEX - Convert OrderServiceMDB to Reactive Messaging Consumer
- Phase: Service Layer - Message-Driven Beans
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: @MessageDriven EJB implementing MessageListener
  - AFTER: @ApplicationScoped CDI bean with @Incoming method
  - Specific changes:
    1. Remove: `import javax.ejb.*;`, `import javax.jms.*;`, `MessageListener` interface
    2. Add: `import jakarta.enterprise.context.ApplicationScoped;`, `import jakarta.transaction.Transactional;`, `import org.eclipse.microprofile.reactive.messaging.Incoming;`, `import jakarta.inject.Inject;`
    3. Replace: `@MessageDriven(...)` with `@ApplicationScoped`
    4. Remove: `implements MessageListener`
    5. Replace onMessage method:
       - Change signature from `public void onMessage(Message rcvMessage)` to `@Incoming("orders") @Transactional public void onMessage(String orderStr)`
       - Remove JMS message handling code (TextMessage casting, etc.)
       - Directly use the orderStr parameter instead of extracting from Message
       - Remove try-catch for JMSException
    6. Update all javax imports to jakarta
- Why: Quarkus uses reactive messaging with @Incoming instead of @MessageDriven
- Depends on: Step 11, Step 13, Step 14
- Verify: `grep '@Incoming("orders")' src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java` returns a match

### Step 19: COMPLEX - Convert InventoryNotificationMDB to Reactive Messaging Consumer
- Phase: Service Layer - Message-Driven Beans
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: MessageListener with manual JMS setup and JNDI lookups
  - AFTER: @ApplicationScoped CDI bean with @Incoming method
  - Specific changes:
    1. Remove: All JMS imports (`javax.jms.*`), JNDI imports (`javax.naming.*`), RMI import (`javax.rmi.PortableRemoteObject`)
    2. Add: `import jakarta.enterprise.context.ApplicationScoped;`, `import org.eclipse.microprofile.reactive.messaging.Incoming;`, `import jakarta.inject.Inject;`
    3. Add: `@ApplicationScoped` annotation to the class
    4. Remove: All JNDI-related constants (JNDI_FACTORY, JMS_FACTORY, TOPIC)
    5. Remove: All JMS connection fields (tcon, tsession, tsubscriber)
    6. Remove: init(), close(), and getInitialContext() methods
    7. Replace onMessage method:
       - Change signature to `@Incoming("orders") public void onMessage(String orderStr)`
       - Remove JMS message handling code
       - Directly use orderStr parameter
       - Remove try-catch for JMSException
    8. Update all javax imports to jakarta
- Why: Quarkus uses reactive messaging; JNDI and manual JMS setup not supported
- Depends on: Step 11, Step 13
- Verify: `grep '@Incoming("orders")' src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java` returns a match

### Step 20: COMPLEX - Convert ShippingService from Remote EJB to REST Service
- Phase: Service Layer - Remote EJB
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do:
  - BEFORE: @Stateless @Remote EJB implementing ShippingServiceRemote
  - AFTER: @ApplicationScoped REST service with JAX-RS annotations
  - Specific changes:
    1. Remove: `import javax.ejb.Remote;`, `import javax.ejb.Stateless;`, `implements ShippingServiceRemote`
    2. Add: `import jakarta.enterprise.context.ApplicationScoped;`, `import jakarta.ws.rs.*;`, `import jakarta.ws.rs.core.MediaType;`
    3. Replace: `@Stateless @Remote` with `@ApplicationScoped @Path("/shipping")`
    4. Add method annotations:
       - Add `@POST @Path("/calculate") @Consumes(MediaType.APPLICATION_JSON) @Produces(MediaType.APPLICATION_JSON)` to calculateShipping method
       - Add `@POST @Path("/insurance") @Consumes(MediaType.APPLICATION_JSON) @Produces(MediaType.APPLICATION_JSON)` to calculateShippingInsurance method
    5. Keep: All existing method logic unchanged
    6. Update all javax imports to jakarta
- Why: Quarkus doesn't support Remote EJBs; use REST services instead
- Depends on: Step 12
- Verify: `grep '@Path("/shipping")' src/main/java/com/redhat/coolstore/service/ShippingService.java` returns a match

### Step 21: Update PromoService
- Phase: Service Layer - Basic EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/PromoService.java
- Action: MODIFY
- What to do:
  - Update all javax imports to jakarta (if any)
  - Add `import jakarta.enterprise.context.ApplicationScoped;` if the class needs scoping
  - Add `@ApplicationScoped` if not already scoped
- Why: Ensure CDI bean discovery and Jakarta EE API compatibility
- Depends on: Step 12
- Verify: File compiles without errors

### Step 22: Update CartEndpoint
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do:
  - Update all javax imports to jakarta (javax.ws.rs → jakarta.ws.rs, javax.inject → jakarta.inject)
  - Add `import jakarta.transaction.Transactional;`
  - Add `@Transactional` annotation to methods that modify data (checkout, set methods)
- Why: Jakarta EE 9+ uses jakarta namespace; explicit transaction boundaries needed in Quarkus
- Depends on: Step 16, Step 17
- Verify: `grep 'jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/CartEndpoint.java` returns a match

### Step 23: Update OrderEndpoint
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do:
  - Update all javax imports to jakarta
  - Add `import jakarta.transaction.Transactional;` if needed
  - Add `@Transactional` to methods that modify data if not covered by service layer
- Why: Jakarta EE 9+ uses jakarta namespace
- Depends on: Step 14
- Verify: `grep 'jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java` returns a match

### Step 24: Update ProductEndpoint
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do:
  - Update all javax imports to jakarta
- Why: Jakarta EE 9+ uses jakarta namespace
- Depends on: Step 15
- Verify: `grep 'jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java` returns a match

### Step 25: Simplify RestApplication
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: MODIFY
- What to do:
  - Update `import javax.ws.rs.ApplicationPath;` to `import jakarta.ws.rs.ApplicationPath;`
  - Update `import javax.ws.rs.core.Application;` to `import jakarta.ws.rs.core.Application;`
  - Keep the @ApplicationPath and class definition as is
- Why: Jakarta EE 9+ uses jakarta namespace; JAX-RS activation is automatic in Quarkus but ApplicationPath is still useful for setting the base path
- Depends on: Step 11
- Verify: `grep 'jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/RestApplication.java` returns a match

### Step 26: Update model classes to Jakarta namespace
- Phase: Model/Entity Layer
- File: src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java
- Action: MODIFY
- What to do: Update all javax.persistence imports to jakarta.persistence
- Why: Jakarta EE 9+ uses jakarta namespace
- Depends on: Step 12
- Verify: `grep 'jakarta.persistence' src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java` returns a match

### Step 27: Update model classes to Jakarta namespace
- Phase: Model/Entity Layer
- File: src/main/java/com/redhat/coolstore/model/InventoryEntity.java
- Action: MODIFY
- What to do: Update all javax.persistence imports to jakarta.persistence
- Why: Jakarta EE 9+ uses jakarta namespace
- Depends on: Step 12
- Verify: `grep 'jakarta.persistence' src/main/java/com/redhat/coolstore/model/InventoryEntity.java` returns a match

### Step 28: Update model classes to Jakarta namespace
- Phase: Model/Entity Layer
- File: src/main/java/com/redhat/coolstore/model/Order.java
- Action: MODIFY
- What to do: Update all javax.persistence imports to jakarta.persistence
- Why: Jakarta EE 9+ uses jakarta namespace
- Depends on: Step 12
- Verify: `grep 'jakarta.persistence' src/main/java/com/redhat/coolstore/model/Order.java` returns a match

### Step 29: Update model classes to Jakarta namespace
- Phase: Model/Entity Layer
- File: src/main/java/com/redhat/coolstore/model/OrderItem.java
- Action: MODIFY
- What to do: Update all javax.persistence imports to jakarta.persistence
- Why: Jakarta EE 9+ uses jakarta namespace
- Depends on: Step 12
- Verify: `grep 'jakarta.persistence' src/main/java/com/redhat/coolstore/model/OrderItem.java` returns a match

### Step 30: Update model classes to Jakarta namespace
- Phase: Model/Entity Layer
- File: src/main/java/com/redhat/coolstore/model/Product.java
- Action: MODIFY
- What to do: Update all javax imports to jakarta (if any persistence or validation annotations)
- Why: Jakarta EE 9+ uses jakarta namespace
- Depends on: Step 12
- Verify: File compiles without errors

### Step 31: Update model classes to Jakarta namespace
- Phase: Model/Entity Layer
- File: src/main/java/com/redhat/coolstore/model/Promotion.java
- Action: MODIFY
- What to do: Update all javax imports to jakarta (if any)
- Why: Jakarta EE 9+ uses jakarta namespace
- Depends on: Step 12
- Verify: File compiles without errors

### Step 32: Update model classes to Jakarta namespace
- Phase: Model/Entity Layer
- File: src/main/java/com/redhat/coolstore/model/ShoppingCart.java
- Action: MODIFY
- What to do: Update all javax imports to jakarta (if any)
- Why: Jakarta EE 9+ uses jakarta namespace
- Depends on: Step 12
- Verify: File compiles without errors

### Step 33: Update model classes to Jakarta namespace
- Phase: Model/Entity Layer
- File: src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java
- Action: MODIFY
- What to do: Update all javax imports to jakarta (if any)
- Why: Jakarta EE 9+ uses jakarta namespace
- Depends on: Step 12
- Verify: File compiles without errors

### Step 34: COMPLEX - Update DataBaseMigrationStartup to Quarkus lifecycle
- Phase: Utilities
- File: src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- Action: MODIFY
- What to do:
  - BEFORE: EJB singleton with @Startup and @PostConstruct
  - AFTER: CDI bean with Quarkus startup event
  - Specific changes:
    1. Remove: `import javax.ejb.*;`, `import javax.annotation.PostConstruct;`
    2. Add: `import jakarta.enterprise.context.ApplicationScoped;`, `import jakarta.transaction.Transactional;`, `import io.quarkus.runtime.StartupEvent;`, `import jakarta.enterprise.event.Observes;`
    3. Replace: `@Singleton @Startup` with `@ApplicationScoped`
    4. Replace @PostConstruct method with:
       - Change `@PostConstruct public void init()` to `void onStart(@Observes StartupEvent ev)`
    5. Add `@Transactional` to methods that perform database operations
    6. Update all javax imports to jakarta
- Why: Quarkus uses CDI events for lifecycle management instead of EJB annotations
- Depends on: Step 12
- Verify: `grep 'StartupEvent' src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java` returns a match

### Step 35: Update Producers utility
- Phase: Utilities
- File: src/main/java/com/redhat/coolstore/utils/Producers.java
- Action: MODIFY
- What to do:
  - Update all javax imports to jakarta (javax.enterprise → jakarta.enterprise, javax.inject → jakarta.inject)
  - Review @Produces methods and ensure they follow Quarkus patterns
  - If producing Logger, verify it's compatible with Quarkus logging
- Why: Jakarta EE 9+ uses jakarta namespace
- Depends on: Step 12
- Verify: `grep 'jakarta.enterprise' src/main/java/com/redhat/coolstore/utils/Producers.java` returns a match

### Step 36: Update StartupListener
- Phase: Utilities
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do:
  - If using weblogic-specific APIs, convert to Quarkus startup event
  - Update all javax imports to jakarta
  - Replace servlet context listener with Quarkus `@Observes StartupEvent` if applicable
- Why: Remove vendor-specific code and use Quarkus lifecycle
- Depends on: Step 34
- Verify: File compiles without errors

### Step 37: Update Transformers utility
- Phase: Utilities
- File: src/main/java/com/redhat/coolstore/utils/Transformers.java
- Action: MODIFY
- What to do:
  - Update all javax.json imports to jakarta.json
  - Ensure JSON-P API usage is compatible with Quarkus
- Why: Jakarta EE 9+ uses jakarta namespace
- Depends on: Step 12
- Verify: `grep 'jakarta.json' src/main/java/com/redhat/coolstore/utils/Transformers.java` returns a match or no JSON imports are used

### Step 38: Delete ShippingServiceRemote interface
- Phase: Cleanup
- File: src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java
- Action: DELETE
- What to do: Delete this file - it's no longer needed since ShippingService is now a REST service
- Why: Remote EJB interfaces are not supported in Quarkus
- Depends on: Step 20
- Verify: File no longer exists

### Step 39: Delete weblogic ApplicationLifecycleEvent
- Phase: Cleanup
- File: src/main/java/weblogic/application/ApplicationLifecycleEvent.java
- Action: DELETE
- What to do: Delete this file - WebLogic compatibility layer not needed
- Why: Vendor-specific code not required in Quarkus
- Depends on: Step 36
- Verify: File no longer exists

### Step 40: Delete weblogic ApplicationLifecycleListener
- Phase: Cleanup
- File: src/main/java/weblogic/application/ApplicationLifecycleListener.java
- Action: DELETE
- What to do: Delete this file - WebLogic compatibility layer not needed
- Why: Vendor-specific code not required in Quarkus
- Depends on: Step 36
- Verify: File no longer exists

### Step 41: Delete weblogic NonCatalogLogger
- Phase: Cleanup
- File: src/main/java/weblogic/i18n/logging/NonCatalogLogger.java
- Action: DELETE
- What to do: Delete this file - WebLogic compatibility layer not needed
- Why: Vendor-specific code not required in Quarkus; use standard logging
- Depends on: Step 36
- Verify: File no longer exists

### Step 42: Delete persistence.xml
- Phase: Cleanup
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Delete this file - configuration moved to application.properties
- Why: Quarkus uses application.properties for persistence configuration
- Depends on: Step 11
- Verify: File no longer exists

### Step 43: Delete beans.xml
- Phase: Cleanup
- File: src/main/webapp/WEB-INF/beans.xml
- Action: DELETE
- What to do: Delete this file - beans.xml content is ignored in Quarkus and CDI is enabled by default
- Why: Quarkus enables CDI automatically; beans.xml descriptor content is ignored
- Depends on: Step 11
- Verify: File no longer exists

### Step 44: Delete web.xml
- Phase: Cleanup
- File: src/main/webapp/WEB-INF/web.xml
- Action: DELETE
- What to do: Delete this file - deployment descriptors not needed in Quarkus
- Why: Quarkus doesn't use traditional Java EE deployment descriptors
- Depends on: Step 11
- Verify: File no longer exists

## Verification

- Build: `mvn clean compile`
- Test: Tests are currently skipped in original pom.xml (maven.test.skip=true); after migration, run `mvn test` to verify any existing tests pass
- Blackbox: 
  1. Start PostgreSQL database: `podman run --name myPostgresDb -p 5432:5432 -e POSTGRES_USER=postgresUser -e POSTGRES_PASSWORD=postgresPW -e POSTGRES_DB=postgresDB -d postgres`
  2. Start the Quarkus application: `mvn quarkus:dev`
  3. Navigate to http://localhost:8080
  4. Verify the CoolStore application UI loads
  5. Test key business flows:
     - Browse products
     - Add items to cart
     - Checkout process (note: Keycloak integration may need additional configuration)
  6. Check logs for successful message processing from reactive messaging channels
  7. Verify shipping calculation REST endpoint: `curl -X POST http://localhost:8080/services/shipping/calculate -H "Content-Type: application/json" -d '{"cartItemTotal": 30}'`

## Notes

### Critical Migration Items

1. **Reactive Messaging Configuration**: The migration uses in-memory channels for reactive messaging. For production:
   - Consider using AMQP (e.g., RabbitMQ) or Kafka connectors
   - Update application.properties with appropriate connector configuration
   - Example for AMQP: `mp.messaging.outgoing.orders.connector=smallrye-amqp`

2. **Session State in ShoppingCartService**: The @Stateful EJB was converted to @ApplicationScoped, which means session state is no longer automatically managed. For production:
   - Externalize cart state to Redis, Infinispan, or database
   - Consider using Quarkus Cache extension
   - Implement session management strategy

3. **Keycloak Integration**: The original application uses Keycloak for authentication. After migration:
   - Add `quarkus-oidc` extension: `<artifactId>quarkus-oidc</artifactId>`
   - Configure OIDC in application.properties:
     ```properties
     quarkus.oidc.auth-server-url=http://localhost:8081/realms/eap
     quarkus.oidc.client-id=coolstore
     ```
   - Secure endpoints with `@RolesAllowed` annotations

4. **Database Migration with Flyway**: The application uses Flyway for database migrations. Ensure:
   - Migration scripts are in `src/main/resources/db/migration/`
   - Scripts follow Flyway naming convention (V1__description.sql)
   - `quarkus.flyway.migrate-at-start=true` is set in application.properties

5. **Remote EJB Clients**: Any clients that previously called ShippingService via Remote EJB must be updated to:
   - Use REST client (JAX-RS client or Quarkus REST Client)
   - Update endpoint URLs to use `/services/shipping/calculate` and `/services/shipping/insurance`

6. **Transaction Management**: While most transaction boundaries are handled by @Transactional on service classes:
   - Verify all database operations are within transactional methods
   - Check for any programmatic transaction management that needs updating
   - Consider using `quarkus.transaction-manager.enable-recovery=true` for XA transactions if needed

7. **Logging**: Replace any java.util.logging or vendor-specific logging with:
   - Quarkus unified logging (supports JBoss Logging)
   - Configuration in application.properties: `quarkus.log.level=INFO`
   - Use `@Inject Logger log;` pattern from Producers

8. **Testing**: Original pom.xml has tests disabled. After migration:
   - Enable tests: remove or set `<maven.test.skip>false</maven.test.skip>`
   - Add Quarkus test dependencies for unit/integration testing
   - Use `@QuarkusTest` annotation for integration tests

9. **WebLogic Removal**: All WebLogic compatibility classes are removed. If the application is deployed to multiple app servers:
   - Remove references to weblogic classes in StartupListener or other utilities
   - Replace with standard Jakarta EE or Quarkus-specific alternatives

10. **JMS Message Format**: The migration assumes messages are String-based JSON. Verify:
    - Message serialization/deserialization works with reactive messaging
    - Consider using structured types with JSON-B if needed
