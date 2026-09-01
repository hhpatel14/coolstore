# Migration Plan

## Goal
Migrate a Java EE 7 application (coolstore monolith) from JBoss EAP 7.4 to Quarkus 3

## Source → Target
Java EE 7 (JBoss EAP 7.4) → Quarkus 3

## Scope
- Files affected: 45
- Estimated complexity: High
- Hardest areas:
  1. JMS to Reactive Messaging (OrderServiceMDB, InventoryNotificationMDB, ShoppingCartOrderProcessor)
  2. Remote EJB to REST conversion (ShippingService)
  3. JNDI lookups to CDI injection (ShoppingCartService, InventoryNotificationMDB)

## Key Decisions Applied
1. **Packaging**: Change from WAR to JAR packaging as Quarkus uses embedded server
2. **Persistence configuration**: Move from persistence.xml to application.properties for centralized configuration
3. **JMS to Reactive Messaging**: Replace JMS Topics with MicroProfile Reactive Messaging using Emitter and @Incoming annotations
4. **Remote EJB**: Convert ShippingService from Remote EJB to REST endpoint since remote EJBs are not supported
5. **Session scope**: Convert @SessionScoped CartEndpoint to use alternative state management (assume stateless REST with client-side session management)
6. **Hibernate sequence strategy**: Explicit sequence naming will be required for entity ID generation due to Hibernate 6.0 changes
7. **WebLogic proprietary classes**: Remove WebLogic-specific classes (ApplicationLifecycleListener, NonCatalogLogger) as they are not needed in Quarkus
8. **CDI beans.xml**: Remove beans.xml as its content is ignored in Quarkus

## Approach

**Phase 1 - Build Configuration**: Update pom.xml to use Quarkus BOM, plugins, and dependencies. Change packaging from WAR to JAR. Remove Java EE dependencies and add Quarkus extensions.

**Phase 2 - Configuration Files**: Create application.properties with datasource and Hibernate configuration. Remove persistence.xml, beans.xml, and web.xml as they are replaced by Quarkus configuration.

**Phase 3 - Model Layer**: Update JPA entities to use Jakarta namespace instead of javax. Address Hibernate sequence generation changes by adding explicit sequence names.

**Phase 4 - Persistence Layer**: Convert @PersistenceContext to @Inject for EntityManager. Remove @Produces annotation from Resources class.

**Phase 5 - Service Layer - EJB Conversion**: Replace @Stateless with @ApplicationScoped. Replace @Stateful with appropriate scope. Add @Transactional annotations for transaction management.

**Phase 6 - Service Layer - JMS to Reactive**: Convert message-driven beans to reactive messaging. Replace @MessageDriven with @ApplicationScoped and @Incoming. Replace JMS Topic injection with Emitter for message publishing.

**Phase 7 - Service Layer - JNDI Removal**: Remove JNDI lookups and replace with CDI injection. Convert Remote EJB (ShippingService) to local service.

**Phase 8 - REST Layer**: Update to Jakarta namespace. Remove JAX-RS Application class (RestApplication). Adjust session management approach.

**Phase 9 - Utilities**: Update utility classes to remove EJB annotations, update to Jakarta namespace, and adjust CDI producers.

**Phase 10 - Cleanup**: Remove WebLogic proprietary classes, legacy configuration files, and web.xml.

## Steps

### Step 1: Update pom.xml packaging type
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Change `<packaging>war</packaging>` to `<packaging>jar</packaging>`
- Why: Quarkus uses JAR packaging with embedded server, not WAR
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
- Why: Quarkus BOM and plugin version management
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
- Why: Centralize Quarkus dependency version management
- Depends on: Step 2
- Verify: dependencyManagement section exists with quarkus-bom

### Step 4: Replace Java EE dependencies with Quarkus extensions
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Remove javax/javaee dependencies and add Quarkus extensions:
  - Remove: javaee-web-api, javaee-api, jboss-jms-api_2.0_spec, jboss-rmi-api_1.0_spec
  - Add without version (managed by BOM):
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
      <artifactId>quarkus-jdbc-postgresql</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-smallrye-reactive-messaging</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-messaging-kafka</artifactId>
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
  - Keep flyway-core dependency
