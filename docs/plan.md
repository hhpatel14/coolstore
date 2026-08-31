# Migration Plan

## Goal
Migrate the CoolStore monolith application from Java EE 7 on JBoss EAP 7.4 to Quarkus 3

## Source → Target
Java EE 7 / JBoss EAP 7.4 → Quarkus 3

## Scope
- Files affected: 35
- Estimated complexity: High
- Hardest areas: 
  - JMS to Reactive Messaging conversion (OrderServiceMDB, InventoryNotificationMDB, ShoppingCartOrderProcessor)
  - JNDI to CDI injection migration (ShoppingCartService, InventoryNotificationMDB)
  - Remote EJB to REST conversion (ShippingService)

## Key Decisions Applied
1. **Reactive Messaging Channel Names**: Using the JMS topic/queue names (e.g., "orders") as MicroProfile Reactive Messaging channel names to maintain consistency with existing configuration.
2. **Session Scope for Cart**: CartEndpoint uses @SessionScoped which requires activating the quarkus-undertow extension. However, for better cloud-native practices, we'll keep @SessionScoped but document this as a known technical debt that may need future refactoring to stateless design.
3. **Hibernate Sequence Strategy**: Entities using @GeneratedValue will need sequence migration. We'll document the need to run DDL generation or manually create sequences with the pattern `<entity_name>_seq`.
4. **Remote EJB to REST**: ShippingService marked as @Remote will be converted to a JAX-RS REST endpoint, as remote EJBs are not supported in Quarkus.

## Approach

### Phase 1: Build Configuration
Update pom.xml to adopt Quarkus 3 BOM, plugins (Quarkus Maven, Compiler, Surefire, Failsafe), and change packaging from WAR to JAR. Add native profile for native compilation support.

### Phase 2: Configuration Files
Convert persistence.xml configuration to application.properties (datasource and Hibernate settings). Delete beans.xml and web.xml as they are no longer needed. Create application.properties with all necessary Quarkus configurations including datasource, Hibernate ORM, and reactive messaging.

### Phase 3: Data Models
Update entity classes (Order, OrderItem) to handle Hibernate 6 sequence naming changes. These are simple changes requiring no structural modifications.

### Phase 4: Persistence Layer
Migrate Resources.java to remove @PersistenceContext and @Produces annotations in favor of direct @Inject for EntityManager. This is a simple refactoring.

### Phase 5: Service Layer - Simple EJB to CDI
Convert @Stateless EJBs (CatalogService, OrderService, ProductService, ShippingService, ShoppingCartOrderProcessor) and @Stateful EJB (ShoppingCartService) to CDI beans with appropriate scopes. Add @Transactional annotations where needed.

### Phase 6: Service Layer - Complex JMS/JNDI
Convert message-driven beans (OrderServiceMDB, InventoryNotificationMDB) from JMS to Reactive Messaging. Replace JNDI lookups with CDI injection. Convert JMS Topic publishing to MicroProfile Emitters.

### Phase 7: REST Layer
Update REST endpoints to remove JAX-RS Application class (RestApplication). Convert ShippingService from Remote EJB to REST endpoint. Update namespace imports from javax to jakarta.

### Phase 8: Utilities
Update utility classes (DataBaseMigrationStartup, Producers) with @Transactional and CDI scope annotations. Remove unnecessary @Produces annotations.

### Phase 9: Cleanup
Delete obsolete files (beans.xml, web.xml, persistence.xml, RestApplication.java, ShippingServiceRemote.java) and WebLogic stub classes.

## Steps

### Step 1: Change packaging type from WAR to JAR
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Change `<packaging>war</packaging>` to `<packaging>jar</packaging>` on line 9
- Why: Quarkus applications use JAR packaging instead of WAR
- Depends on: none
- Verify: `grep '<packaging>jar</packaging>' pom.xml`

