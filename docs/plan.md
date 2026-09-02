# Migration Plan

## Goal
Migrate the CoolStore monolith from Java EE 7 (targeting JBoss EAP 7.4) to Quarkus 3.

## Source → Target
Java EE 7 / JBoss EAP 7.4 → Quarkus 3

## Scope
- Files affected: 38
- Estimated complexity: High
- Hardest areas: JMS message-driven beans migration to reactive messaging, JNDI lookups replacement with CDI, Remote EJB conversion to REST

## Key Decisions Applied
1. **JMS to Reactive Messaging**: Converting JMS Topic/MDB to SmallRye Reactive Messaging with in-memory channels. For production, this could be configured to use external messaging brokers (Kafka, AMQP) via application.properties.
2. **Stateful EJB Scope**: Converting @Stateful ShoppingCartService to @ApplicationScoped rather than @SessionScoped to follow Quarkus best practices of keeping state external. Session state management will need to be handled at the application level or externally.
3. **Flyway Migration**: Keeping Flyway database migration as-is since Quarkus supports it, just needs configuration in application.properties.
4. **Packaging**: Changing from WAR to JAR packaging as Quarkus uses embedded server model.

## Approach

### Phase 1: Build Configuration
Update pom.xml to Quarkus 3 with necessary BOM, plugins, dependencies, and profiles. Change packaging from WAR to JAR.

### Phase 2: Application Configuration
Replace XML-based configuration (persistence.xml, beans.xml, web.xml) with Quarkus application.properties for datasource, persistence, and application settings.

### Phase 3: Core Infrastructure
Migrate persistence resources, CDI producers, and startup/lifecycle components to Quarkus patterns.

### Phase 4: Data Models
Update JPA entity classes with any necessary namespace changes from javax to jakarta.

### Phase 5: Service Layer - Simple EJBs
Convert stateless EJBs to CDI beans with @ApplicationScoped and @Transactional annotations.

### Phase 6: Service Layer - Complex Components
Migrate message-driven beans to reactive messaging, convert remote EJBs to REST endpoints, handle JNDI lookups replacement, and convert stateful EJBs.

### Phase 7: REST API Layer
Update JAX-RS endpoints and application configuration for Quarkus.

### Phase 8: Cleanup
Remove legacy deployment descriptors and Java EE specific files no longer needed in Quarkus.

## Steps

### Step 1: Update pom.xml packaging
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Change packaging from `<packaging>war</packaging>` to `<packaging>jar</packaging>`
- Why: Quarkus uses JAR packaging with embedded server, not WAR files for external containers
- Depends on: none
- Verify: pom.xml contains `<packaging>jar</packaging>`

### Step 2: Add Quarkus properties to pom.xml
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add Quarkus platform properties in the `<properties>` section:
  ```xml
  <quarkus.platform.artifact-id>quarkus-bom</quarkus.platform.artifact-id>
  <quarkus.platform.group-id>io.quarkus.platform</quarkus.platform.group-id>
  <quarkus.platform.version>3.1.0.Final</quarkus.platform.version>
  <compiler-plugin.version>3.10.1</compiler-plugin.version>
  <maven.compiler.release>11</maven.compiler.release>
  <surefire-plugin.version>3.0.0</surefire-plugin.version>
  ```
- Why: Required to manage Quarkus dependencies and configure build plugins
- Depends on: Step 1
- Verify: Properties section contains quarkus.platform.version

### Step 3: Add Quarkus BOM to pom.xml
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
- Why: Centralized Quarkus dependency version management
- Depends on: Step 2
- Verify: dependencyManagement section exists with quarkus-bom