- Why: Replace Java EE APIs with Quarkus extensions
- Depends on: Step 3
- Verify: No javax/javaee-api dependencies remain, Quarkus extensions present

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
      <compilerArgs>
        <arg>-parameters</arg>
      </compilerArgs>
    </configuration>
  </plugin>
  ```
- Why: Quarkus requires -parameters flag for CDI
- Depends on: Step 2
- Verify: Compiler plugin has -parameters in compilerArgs

### Step 6: Remove maven-war-plugin and add Quarkus Maven plugin
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: 
  - Remove maven-war-plugin
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
- Why: Quarkus uses its own build plugin instead of WAR plugin
- Depends on: Step 1, Step 2
- Verify: quarkus-maven-plugin present, maven-war-plugin removed

### Step 7: Add Maven Surefire plugin
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add surefire plugin configuration:
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
- Depends on: Step 2
- Verify: Surefire plugin configured with Quarkus properties

### Step 8: Add Maven Failsafe plugin
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add failsafe plugin for integration tests:
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
- Verify: Failsafe plugin configured

### Step 9: Add native profile to pom.xml
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add native build profile:
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
- Why: Enable native compilation option
- Depends on: Step 2
- Verify: Native profile exists in profiles section

### Step 10: Create application.properties
- Phase: Configuration Files
- File: src/main/resources/application.properties
- Action: CREATE
- What to do: Create Quarkus configuration file with datasource and Hibernate settings:
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
  
  # Reactive Messaging - Kafka configuration for orders topic
  mp.messaging.outgoing.orders.connector=smallrye-kafka
  mp.messaging.outgoing.orders.topic=orders
  mp.messaging.outgoing.orders.value.serializer=org.apache.kafka.common.serialization.StringSerializer
  
  mp.messaging.incoming.orders.connector=smallrye-kafka
  mp.messaging.incoming.orders.topic=orders
  mp.messaging.incoming.orders.value.deserializer=org.apache.kafka.common.serialization.StringDeserializer
  
  # Kafka broker configuration
  kafka.bootstrap.servers=localhost:9092
  ```
- Why: Replace persistence.xml and datasource configuration with Quarkus properties
- Depends on: none
- Verify: File exists with datasource and Kafka configuration

### Step 11: Update CatalogItemEntity imports
- Phase: Model Layer
- File: src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` imports with `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: No javax.persistence imports remain

### Step 12: Update InventoryEntity imports
- Phase: Model Layer
- File: src/main/java/com/redhat/coolstore/model/InventoryEntity.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` imports with `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: No javax.persistence imports remain

### Step 13: COMPLEX - Update Order entity with explicit sequence
- Phase: Model Layer
- File: src/main/java/com/redhat/coolstore/model/Order.java
- Action: MODIFY
- What to do:
  - Replace all `javax.persistence.*` imports with `jakarta.persistence.*`
  - Update ID generation strategy to use explicit sequence:
    - BEFORE: `@GeneratedValue`
    - AFTER: `@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_seq")` and add `@SequenceGenerator(name = "order_seq", sequenceName = "order_seq", allocationSize = 1)`
- Why: Hibernate 6.0 changed implicit sequence naming; explicit naming prevents migration issues
- Depends on: Step 4
- Verify: jakarta.persistence imports, explicit sequence generator present

### Step 14: COMPLEX - Update OrderItem entity with explicit sequence
- Phase: Model Layer
- File: src/main/java/com/redhat/coolstore/model/OrderItem.java
- Action: MODIFY
- What to do:
  - Replace all `javax.persistence.*` imports with `jakarta.persistence.*`
  - Update ID generation strategy to use explicit sequence:
    - BEFORE: `@GeneratedValue`
    - AFTER: `@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "orderitem_seq")` and add `@SequenceGenerator(name = "orderitem_seq", sequenceName = "orderitem_seq", allocationSize = 1)`