### Step 2: Add Quarkus BOM to pom.xml
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add the following to the `<properties>` section:
  ```xml
  <quarkus.platform.artifact-id>quarkus-bom</quarkus.platform.artifact-id>
  <quarkus.platform.group-id>io.quarkus.platform</quarkus.platform.group-id>
  <quarkus.platform.version>3.1.0.Final</quarkus.platform.version>
  <compiler-plugin.version>3.10.1</compiler-plugin.version>
  <maven.compiler.release>11</maven.compiler.release>
  <surefire-plugin.version>3.0.0</surefire-plugin.version>
  ```
  And add to the pom after `</properties>`:
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
- Why: Quarkus BOM manages versions of Quarkus dependencies centrally
- Depends on: Step 1
- Verify: `grep 'quarkus-bom' pom.xml`

### Step 3: Replace Java EE dependencies with Quarkus extensions
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Remove javax/javaee-web-api and javaee-api dependencies. Replace with Quarkus extensions:
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
  Remove jboss-jms-api and jboss-rmi-api dependencies (replaced by reactive messaging)
- Why: Quarkus uses its own extensions instead of Java EE APIs
- Depends on: Step 2
- Verify: `grep 'quarkus-hibernate-orm-panache' pom.xml && ! grep 'javaee-api' pom.xml`

### Step 4: Add Quarkus Maven plugin
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Replace the existing `<build>` section with:
  ```xml
  <build>
    <finalName>coolstore</finalName>
    <plugins>
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
      <plugin>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>${compiler-plugin.version}</version>
        <configuration>
          <compilerArgs>
            <arg>-parameters</arg>
          </compilerArgs>
        </configuration>
      </plugin>
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
    </plugins>
  </build>
  ```
- Why: Quarkus requires its own Maven plugin for build, dev mode, and native compilation
- Depends on: Step 3
- Verify: `grep 'quarkus-maven-plugin' pom.xml`

### Step 5: Add native profile to pom.xml
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add after the `</build>` section:
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
- Why: Enables native compilation with GraalVM
- Depends on: Step 4
- Verify: `grep 'quarkus.package.type' pom.xml`