### Step 4: Replace Java EE dependencies with Quarkus extensions in pom.xml
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Remove Java EE dependencies (javaee-web-api, javaee-api, jboss-jms-api, jboss-rmi-api) and add Quarkus extensions:
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
    <artifactId>quarkus-resteasy-jackson</artifactId>
  </dependency>
  <dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-arc</artifactId>
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
    <artifactId>quarkus-narayana-jta</artifactId>
  </dependency>
  <dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
    <version>4.1.2</version>
  </dependency>
  ```
- Why: Replace Java EE APIs with Quarkus extensions for JPA, REST, CDI, messaging, and transactions
- Depends on: Step 3
- Verify: No javax or jboss-* dependencies remain except flyway-core

### Step 5: Add Quarkus Maven plugin to pom.xml
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Replace maven-war-plugin with quarkus-maven-plugin in `<build><plugins>`:
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
- Why: Quarkus Maven plugin manages build, packaging, and code generation
- Depends on: Step 4
- Verify: quarkus-maven-plugin present, maven-war-plugin removed

### Step 6: Update Maven compiler plugin in pom.xml
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Update maven-compiler-plugin configuration:
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
- Why: Enable parameter name retention for CDI and reflection
- Depends on: Step 5
- Verify: maven-compiler-plugin has -parameters compiler arg

### Step 7: Add Maven Surefire plugin to pom.xml
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add maven-surefire-plugin to `<build><plugins>`:
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
- Verify: maven-surefire-plugin configured with JBoss LogManager

### Step 8: Add Maven Failsafe plugin to pom.xml
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add maven-failsafe-plugin to `<build><plugins>`:
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
- Why: Configure integration tests for Quarkus
- Depends on: Step 7
- Verify: maven-failsafe-plugin present with integration-test goal

### Step 9: Add native profile to pom.xml
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add native build profile in `<profiles>`:
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
- Why: Enable optional native compilation with GraalVM
- Depends on: Step 8
- Verify: Profile with id="native" exists

### Step 10: Create application.properties
- Phase: Application Configuration
- File: src/main/resources/application.properties
- Action: CREATE
- What to do: Create Quarkus configuration file with datasource, Hibernate, Flyway, and messaging settings:
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
  
  # Flyway configuration
  quarkus.flyway.migrate-at-start=true
  quarkus.flyway.locations=classpath:db/migration
  
  # REST configuration
  quarkus.resteasy.path=/services
  
  # Reactive Messaging - In-memory channels for topic/orders
  mp.messaging.outgoing.orders.connector=smallrye-in-memory
  mp.messaging.incoming.orders.connector=smallrye-in-memory
  ```
- Why: Replace XML-based configuration with centralized properties file
- Depends on: Step 1
- Verify: File exists with datasource and hibernate-orm configuration

### Step 11: Migrate Resources.java persistence producer
- Phase: Core Infrastructure
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do:
  - Remove the entire class content - EntityManager injection is automatic in Quarkus
  - Replace with a comment explaining EntityManager is now injected directly:
  ```java
  package com.redhat.coolstore.persistence;
  
  // EntityManager producer no longer needed in Quarkus.
  // Inject EntityManager directly with @Inject in your services.
  ```
- Why: Quarkus automatically provides EntityManager beans; @PersistenceContext and @Produces are not needed
- Depends on: Step 10
- Verify: Resources.java exists but does not contain @Produces or @PersistenceContext

### Step 12: Update Producers.java
- Phase: Core Infrastructure
- File: src/main/java/com/redhat/coolstore/utils/Producers.java
- Action: MODIFY
- What to do: Replace @Produces Logger with @Named approach or direct Logger injection pattern for Quarkus:
  - Remove @Produces annotation
  - Add @ApplicationScoped and keep the method with qualifier if still needed, or replace with direct Logger.getLogger calls in consuming classes
- Why: In Quarkus, @Produces can be skipped if the method has a scope/qualifier annotation
- Depends on: Step 11
- Verify: File compiles without @Produces annotation errors

### Step 13: COMPLEX - Migrate DataBaseMigrationStartup.java
- Phase: Core Infrastructure
- File: src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- Action: MODIFY
- What to do:
  - BEFORE: @Singleton EJB with @Startup and @PostConstruct, @TransactionAttribute
  - AFTER: Quarkus startup bean with io.quarkus.runtime.StartupEvent
  - Specific changes:
    1. Remove: `import javax.ejb.*`, `import javax.annotation.PostConstruct`
    2. Add: `import io.quarkus.runtime.StartupEvent`, `import jakarta.enterprise.event.Observes`, `import jakarta.transaction.Transactional`
    3. Replace: `@Singleton` with `@ApplicationScoped`
    4. Remove: `@Startup` annotation
    5. Replace: `@PostConstruct public void init()` with `@Transactional void onStart(@Observes StartupEvent event)`
    6. Remove: All `@TransactionAttribute` annotations
- Why: Quarkus uses event observers for startup, not EJB lifecycle annotations
- Depends on: Step 12
- Verify: Class uses StartupEvent observer pattern and compiles

### Step 14: Update StartupListener.java
- Phase: Core Infrastructure
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do: Replace WebLogic ApplicationLifecycleListener with Quarkus StartupEvent observer similar to Step 13
- Why: WebLogic-specific lifecycle hooks are not applicable in Quarkus
- Depends on: Step 13
- Verify: Uses Quarkus StartupEvent instead of WebLogic APIs