- Why: Hibernate 6.0 changed implicit sequence naming; explicit naming prevents migration issues
- Depends on: Step 4
- Verify: jakarta.persistence imports, explicit sequence generator present

### Step 15: Update Product model imports
- Phase: Model Layer
- File: src/main/java/com/redhat/coolstore/model/Product.java
- Action: MODIFY
- What to do: Replace any `javax` imports with `jakarta` equivalents if present (check file for Serializable import)
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: No javax imports remain

### Step 16: Update Promotion model imports
- Phase: Model Layer
- File: src/main/java/com/redhat/coolstore/model/Promotion.java
- Action: MODIFY
- What to do: Replace any `javax` imports with `jakarta` equivalents if present
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: No javax imports remain

### Step 17: Update ShoppingCart model imports
- Phase: Model Layer
- File: src/main/java/com/redhat/coolstore/model/ShoppingCart.java
- Action: MODIFY
- What to do: Replace any `javax` imports with `jakarta` equivalents if present
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: No javax imports remain

### Step 18: Update ShoppingCartItem model imports
- Phase: Model Layer
- File: src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java
- Action: MODIFY
- What to do: Replace any `javax` imports with `jakarta` equivalents if present
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: No javax imports remain

### Step 19: COMPLEX - Convert Resources persistence producer
- Phase: Persistence Layer
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do:
  - Replace `javax.persistence.*` with `jakarta.persistence.*`
  - Replace `javax.enterprise.*` with `jakarta.enterprise.*`
  - Replace `@PersistenceContext` with `@Inject` for EntityManager
  - Remove `@Produces` annotation from getEntityManager method
  - BEFORE:
    ```java
    @PersistenceContext
    private EntityManager em;
    
    @Produces
    public EntityManager getEntityManager() {
        return em;
    }
    ```
  - AFTER:
    ```java
    @Inject
    EntityManager em;
    
    public EntityManager getEntityManager() {
        return em;
    }
    ```
- Why: Quarkus auto-configures EntityManager bean; @Produces on EntityManager is illegal in Quarkus
- Depends on: Step 10
- Verify: Uses @Inject for EntityManager, no @Produces annotation

### Step 20: Update CatalogService to CDI bean
- Phase: Service Layer - EJB Conversion
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do:
  - Replace `javax.ejb.Stateless` with `jakarta.enterprise.context.ApplicationScoped`
  - Replace `javax.inject.Inject` with `jakarta.inject.Inject`
  - Replace `javax.persistence.*` with `jakarta.persistence.*`
  - Add `@Transactional` annotation to the class
  - Add import: `import jakarta.transaction.Transactional;`
- Why: @Stateless EJBs convert to @ApplicationScoped CDI beans; transactions require explicit @Transactional
- Depends on: Step 4, Step 19
- Verify: No EJB annotations, @ApplicationScoped and @Transactional present

### Step 21: Update OrderService to CDI bean
- Phase: Service Layer - EJB Conversion
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do:
  - Replace `javax.ejb.Stateless` with `jakarta.enterprise.context.ApplicationScoped`
  - Replace `javax.inject.Inject` with `jakarta.inject.Inject`
  - Replace `javax.persistence.*` with `jakarta.persistence.*`
  - Add `@Transactional` annotation to the save method
  - Add import: `import jakarta.transaction.Transactional;`
- Why: @Stateless EJBs convert to @ApplicationScoped CDI beans; persist operations require @Transactional
- Depends on: Step 4, Step 19
- Verify: No EJB annotations, @ApplicationScoped present, save method has @Transactional

### Step 22: Update ProductService to CDI bean
- Phase: Service Layer - EJB Conversion
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do:
  - Replace `javax.ejb.Stateless` with `jakarta.enterprise.context.ApplicationScoped`
  - Replace `javax.inject.Inject` with `jakarta.inject.Inject`
  - Replace `javax.persistence.*` with `jakarta.persistence.*`
  - Add `@Transactional` annotation to the class
  - Add import: `import jakarta.transaction.Transactional;`