### Step 6: Create Quarkus application.properties
- Phase: Configuration Files
- File: src/main/resources/application.properties
- Action: CREATE
- What to do: Create file with:
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
  
  # Reactive Messaging - Orders topic
  mp.messaging.incoming.orders.connector=smallrye-in-memory
  mp.messaging.outgoing.orders.connector=smallrye-in-memory
  
  # HTTP configuration
  quarkus.http.port=8080
  quarkus.http.test-port=8081
  
  # Session configuration for shopping cart
  quarkus.servlet.context-path=/
  ```
- Why: Quarkus uses application.properties for all configuration instead of XML files
- Depends on: Step 1
- Verify: File exists with datasource properties

### Step 7: Update CatalogItemEntity imports
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java
- Action: MODIFY
- What to do: Replace all `javax.persistence` imports with `jakarta.persistence` imports
- Why: Quarkus 3 uses Jakarta EE 10 which moved from javax to jakarta namespace
- Depends on: none
- Verify: `grep 'jakarta.persistence' src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java`

### Step 8: Update InventoryEntity imports
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/InventoryEntity.java
- Action: MODIFY
- What to do: Replace all `javax.persistence` imports with `jakarta.persistence` imports
- Why: Quarkus 3 uses Jakarta EE 10 which moved from javax to jakarta namespace
- Depends on: none
- Verify: `grep 'jakarta.persistence' src/main/java/com/redhat/coolstore/model/InventoryEntity.java`

### Step 9: COMPLEX - Update Order entity with sequence configuration
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/Order.java
- Action: MODIFY
- What to do:
  - BEFORE: `javax.persistence` imports and `@GeneratedValue` without strategy
  - AFTER: `jakarta.persistence` imports and explicit sequence configuration
  - Specific changes:
    1. Replace all `javax.persistence` imports with `jakarta.persistence`
    2. Update `@GeneratedValue` annotation on line 23 to:
       ```java
       @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_seq")
       @SequenceGenerator(name = "order_seq", sequenceName = "order_seq", allocationSize = 1)
       ```
    3. Add import: `import jakarta.persistence.SequenceGenerator;`
- Why: Hibernate 6 changed default sequence naming; explicit configuration prevents runtime errors
- Depends on: none
- Verify: `grep 'SequenceGenerator' src/main/java/com/redhat/coolstore/model/Order.java`

### Step 10: COMPLEX - Update OrderItem entity with sequence configuration
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/OrderItem.java
- Action: MODIFY
- What to do:
  - BEFORE: `javax.persistence` imports and `@GeneratedValue` without strategy
  - AFTER: `jakarta.persistence` imports and explicit sequence configuration
  - Specific changes:
    1. Replace all `javax.persistence` imports with `jakarta.persistence`
    2. Update `@GeneratedValue` annotation to:
       ```java
       @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "orderitem_seq")
       @SequenceGenerator(name = "orderitem_seq", sequenceName = "orderitem_seq", allocationSize = 1)
       ```
    3. Add import: `import jakarta.persistence.SequenceGenerator;`
- Why: Hibernate 6 changed default sequence naming; explicit configuration prevents runtime errors
- Depends on: none
- Verify: `grep 'SequenceGenerator' src/main/java/com/redhat/coolstore/model/OrderItem.java`

### Step 11: Update Product imports
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/Product.java
- Action: MODIFY
- What to do: Replace all `javax.persistence` imports with `jakarta.persistence` imports
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: none
- Verify: `grep 'jakarta.persistence' src/main/java/com/redhat/coolstore/model/Product.java`

### Step 12: Update Promotion imports
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/Promotion.java
- Action: MODIFY
- What to do: Replace all `javax.persistence` imports with `jakarta.persistence` imports
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: none
- Verify: `grep 'jakarta.persistence' src/main/java/com/redhat/coolstore/model/Promotion.java`

### Step 13: Update ShoppingCart imports
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/ShoppingCart.java
- Action: MODIFY
- What to do: Replace any `javax` imports with `jakarta` equivalents
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: none
- Verify: `! grep 'import javax' src/main/java/com/redhat/coolstore/model/ShoppingCart.java || grep 'jakarta' src/main/java/com/redhat/coolstore/model/ShoppingCart.java`

### Step 14: Update ShoppingCartItem imports
- Phase: Data Models
- File: src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java
- Action: MODIFY
- What to do: Replace any `javax` imports with `jakarta` equivalents
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: none
- Verify: `! grep 'import javax' src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java || grep 'jakarta' src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java`

### Step 15: COMPLEX - Migrate Resources.java persistence producer
- Phase: Persistence Layer
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do:
  - BEFORE: Class with `@PersistenceContext` and `@Produces` for EntityManager
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
  - AFTER: Remove this class entirely, or simplify to empty class
  - Specific changes:
    1. Replace `javax.enterprise` imports with `jakarta.enterprise`
    2. Replace `javax.persistence` imports with `jakarta.persistence`
    3. Remove `@PersistenceContext` annotation (line 9)
    4. Remove `@Produces` annotation (line 12)
    5. Remove the getEntityManager() method
    6. Keep the class as an empty placeholder or delete (Note: We'll keep as placeholder in case it's referenced elsewhere)
- Why: In Quarkus, EntityManager is automatically available for injection without a producer; @Produces on EntityManager is illegal
- Depends on: Step 6
- Verify: `! grep '@Produces' src/main/java/com/redhat/coolstore/persistence/Resources.java`

### Step 16: Convert CatalogService from @Stateless to @ApplicationScoped
- Phase: Service Layer - Simple EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless;` with `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
  - Replace `import javax.persistence.EntityManager;` with `import jakarta.persistence.EntityManager;`
  - Replace `import javax.persistence.criteria.*` with `import jakarta.persistence.criteria.*`
  - Replace `@Stateless` annotation with `@ApplicationScoped`
  - Add `@Transactional` annotation to the class
  - Add import: `import jakarta.transaction.Transactional;`
- Why: Quarkus uses CDI scopes instead of EJB annotations; @Transactional needed for EntityManager operations
- Depends on: Step 15
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/CatalogService.java && grep '@Transactional' src/main/java/com/redhat/coolstore/service/CatalogService.java`