### Step 15: Update namespace imports in model classes
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java
- Action: MODIFY
- What to do: Replace `javax.persistence.*` imports with `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 10
- Verify: No javax.persistence imports remain

### Step 16: Update namespace imports in model classes
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/InventoryEntity.java
- Action: MODIFY
- What to do: Replace `javax.persistence.*` imports with `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 10
- Verify: No javax.persistence imports remain

### Step 17: Update namespace imports in model classes
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/Order.java
- Action: MODIFY
- What to do: Replace `javax.persistence.*` imports with `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 10
- Verify: No javax.persistence imports remain

### Step 18: Update namespace imports in model classes
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/OrderItem.java
- Action: MODIFY
- What to do: Replace `javax.persistence.*` imports with `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 10
- Verify: No javax.persistence imports remain

### Step 19: Update namespace imports in model classes
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/Product.java
- Action: MODIFY
- What to do: Replace `javax.*` imports with `jakarta.*` for persistence and validation APIs
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 10
- Verify: No javax imports remain

### Step 20: Update namespace imports in model classes
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/Promotion.java
- Action: MODIFY
- What to do: Replace `javax.*` imports with `jakarta.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 10
- Verify: No javax imports remain

### Step 21: Update namespace imports in model classes
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/ShoppingCart.java
- Action: MODIFY
- What to do: Replace `javax.*` imports with `jakarta.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 10
- Verify: No javax imports remain