- Why: @Stateless EJBs convert to @ApplicationScoped CDI beans; transactions require explicit @Transactional
- Depends on: Step 4, Step 19
- Verify: No EJB annotations, @ApplicationScoped and @Transactional present

### Step 23: COMPLEX - Update ShippingService from Remote EJB to local CDI bean
- Phase: Service Layer - EJB Conversion
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do:
  - Remove `@Remote` annotation and import
  - Replace `javax.ejb.Stateless` with `jakarta.enterprise.context.ApplicationScoped`
  - Keep implements ShippingServiceRemote (interface still used locally)
  - Add import: `import jakarta.enterprise.context.ApplicationScoped;`
- Why: Remote EJBs not supported in Quarkus; convert to local CDI bean (REST conversion would require major refactoring of ShoppingCartService)
- Depends on: Step 4
- Verify: No @Remote or @Stateless annotations, @ApplicationScoped present

### Step 24: Update ShoppingCartOrderProcessor to CDI bean
- Phase: Service Layer - EJB Conversion
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - Replace `javax.ejb.Stateless` with `jakarta.enterprise.context.ApplicationScoped`
  - Replace `javax.annotation.Resource` with `jakarta.annotation.Resource`
  - Replace `javax.inject.Inject` with `jakarta.inject.Inject`
  - Add `@Transactional` annotation to the class
  - Add import: `import jakarta.transaction.Transactional;`
  - Add import: `import jakarta.enterprise.context.ApplicationScoped;`
- Why: @Stateless EJBs convert to @ApplicationScoped CDI beans
- Depends on: Step 4
- Verify: No EJB annotations, @ApplicationScoped and @Transactional present

### Step 25: COMPLEX - Update ShoppingCartService from Stateful to ApplicationScoped
- Phase: Service Layer - EJB Conversion
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - Replace `javax.ejb.Stateful` with `jakarta.enterprise.context.ApplicationScoped`
  - Replace `javax.inject.Inject` with `jakarta.inject.Inject`
  - Replace `javax.naming.*` imports with jakarta equivalents
  - Add `@Transactional` annotation to checkOutShoppingCart method
  - Add import: `import jakarta.transaction.Transactional;`
  - Add import: `import jakarta.enterprise.context.ApplicationScoped;`
  - Note: State management will change from server-side session to stateless (cart lookup by ID)
- Why: @Stateful EJBs convert to @ApplicationScoped; Quarkus recommends external state management
- Depends on: Step 4, Step 23
- Verify: No EJB annotations, @ApplicationScoped present

### Step 26: COMPLEX - Remove JNDI lookups from ShoppingCartService
- Phase: Service Layer - JNDI Removal
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - Remove lookupShippingServiceRemote() method
  - Inject ShippingService using CDI:
    - BEFORE: `return (ShippingServiceRemote) context.lookup(...);`
    - AFTER: Add field `@Inject ShippingService shippingService;`
  - Replace all calls to `lookupShippingServiceRemote().calculateShipping(...)` with `shippingService.calculateShipping(...)`
  - Replace all calls to `lookupShippingServiceRemote().calculateShippingInsurance(...)` with `shippingService.calculateShippingInsurance(...)`
  - Remove InitialContext and JNDI-related imports
- Why: JNDI lookups not supported in Quarkus; use CDI injection instead
- Depends on: Step 23, Step 25
- Verify: No JNDI lookups, ShippingService injected via @Inject

### Step 27: COMPLEX - Convert ShoppingCartOrderProcessor from JMS to Reactive Messaging
- Phase: Service Layer - JMS to Reactive
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - Remove JMS imports: `javax.jms.JMSContext`, `javax.jms.Topic`, `javax.annotation.Resource`
  - Add Reactive Messaging imports:
    ```java
    import io.smallrye.reactive.messaging.annotations.Channel;
    import io.smallrye.mutiny.Uni;
    import org.eclipse.microprofile.reactive.messaging.Emitter;
    ```
  - Replace JMS Topic with Emitter:
    - BEFORE:
      ```java
      @Inject
      private transient JMSContext context;
      
      @Resource(lookup = "java:/topic/orders")
      private Topic ordersTopic;
      ```
    - AFTER:
      ```java
      @Inject
      @Channel("orders")
      Emitter<String> ordersEmitter;
      ```
  - Update process method:
    - BEFORE: `context.createProducer().send(ordersTopic, Transformers.shoppingCartToJson(cart));`
    - AFTER: `ordersEmitter.send(Transformers.shoppingCartToJson(cart));`