### Step 17: Convert OrderService from @Stateless to @ApplicationScoped
- Phase: Service Layer - Simple EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless;` with `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
  - Replace `import javax.persistence.EntityManager;` with `import jakarta.persistence.EntityManager;`
  - Replace `@Stateless` annotation with `@ApplicationScoped`
  - Add `@Transactional` annotation to the class
  - Add import: `import jakarta.transaction.Transactional;`
- Why: Quarkus uses CDI scopes instead of EJB annotations; @Transactional needed for persist operations
- Depends on: Step 15
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/OrderService.java && grep '@Transactional' src/main/java/com/redhat/coolstore/service/OrderService.java`

### Step 18: Convert ProductService from @Stateless to @ApplicationScoped
- Phase: Service Layer - Simple EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless;` with `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
  - Replace `@Stateless` annotation with `@ApplicationScoped`
  - Add `@Transactional` annotation to the class (if it performs any database operations)
  - Add import: `import jakarta.transaction.Transactional;`
- Why: Quarkus uses CDI scopes instead of EJB annotations
- Depends on: Step 15
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ProductService.java`

### Step 19: Convert ShoppingCartService from @Stateful to @SessionScoped
- Phase: Service Layer - Simple EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateful;` with `import jakarta.enterprise.context.SessionScoped;`
  - Replace all other `javax` imports with `jakarta` equivalents
  - Replace `@Stateful` annotation with `@SessionScoped`
  - Add `@Transactional` annotation to the class
  - Add import: `import jakarta.transaction.Transactional;`
- Why: @Stateful EJBs typically map to @SessionScoped in Quarkus; @Transactional needed for EntityManager operations
- Depends on: Step 15
- Verify: `grep '@SessionScoped' src/main/java/com/redhat/coolstore/service/ShoppingCartService.java && grep '@Transactional' src/main/java/com/redhat/coolstore/service/ShoppingCartService.java`

### Step 20: COMPLEX - Replace JNDI lookups with CDI injection in ShoppingCartService
- Phase: Service Layer - Simple EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - BEFORE: JNDI InitialContext lookups for service injection
    ```java
    InitialContext ctx = new InitialContext();
    ProductService productService = (ProductService) ctx.lookup("java:app/ProductService");
    ```
  - AFTER: Direct CDI injection
    ```java
    @Inject
    ProductService productService;
    ```
  - Specific changes:
    1. Remove `import javax.naming.InitialContext;` and `import javax.naming.NamingException;`
    2. Add `@Inject ProductService productService;` field at class level
    3. Add `@Inject PromoService promoService;` field at class level
    4. Add `@Inject ShippingService shippingService;` field at class level
    5. Remove all `InitialContext` instantiation and `lookup()` calls
    6. Replace usages of looked-up services with injected fields
- Why: JNDI is not supported in Quarkus; use CDI @Inject instead
- Depends on: Step 19
- Verify: `! grep 'InitialContext' src/main/java/com/redhat/coolstore/service/ShoppingCartService.java && grep '@Inject' src/main/java/com/redhat/coolstore/service/ShoppingCartService.java`

### Step 21: Convert ShoppingCartOrderProcessor from @Stateless to @ApplicationScoped
- Phase: Service Layer - Simple EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless;` with `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
  - Replace `@Stateless` annotation with `@ApplicationScoped`
- Why: Quarkus uses CDI scopes instead of EJB annotations
- Depends on: Step 15
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java`