### Step 22: Update namespace imports in model classes
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java
- Action: MODIFY
- What to do: Replace `javax.*` imports with `jakarta.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 10
- Verify: No javax imports remain

### Step 23: Migrate CatalogService.java
- Phase: Service Layer - Simple EJBs
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do:
  1. Replace `import javax.ejb.Stateless` with `import jakarta.enterprise.context.ApplicationScoped`
  2. Replace `import javax.inject.Inject` with `import jakarta.inject.Inject`
  3. Replace `import javax.persistence.EntityManager` with `import jakarta.persistence.EntityManager`
  4. Add `import jakarta.transaction.Transactional`
  5. Replace `@Stateless` with `@ApplicationScoped`
  6. Add `@Transactional` to the class level
- Why: Convert stateless EJB to CDI bean; EJB container transactions must be explicit with @Transactional
- Depends on: Step 11, Step 15, Step 16
- Verify: No @Stateless annotation, has @ApplicationScoped and @Transactional

### Step 24: Migrate ProductService.java
- Phase: Service Layer - Simple EJBs
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do:
  1. Replace `javax.*` imports with `jakarta.*`
  2. Replace `@Stateless` with `@ApplicationScoped`
  3. Add `@Transactional` at class level
- Why: Convert stateless EJB to CDI bean with explicit transaction management
- Depends on: Step 11, Step 19
- Verify: No @Stateless annotation, has @ApplicationScoped and @Transactional

### Step 25: Migrate OrderService.java
- Phase: Service Layer - Simple EJBs
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do:
  1. Replace `javax.*` imports with `jakarta.*`
  2. Replace `@Stateless` with `@ApplicationScoped`
  3. Add `@Transactional` at class level
- Why: Convert stateless EJB to CDI bean; persist() operations require @Transactional
- Depends on: Step 11, Step 17
- Verify: No @Stateless annotation, has @ApplicationScoped and @Transactional

### Step 26: Migrate PromoService.java
- Phase: Service Layer - Simple EJBs
- File: src/main/java/com/redhat/coolstore/service/PromoService.java
- Action: MODIFY
- What to do: Replace `javax.inject.Inject` with `jakarta.inject.Inject` and any other javax imports with jakarta equivalents
- Why: Update to Jakarta EE namespace
- Depends on: Step 10
- Verify: No javax imports remain

### Step 27: COMPLEX - Migrate ShippingService.java from Remote EJB to REST
- Phase: Service Layer - Complex Components
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do:
  - BEFORE: `@Stateless @Remote(ShippingServiceRemote.class)` EJB with business method
  - AFTER: REST resource with JAX-RS annotations
  - Specific changes:
    1. Remove: `import javax.ejb.*`, remote interface reference
    2. Add: `import jakarta.ws.rs.*`, `import jakarta.enterprise.context.ApplicationScoped`, `import jakarta.transaction.Transactional`
    3. Replace: `@Stateless` with `@ApplicationScoped`
    4. Add: `@Path("/shipping")` at class level
    5. Add: `@Transactional` at class level
    6. Update method `calculateShipping`: Add `@POST`, `@Path("/calculate")`, `@Consumes(MediaType.APPLICATION_JSON)`, `@Produces(MediaType.APPLICATION_JSON)`
    7. Remove: `@Remote` annotation
- Why: Remote EJBs are not supported in Quarkus; replace with REST endpoints
- Depends on: Step 11, Step 21
- Verify: Class has @Path annotation, no @Remote or @Stateless

### Step 28: COMPLEX - Migrate ShoppingCartOrderProcessor.java to reactive messaging
- Phase: Service Layer - Complex Components
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - BEFORE: `@Stateless` EJB with `@Inject JMSContext` and `@Resource Topic`
  - AFTER: CDI bean with SmallRye Reactive Messaging Emitter
  - Specific changes:
    1. Remove: `import javax.ejb.Stateless`, `import javax.annotation.Resource`, `import javax.jms.*`
    2. Add: `import jakarta.enterprise.context.ApplicationScoped`, `import org.eclipse.microprofile.reactive.messaging.Channel`, `import org.eclipse.microprofile.reactive.messaging.Emitter`, `import jakarta.inject.Inject`
    3. Replace: `@Stateless` with `@ApplicationScoped`
    4. Replace: `@Inject JMSContext context; @Resource(lookup = "java:/topic/orders") Topic ordersTopic;` with `@Inject @Channel("orders") Emitter<String> ordersEmitter;`
    5. Replace: `context.createProducer().send(ordersTopic, json)` with `ordersEmitter.send(json)`
- Why: JMS is replaced with SmallRye Reactive Messaging in Quarkus
- Depends on: Step 10, Step 21
- Verify: Uses Emitter instead of JMS Topic

### Step 29: COMPLEX - Migrate OrderServiceMDB.java to reactive messaging consumer
- Phase: Service Layer - Complex Components
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: `@MessageDriven` EJB with `MessageListener` interface
  - AFTER: CDI bean with `@Incoming` reactive messaging method
  - Specific changes:
    1. Remove: `import javax.ejb.*`, `import javax.jms.*`, `implements MessageListener`
    2. Add: `import jakarta.enterprise.context.ApplicationScoped`, `import org.eclipse.microprofile.reactive.messaging.Incoming`, `import jakarta.inject.Inject`
    3. Replace: `@MessageDriven(...)` with `@ApplicationScoped`
    4. Replace: `public void onMessage(Message rcvMessage)` with `@Incoming("orders") public void onMessage(String orderStr)`
    5. Remove: All JMS message handling code, TextMessage casting
    6. Simplify: Method receives String directly, parse it to Order
- Why: Message-driven beans are replaced with reactive messaging in Quarkus
- Depends on: Step 10, Step 25, Step 23
- Verify: Uses @Incoming annotation instead of @MessageDriven

### Step 30: COMPLEX - Migrate InventoryNotificationMDB.java to reactive messaging
- Phase: Service Layer - Complex Components
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
  - Remove JNDI lookups using InitialContext
  - Replace `@MessageDriven` with `@ApplicationScoped` and `@Incoming` for message consumption
  - Replace JNDI datasource lookup with `@Inject` EntityManager or DataSource
  - Update all `javax.*` imports to `jakarta.*`
- Why: JNDI is not supported in Quarkus; use CDI injection and reactive messaging
- Depends on: Step 10, Step 11
- Verify: No InitialContext or JNDI lookups, uses @Incoming

### Step 31: COMPLEX - Migrate ShoppingCartService.java stateful to stateless
- Phase: Service Layer - Complex Components
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - BEFORE: `@Stateful` EJB with JNDI lookups
  - AFTER: `@ApplicationScoped` CDI bean with dependency injection
  - Specific changes:
    1. Remove: `import javax.ejb.Stateful`, `import javax.naming.*`
    2. Add: `import jakarta.enterprise.context.ApplicationScoped`, `import jakarta.transaction.Transactional`
    3. Replace: `@Stateful` with `@ApplicationScoped`
    4. Add: `@Transactional` at class level
    5. Replace: JNDI lookups (InitialContext, lookup calls) with `@Inject` for dependencies
    6. Update all `javax.*` imports to `jakarta.*`
- Why: Stateful EJBs and JNDI are not supported in Quarkus; state should be managed externally
- Depends on: Step 11, Step 21, Step 27, Step 28
- Verify: No @Stateful, no InitialContext, has @ApplicationScoped

### Step 32: Update RestApplication.java
- Phase: REST API Layer
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: MODIFY
- What to do:
  1. Replace `import javax.ws.rs.*` with `import jakarta.ws.rs.*`
  2. Optionally remove @ApplicationPath annotation since path is configured in application.properties
  3. Consider removing the class entirely if no custom configuration needed
- Why: JAX-RS activation is automatic in Quarkus; path can be set in application.properties
- Depends on: Step 10
- Verify: Uses jakarta.ws.rs imports or class removed

### Step 33: Update CartEndpoint.java
- Phase: REST API Layer
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do: Replace all `javax.ws.rs.*` and `javax.inject.*` imports with `jakarta.*` equivalents
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 31, Step 32
- Verify: No javax imports remain

### Step 34: Update OrderEndpoint.java
- Phase: REST API Layer
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do: Replace all `javax.ws.rs.*` and `javax.inject.*` imports with `jakarta.*` equivalents
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 25, Step 32
- Verify: No javax imports remain

### Step 35: Update ProductEndpoint.java
- Phase: REST API Layer
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do: Replace all `javax.ws.rs.*` and `javax.inject.*` imports with `jakarta.*` equivalents
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 24, Step 32
- Verify: No javax imports remain

### Step 36: Update Transformers.java utility class
- Phase: REST API Layer
- File: src/main/java/com/redhat/coolstore/utils/Transformers.java
- Action: MODIFY
- What to do: Replace any `javax.*` imports with `jakarta.*` if present
- Why: Ensure namespace consistency
- Depends on: Step 17, Step 21
- Verify: No javax imports remain

### Step 37: Delete beans.xml
- Phase: Cleanup
- File: src/main/webapp/WEB-INF/beans.xml
- Action: DELETE
- What to do: Delete this file - CDI bean discovery is automatic in Quarkus
- Why: beans.xml descriptor content is ignored in Quarkus and can be removed
- Depends on: Step 33, Step 34, Step 35
- Verify: File no longer exists

### Step 38: Delete persistence.xml
- Phase: Cleanup
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Delete this file - persistence configuration moved to application.properties
- Why: Quarkus uses application.properties for datasource and persistence configuration
- Depends on: Step 10, Step 23, Step 24, Step 25
- Verify: File no longer exists

## Verification

- Build: `mvn clean compile`
- Test: Tests are currently skipped (maven.test.skip=true in pom.xml). After migration, enable tests and run: `mvn test`
- Blackbox: 
  1. Start PostgreSQL: `podman run --name myPostgresDb -p 5432:5432 -e POSTGRES_USER=postgresUser -e POSTGRES_PASSWORD=postgresPW -e POSTGRES_DB=postgresDB -d postgres`
  2. Start Quarkus in dev mode: `mvn quarkus:dev`
  3. Navigate to http://localhost:8080/services (or base URL if RestApplication removed)
  4. Test REST endpoints for products, cart, and orders
  5. Verify checkout process triggers message flow from ShoppingCartOrderProcessor (orders emitter) to OrderServiceMDB (orders consumer)
  6. Check database for persisted orders and inventory updates
  7. Verify Flyway migrations executed successfully at startup

## Notes

1. **Keycloak Integration**: The original application uses Keycloak for authentication. Quarkus has excellent Keycloak/OIDC support via `quarkus-oidc` extension. This will need to be added and configured in application.properties after basic migration is complete.

2. **Messaging Broker**: The migration uses in-memory channels for simplicity. For production or clustered deployment, configure external messaging (Kafka, AMQP) by changing the connector in application.properties:
   ```
   mp.messaging.outgoing.orders.connector=smallrye-kafka
   mp.messaging.incoming.orders.connector=smallrye-kafka
   ```

3. **Static Web Content**: The original WAR has webapp/ directory with AngularJS frontend. In Quarkus, static resources should be in src/main/resources/META-INF/resources/. Move webapp/ contents there or serve separately.

4. **Session State**: ShoppingCartService was @Stateful, implying server-side session state. In Quarkus, consider using external session store (Redis, Infinispan) or redesigning to stateless with client-side session tokens.

5. **Clustering**: Original deployment used JBoss clustering (standalone-full-ha.xml). Quarkus clustering requires Infinispan or other distributed cache solutions. Add quarkus-infinispan-client extension if clustering is needed.

6. **Remote EJB Clients**: If external applications invoke ShippingService via remote EJB, they must be updated to call the new REST endpoint after migration.

7. **WebLogic Classes**: The weblogic.* packages (ApplicationLifecycleListener, NonCatalogLogger) appear to be legacy/test code and may not be actively used. Verify if these can be deleted entirely.

8. **Flyway Version**: Consider updating Flyway to a newer version compatible with Quarkus 3 (e.g., 9.x) for better support and security updates.