- Why: JMS not supported in Quarkus; use Reactive Messaging with Emitter
- Depends on: Step 24, Step 10
- Verify: No JMS imports, Emitter with @Channel present

### Step 28: COMPLEX - Convert OrderServiceMDB to Reactive Messaging
- Phase: Service Layer - JMS to Reactive
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
  - Remove @MessageDriven annotation and activationConfig
  - Remove MessageListener interface implementation
  - Replace `javax.ejb.*` with `jakarta.enterprise.context.ApplicationScoped`
  - Replace `javax.inject.Inject` with `jakarta.inject.Inject`
  - Remove all JMS imports (`javax.jms.*`)
  - Add Reactive Messaging import: `import org.eclipse.microprofile.reactive.messaging.Incoming;`
  - Add `@Transactional` annotation and import
  - Convert onMessage method:
    - BEFORE:
      ```java
      @Override
      public void onMessage(Message rcvMessage) {
          TextMessage msg = (TextMessage) rcvMessage;
          String orderStr = msg.getBody(String.class);
          ...
      }
      ```
    - AFTER:
      ```java
      @Incoming("orders")
      @Transactional
      public void processOrder(String orderStr) {
          System.out.println("Received order: " + orderStr);
          Order order = Transformers.jsonToOrder(orderStr);
          ...
      }
      ```
- Why: @MessageDriven EJBs not supported; use @Incoming for reactive message consumption
- Depends on: Step 4, Step 10, Step 20, Step 21
- Verify: No JMS or EJB annotations, @ApplicationScoped and @Incoming present

### Step 29: COMPLEX - Convert InventoryNotificationMDB to Reactive Messaging
- Phase: Service Layer - JMS to Reactive
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
  - Remove @MessageDriven annotation and activationConfig
  - Remove MessageListener interface implementation
  - Replace `javax` imports with `jakarta` equivalents
  - Remove all JMS imports
  - Remove JNDI lookup code (InitialContext)
  - Add `@ApplicationScoped` annotation
  - Add `@Incoming` annotation for message handling
  - Add `@Transactional` annotation
  - Convert onMessage to reactive method signature accepting String payload
  - Replace JNDI lookups with CDI injection for any dependencies
- Why: @MessageDriven EJBs not supported; use @Incoming for reactive message consumption
- Depends on: Step 4, Step 10
- Verify: No JMS, EJB, or JNDI code; @ApplicationScoped and @Incoming present

### Step 30: Update PromoService imports
- Phase: Service Layer - EJB Conversion
- File: src/main/java/com/redhat/coolstore/service/PromoService.java
- Action: MODIFY
- What to do: Replace `javax.inject.Inject` with `jakarta.inject.Inject` and any other javax imports with jakarta equivalents
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: No javax imports remain

### Step 31: Update CartEndpoint to Jakarta namespace
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do:
  - Replace `javax.enterprise.context.SessionScoped` with `jakarta.enterprise.context.ApplicationScoped`
  - Replace `javax.inject.Inject` with `jakarta.inject.Inject`
  - Replace `javax.ws.rs.*` imports with `jakarta.ws.rs.*`
  - Note: Changed from @SessionScoped to @ApplicationScoped (stateless REST)
- Why: Quarkus 3 uses Jakarta namespace; session management handled client-side or externally
- Depends on: Step 4, Step 25
- Verify: All imports use jakarta namespace, @ApplicationScoped present