### Step 22: COMPLEX - Convert ShoppingCartOrderProcessor JMS to Reactive Messaging
- Phase: Service Layer - Complex JMS/JNDI
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - BEFORE: JMS Topic with JMSContext
    ```java
    @Inject
    private transient JMSContext context;
    
    @Resource(lookup = "java:/topic/orders")
    private Topic ordersTopic;
    
    public void process(ShoppingCart cart) {
        context.createProducer().send(ordersTopic, Transformers.shoppingCartToJson(cart));
    }
    ```
  - AFTER: MicroProfile Reactive Messaging Emitter
    ```java
    @Inject
    @Channel("orders")
    Emitter<String> ordersEmitter;
    
    public void process(ShoppingCart cart) {
        ordersEmitter.send(Transformers.shoppingCartToJson(cart));
    }
    ```
  - Specific changes:
    1. Remove imports: `javax.jms.JMSContext`, `javax.jms.Topic`, `javax.annotation.Resource`
    2. Add imports: `import org.eclipse.microprofile.reactive.messaging.Channel;`, `import org.eclipse.microprofile.reactive.messaging.Emitter;`
    3. Remove `JMSContext` field
    4. Remove `Topic` field and `@Resource` annotation
    5. Add `@Inject @Channel("orders") Emitter<String> ordersEmitter;` field
    6. Replace `context.createProducer().send(ordersTopic, ...)` with `ordersEmitter.send(...)`
- Why: JMS is not supported in Quarkus; use MicroProfile Reactive Messaging instead
- Depends on: Step 21
- Verify: `grep 'Emitter<String>' src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java && ! grep 'JMSContext' src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java`

### Step 23: COMPLEX - Convert OrderServiceMDB to Reactive Messaging
- Phase: Service Layer - Complex JMS/JNDI
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: @MessageDriven EJB with MessageListener
    ```java
    @MessageDriven(name = "OrderServiceMDB", activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "topic/orders"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
        @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge")})
    public class OrderServiceMDB implements MessageListener {
        public void onMessage(Message rcvMessage) { ... }
    }
    ```
  - AFTER: CDI bean with @Incoming method
    ```java
    @ApplicationScoped
    public class OrderServiceMDB {
        @Incoming("orders")
        public void onMessage(String orderStr) { ... }
    }
    ```
  - Specific changes:
    1. Remove imports: `javax.ejb.*`, `javax.jms.*`
    2. Add imports: `jakarta.enterprise.context.ApplicationScoped`, `org.eclipse.microprofile.reactive.messaging.Incoming`
    3. Remove `@MessageDriven` annotation and all `@ActivationConfigProperty` annotations
    4. Add `@ApplicationScoped` annotation to class
    5. Remove `implements MessageListener`
    6. Change `onMessage(Message rcvMessage)` signature to `onMessage(String orderStr)`
    7. Add `@Incoming("orders")` annotation to onMessage method
    8. Remove JMS message unwrapping code - method receives String directly
    9. Remove try-catch for JMSException
    10. Add `@Transactional` annotation to the class
    11. Add import: `import jakarta.transaction.Transactional;`
- Why: Message-driven EJBs are not supported in Quarkus; use Reactive Messaging @Incoming
- Depends on: Step 17, Step 16
- Verify: `grep '@Incoming("orders")' src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java && ! grep '@MessageDriven' src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java`

### Step 24: COMPLEX - Convert InventoryNotificationMDB to Reactive Messaging
- Phase: Service Layer - Complex JMS/JNDI
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: @MessageDriven EJB with MessageListener and JNDI lookups
  - AFTER: CDI bean with @Incoming and CDI injection
  - Specific changes:
    1. Remove imports: `javax.ejb.*`, `javax.jms.*`, `javax.naming.*`
    2. Add imports: `jakarta.enterprise.context.ApplicationScoped`, `org.eclipse.microprofile.reactive.messaging.Incoming`, `jakarta.inject.Inject`
    3. Remove `@MessageDriven` and replace with `@ApplicationScoped`
    4. Remove `implements MessageListener`
    5. Change onMessage method signature to accept String directly
    6. Add `@Incoming("<channel-name>")` annotation to onMessage (use appropriate channel name from activation config)
    7. Remove all JNDI InitialContext and lookup() code
    8. Replace JNDI lookups with `@Inject` fields for services
    9. Add `@Transactional` annotation to the class
- Why: Message-driven EJBs and JNDI are not supported in Quarkus
- Depends on: none
- Verify: `grep '@Incoming' src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java && ! grep 'InitialContext' src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java`

### Step 25: Convert PromoService imports
- Phase: Service Layer - Simple EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/PromoService.java
- Action: MODIFY
- What to do: Replace all `javax` imports with `jakarta` equivalents
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: none
- Verify: `! grep 'import javax' src/main/java/com/redhat/coolstore/service/PromoService.java || grep 'jakarta' src/main/java/com/redhat/coolstore/service/PromoService.java`

### Step 26: COMPLEX - Convert ShippingService from Remote EJB to REST endpoint
- Phase: Service Layer - Complex JMS/JNDI
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do:
  - BEFORE: @Stateless @Remote EJB
    ```java
    @Stateless
    @Remote
    public class ShippingService implements ShippingServiceRemote {
        public double calculateShipping(...) { ... }
    }
    ```
  - AFTER: JAX-RS REST endpoint
    ```java
    @ApplicationScoped
    @Path("/shipping")
    public class ShippingService {
        @POST
        @Path("/calculate")
        @Produces(MediaType.APPLICATION_JSON)
        @Consumes(MediaType.APPLICATION_JSON)
        public double calculateShipping(@QueryParam("...") ...) { ... }
    }
    ```
  - Specific changes:
    1. Remove `@Stateless` and `@Remote` annotations
    2. Remove `implements ShippingServiceRemote`
    3. Add `@ApplicationScoped` annotation
    4. Add `@Path("/shipping")` to the class
    5. Add `@Transactional` to the class
    6. Add `@POST`, `@Path("/calculate")`, `@Produces(MediaType.APPLICATION_JSON)` to calculateShipping method
    7. Update method parameters with `@QueryParam` annotations as needed
    8. Replace `javax` imports with `jakarta` equivalents
    9. Add imports: `jakarta.ws.rs.*`
- Why: Remote EJBs are not supported in Quarkus; convert to REST endpoints
- Depends on: none
- Verify: `grep '@Path' src/main/java/com/redhat/coolstore/service/ShippingService.java && ! grep '@Remote' src/main/java/com/redhat/coolstore/service/ShippingService.java`

### Step 27: Update CartEndpoint imports and annotations
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do:
  - Replace `import javax.enterprise.context.SessionScoped;` with `import jakarta.enterprise.context.SessionScoped;`
  - Replace `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
  - Replace `import javax.ws.rs.*` with `import jakarta.ws.rs.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 19, Step 20
- Verify: `grep 'import jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/CartEndpoint.java`

### Step 28: Update OrderEndpoint imports
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do: Replace all `javax` imports with `jakarta` equivalents
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 17
- Verify: `grep 'jakarta' src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java`

### Step 29: Update ProductEndpoint imports
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do: Replace all `javax` imports with `jakarta` equivalents
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 16
- Verify: `grep 'jakarta' src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java`

### Step 30: Update DataBaseMigrationStartup utility
- Phase: Utilities
- File: src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- Action: MODIFY
- What to do:
  - Replace all `javax` imports with `jakarta` equivalents
  - Add `@Transactional` annotation to the class
  - Add import: `import jakarta.transaction.Transactional;`
- Why: Quarkus 3 uses Jakarta EE namespace; @Transactional needed for database operations
- Depends on: none
- Verify: `grep '@Transactional' src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java`

### Step 31: Update Producers utility
- Phase: Utilities
- File: src/main/java/com/redhat/coolstore/utils/Producers.java
- Action: MODIFY
- What to do:
  - Replace all `javax` imports with `jakarta` equivalents
  - Remove `@Produces` annotation if present and no longer needed
  - Add `@ApplicationScoped` if not present
- Why: Quarkus 3 uses Jakarta EE namespace; some producers may not be needed
- Depends on: none
- Verify: `grep 'jakarta' src/main/java/com/redhat/coolstore/utils/Producers.java`