### Step 32: Update OrderEndpoint to Jakarta namespace
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do:
  - Replace `javax.inject.Inject` with `jakarta.inject.Inject`
  - Replace `javax.ws.rs.*` imports with `jakarta.ws.rs.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: All imports use jakarta namespace

### Step 33: Update ProductEndpoint to Jakarta namespace
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do:
  - Replace `javax.inject.Inject` with `jakarta.inject.Inject`
  - Replace `javax.ws.rs.*` imports with `jakarta.ws.rs.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: All imports use jakarta namespace

### Step 34: Update Transformers utility imports
- Phase: Utilities
- File: src/main/java/com/redhat/coolstore/utils/Transformers.java
- Action: MODIFY
- What to do: Replace `javax.json.*` imports with `jakarta.json.*`
- Why: Quarkus 3 uses Jakarta EE namespace for JSON-P
- Depends on: Step 4
- Verify: All imports use jakarta namespace

### Step 35: Update Producers utility
- Phase: Utilities
- File: src/main/java/com/redhat/coolstore/utils/Producers.java
- Action: MODIFY
- What to do:
  - Replace `javax.enterprise.inject.Produces` with `jakarta.enterprise.inject.Produces`
  - Replace `javax.enterprise.inject.spi.InjectionPoint` with `jakarta.enterprise.inject.spi.InjectionPoint`
  - Note: This @Produces for Logger is valid and recommended in Quarkus
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: All imports use jakarta namespace

### Step 36: Update DataBaseMigrationStartup
- Phase: Utilities
- File: src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- Action: MODIFY
- What to do:
  - Replace `javax.annotation.PostConstruct` with `jakarta.annotation.PostConstruct`
  - Replace `javax.ejb.Singleton` with `jakarta.enterprise.context.ApplicationScoped`
  - Replace `javax.ejb.Startup` with `io.quarkus.runtime.Startup`
  - Replace `javax.inject.Inject` with `jakarta.inject.Inject`
  - Add `@Transactional` annotation to methods that interact with database
  - Add import: `import jakarta.transaction.Transactional;`
- Why: Quarkus uses @Startup and @ApplicationScoped instead of EJB @Singleton/@Startup
- Depends on: Step 4
- Verify: No javax or EJB imports, Quarkus @Startup and @ApplicationScoped present

### Step 37: Update StartupListener
- Phase: Utilities
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do: Replace `javax` imports with `jakarta` equivalents if any present
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: All imports use jakarta namespace

### Step 38: Delete RestApplication
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: DELETE
- What to do: Delete this file - JAX-RS activation is automatic in Quarkus
- Why: Quarkus automatically activates JAX-RS; Application class is unnecessary
- Depends on: Step 31, Step 32, Step 33
- Verify: File no longer exists

### Step 39: Delete ShippingServiceRemote interface
- Phase: Cleanup
- File: src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java
- Action: DELETE
- What to do: Delete this file - no longer needed after converting from Remote EJB
- Why: Remote interface only needed for Remote EJBs; local CDI bean uses direct injection
- Depends on: Step 23, Step 26
- Verify: File no longer exists

### Step 40: Delete WebLogic ApplicationLifecycleEvent
- Phase: Cleanup
- File: src/main/java/weblogic/application/ApplicationLifecycleEvent.java
- Action: DELETE
- What to do: Delete this WebLogic proprietary class
- Why: WebLogic-specific code not needed in Quarkus
- Depends on: none
- Verify: File no longer exists

### Step 41: Delete WebLogic ApplicationLifecycleListener
- Phase: Cleanup
- File: src/main/java/weblogic/application/ApplicationLifecycleListener.java
- Action: DELETE
- What to do: Delete this WebLogic proprietary class
- Why: WebLogic-specific code not needed in Quarkus
- Depends on: none
- Verify: File no longer exists

### Step 42: Delete WebLogic NonCatalogLogger
- Phase: Cleanup
- File: src/main/java/weblogic/i18n/logging/NonCatalogLogger.java
- Action: DELETE
- What to do: Delete this WebLogic proprietary class
- Why: WebLogic-specific code not needed in Quarkus; use standard Java logging
- Depends on: none
- Verify: File no longer exists