### Step 32: Update StartupListener utility
- Phase: Utilities
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do: Replace all `javax` imports with `jakarta` equivalents
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: none
- Verify: `grep 'jakarta' src/main/java/com/redhat/coolstore/utils/StartupListener.java || ! grep 'import javax' src/main/java/com/redhat/coolstore/utils/StartupListener.java`

### Step 33: Update Transformers utility
- Phase: Utilities
- File: src/main/java/com/redhat/coolstore/utils/Transformers.java
- Action: MODIFY
- What to do: Replace any `javax` imports with `jakarta` equivalents (if any)
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: none
- Verify: `! grep 'import javax' src/main/java/com/redhat/coolstore/utils/Transformers.java || grep 'jakarta' src/main/java/com/redhat/coolstore/utils/Transformers.java`

### Step 34: Delete beans.xml
- Phase: Cleanup
- File: src/main/webapp/WEB-INF/beans.xml
- Action: DELETE
- What to do: Delete this file - beans.xml is ignored in Quarkus and CDI is enabled by default
- Why: Quarkus ignores beans.xml and enables CDI automatically
- Depends on: Step 27, Step 28, Step 29
- Verify: `! test -f src/main/webapp/WEB-INF/beans.xml`

### Step 35: Delete web.xml
- Phase: Cleanup
- File: src/main/webapp/WEB-INF/web.xml
- Action: DELETE
- What to do: Delete this file - not needed for Quarkus applications
- Why: Quarkus does not use deployment descriptors
- Depends on: Step 27, Step 28, Step 29
- Verify: `! test -f src/main/webapp/WEB-INF/web.xml`

### Step 36: Delete persistence.xml
- Phase: Cleanup
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Delete this file - persistence configuration is now in application.properties
- Why: Quarkus uses application.properties for all configuration
- Depends on: Step 6, Step 16, Step 17
- Verify: `! test -f src/main/resources/META-INF/persistence.xml`

### Step 37: Delete RestApplication.java
- Phase: Cleanup
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: DELETE
- What to do: Delete this file - JAX-RS activation is no longer necessary in Quarkus
- Why: Quarkus automatically activates JAX-RS endpoints without requiring an Application class
- Depends on: Step 27, Step 28, Step 29
- Verify: `! test -f src/main/java/com/redhat/coolstore/rest/RestApplication.java`

### Step 38: Delete ShippingServiceRemote.java
- Phase: Cleanup
- File: src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java
- Action: DELETE
- What to do: Delete this file - no longer needed after converting to REST endpoint
- Why: Remote EJB interface is not needed for REST endpoints
- Depends on: Step 26
- Verify: `! test -f src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java`

### Step 39: Delete WebLogic stub classes
- Phase: Cleanup
- File: src/main/java/weblogic/application/ApplicationLifecycleEvent.java
- Action: DELETE
- What to do: Delete this file - WebLogic-specific stub class not needed in Quarkus
- Why: WebLogic classes are not relevant to Quarkus deployment
- Depends on: Step 1
- Verify: `! test -f src/main/java/weblogic/application/ApplicationLifecycleEvent.java`

### Step 40: Delete WebLogic ApplicationLifecycleListener stub
- Phase: Cleanup
- File: src/main/java/weblogic/application/ApplicationLifecycleListener.java
- Action: DELETE
- What to do: Delete this file - WebLogic-specific stub class not needed in Quarkus
- Why: WebLogic classes are not relevant to Quarkus deployment
- Depends on: Step 1
- Verify: `! test -f src/main/java/weblogic/application/ApplicationLifecycleListener.java`

### Step 41: Delete WebLogic NonCatalogLogger stub
- Phase: Cleanup
- File: src/main/java/weblogic/i18n/logging/NonCatalogLogger.java
- Action: DELETE
- What to do: Delete this file - WebLogic-specific stub class not needed in Quarkus
- Why: WebLogic classes are not relevant to Quarkus deployment
- Depends on: Step 1
- Verify: `! test -f src/main/java/weblogic/i18n/logging/NonCatalogLogger.java`

### Step 42: Create database sequence migration script
- Phase: Configuration Files
- File: src/main/resources/db/migration/V1_3__AddSequences.sql
- Action: CREATE
- What to do: Create file with:
  ```sql
  -- Create sequences for Hibernate 6 compatibility
  CREATE SEQUENCE IF NOT EXISTS order_seq START WITH 1 INCREMENT BY 1;
  CREATE SEQUENCE IF NOT EXISTS orderitem_seq START WITH 1 INCREMENT BY 1;
  ```
- Why: Hibernate 6 requires explicit sequences for entities using @GeneratedValue
- Depends on: Step 9, Step 10
- Verify: `test -f src/main/resources/db/migration/V1_3__AddSequences.sql`

## Verification

### Build
```bash
mvn clean package
```
Expected: Clean compilation with no errors. The build should produce `target/coolstore-runner.jar`

### Test
No existing tests found in the project. After migration, consider adding:
```bash
mvn test
```

### Blackbox
Based on README.md run instructions (adapted for Quarkus):

1. **Start PostgreSQL database:**
   ```bash
   podman run --name myPostgresDb \
      -p 5432:5432 \
      -e POSTGRES_USER=postgresUser \
      -e POSTGRES_PASSWORD=postgresPW \
      -e POSTGRES_DB=postgresDB \
      -d postgres
   ```

2. **Run Quarkus in dev mode:**
   ```bash
   mvn quarkus:dev
   ```

3. **Verify application starts:**
   - Navigate to http://localhost:8080
   - Verify the coolstore UI loads
   - Test browsing products
   - Test adding items to cart
   - Test checkout process (requires Keycloak - see README for setup)

4. **Verify reactive messaging:**
   - Complete a checkout
   - Check application logs for order processing messages
   - Verify order is saved to database
   - Verify inventory is updated

5. **Verify REST endpoints:**
   ```bash
   curl http://localhost:8080/api/products
   curl http://localhost:8080/api/cart/test-cart-id
   ```

## Notes

### Reactive Messaging Configuration
The migration uses SmallRye In-Memory connector for the "orders" channel as a starting point. In production, this should be replaced with a proper message broker like Apache Kafka or AMQP. Update `application.properties` with appropriate connector configuration:

For Kafka:
```properties
mp.messaging.incoming.orders.connector=smallrye-kafka
mp.messaging.incoming.orders.topic=orders
mp.messaging.outgoing.orders.connector=smallrye-kafka
mp.messaging.outgoing.orders.topic=orders
```

### Session Scope Consideration
CartEndpoint uses @SessionScoped which requires the quarkus-undertow extension. This works but is not optimal for cloud-native stateless applications. Consider future refactoring to use external session storage (Redis, database) or move to a stateless design.

### Keycloak Integration
The application uses Keycloak for authentication. After migration, you'll need to add the quarkus-oidc extension and configure it in application.properties if authentication is required:
```properties
quarkus.oidc.auth-server-url=http://localhost:8081/realms/eap
quarkus.oidc.client-id=<client-id>
quarkus.oidc.credentials.secret=<client-secret>
```

### Database Sequences
The Flyway migration script (V1_3__AddSequences.sql) creates sequences for Order and OrderItem entities. If the database already has existing data, the sequence start values should be set higher than the maximum existing IDs:
```sql
CREATE SEQUENCE order_seq START WITH <max_order_id + 1>;
```

### Known Issues
1. **WebLogic stubs removed**: The three WebLogic stub classes were deleted. If any code references them, compilation will fail and those references must be removed.
2. **Remote EJB clients**: Any external clients calling ShippingService as a remote EJB will need to be updated to call the new REST endpoint.
3. **JMS client compatibility**: The change from JMS to Reactive Messaging is internal to the application. External JMS clients publishing to the orders topic will not work without additional bridge configuration.