### Step 43: Delete persistence.xml
- Phase: Cleanup
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Delete this file - replaced by application.properties
- Why: Quarkus uses application.properties for persistence configuration
- Depends on: Step 10
- Verify: File no longer exists

### Step 44: Delete beans.xml
- Phase: Cleanup
- File: src/main/webapp/WEB-INF/beans.xml
- Action: DELETE
- What to do: Delete this file - content is ignored in Quarkus
- Why: Quarkus ignores beans.xml descriptor content; CDI is enabled by default
- Depends on: none
- Verify: File no longer exists

### Step 45: Delete web.xml
- Phase: Cleanup
- File: src/main/webapp/WEB-INF/web.xml
- Action: DELETE
- What to do: Delete this file - not used in Quarkus
- Why: Quarkus doesn't use web.xml; configuration is in application.properties
- Depends on: none
- Verify: File no longer exists

## Verification

- Build: `mvn clean compile`
- Test: Tests are currently skipped (maven.test.skip=true), but can be enabled after migration with `mvn test`
- Blackbox: 
  1. Start PostgreSQL database: `podman run --name myPostgresDb -p 5432:5432 -e POSTGRES_USER=postgresUser -e POSTGRES_PASSWORD=postgresPW -e POSTGRES_DB=postgresDB -d postgres`
  2. Start Kafka (required for reactive messaging): `podman run -p 9092:9092 --name kafka -e KAFKA_ENABLE_KRAFT=yes -e KAFKA_CFG_PROCESS_ROLES=broker,controller -e KAFKA_CFG_CONTROLLER_LISTENER_NAMES=CONTROLLER -e KAFKA_CFG_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 -e KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT -e KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 -e KAFKA_BROKER_ID=1 -e KAFKA_CFG_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 -e ALLOW_PLAINTEXT_LISTENER=yes -d bitnami/kafka:latest`
  3. Start Keycloak on port 8081 (as per README)
  4. Start Quarkus application in dev mode: `mvn quarkus:dev`
  5. Navigate to http://localhost:8080
  6. Sign in with Keycloak user credentials
  7. Add items to cart and complete checkout process
  8. Verify order processing via logs (message consumption)
  9. Verify inventory updates after checkout

## Notes

1. **Session Management**: The original application uses `@SessionScoped` for CartEndpoint. In Quarkus, this has been changed to `@ApplicationScoped` with stateless REST design. Cart state is maintained via cart ID lookup. Consider implementing Redis or another external session store if true session management is required.

2. **JMS to Kafka**: The migration replaces JMS Topics with Kafka topics via Reactive Messaging. The topic name "orders" is preserved. Ensure Kafka is running before starting the application.

3. **Remote EJB**: ShippingService was converted from Remote EJB to local CDI bean rather than REST because it's only used internally by ShoppingCartService. This maintains the existing architecture with minimal refactoring.

4. **Database Sequences**: Explicit sequence generators added to Order and OrderItem entities. Ensure database schema creates these sequences (order_seq, orderitem_seq) or run with hibernate.hbm2ddl.auto to generate them.

5. **Flyway**: The existing Flyway migration scripts are preserved and will run on startup via `quarkus.flyway.migrate-at-start=true`.

6. **WebLogic Classes**: Removed weblogic.* package classes as they were proprietary placeholders and not actually used by the application logic.

7. **Packaging Change**: WAR packaging changed to JAR. The application no longer deploys to an external application server but runs as a standalone Quarkus application with embedded server.

8. **REST Path**: The application root was configured as "/services/rest" in RestApplication. In Quarkus, this can be configured via `quarkus.resteasy-reactive.path=/services/rest` in application.properties if needed.

9. **InventoryNotificationMDB**: This MDB needs channel configuration added to application.properties for whatever topic/queue it subscribes to (not clearly defined in analysis results - verify actual topic name from code).

10. **Build Output**: Final artifact changes from ROOT.war to monolith-1.0.0-SNAPSHOT-runner.jar (or just use `mvn quarkus:dev` for development).
