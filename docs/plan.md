# Migration Plan

## Goal
Migrate a Java EE 7 monolith application (coolstore) from Java EE/WildFly to Quarkus 3

## Source → Target
Java EE 7 (WAR packaging, WildFly/WebLogic runtime) → Quarkus 3 (JAR packaging)

## Scope
- Files affected: 30 (27 Java files, 1 pom.xml, 1 persistence.xml, 1 application.properties to create)
- Estimated complexity: High
- Hardest areas:
  1. JMS message-driven beans (MDBs) migration to Reactive Messaging
  2. JNDI lookups replacement with CDI injection
  3. Remote EJB to REST service conversion

## Key Decisions Applied
1. **JMS to SmallRye Reactive Messaging**: Replace JMS Topics and MDBs with SmallRye Reactive Messaging channels using the in-memory connector for internal messaging, as this maintains the async messaging pattern without requiring an external message broker
2. **Remote EJB Elimination**: Convert ShippingService remote EJB to a local CDI bean with direct injection instead of REST, as it's used within the same application
3. **Persistence Configuration**: Move persistence.xml datasource configuration to Quarkus application.properties using the standard quarkus.datasource.* properties
4. **WebLogic Stubs**: Delete WebLogic-specific stub classes as they're not needed in Quarkus

## Approach
The migration follows a bottom-up dependency order across five phases:

1. **Phase 1 - Build Configuration**: Convert Maven pom.xml from WAR packaging to Quarkus JAR with BOM, plugins, and native profile
2. **Phase 2 - Models**: Update JPA entities with Quarkus-specific persistence annotations, replace javax.* with jakarta.*, handle named query migrations
3. **Phase 3 - Persistence & Configuration**: Migrate persistence.xml to application.properties, update Resources.java to use @Inject instead of @PersistenceContext, configure datasource
4. **Phase 4 - Services**: 
   - Replace @Stateless/@Stateful EJB annotations with CDI scopes
   - Add @Transactional to methods requiring transactions
   - Convert JMS MDBs to Reactive Messaging @Incoming methods
   - Replace JNDI lookups with CDI @Inject
   - Convert Remote EJB to local service
5. **Phase 5 - REST & Cleanup**:
   - Update REST endpoints (remove JAX-RS activation class)
   - Update utility classes
   - Delete deployment descriptors (web.xml, beans.xml)
   - Delete WebLogic stub classes

## Steps

### Step 1: Update Maven packaging to JAR
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Change `<packaging>war</packaging>` to `<packaging>jar</packaging>`
- Why: Quarkus applications use JAR packaging instead of WAR
- Depends on: none
- Verify: `grep '<packaging>jar</packaging>' pom.xml`

### Step 2: Add Quarkus BOM
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do:
  - Add the following in `<dependencyManagement><dependencies>` section:
    ```xml
    <dependency>
      <groupId>io.quarkus.platform</groupId>
      <artifactId>quarkus-bom</artifactId>
      <version>3.2.0.Final</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
    ```
- Why: Quarkus BOM manages versions of all Quarkus dependencies
- Depends on: Step 1
- Verify: `grep 'quarkus-bom' pom.xml`

### Step 3: Replace Java EE dependencies with Quarkus extensions
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do:
  - Remove dependencies: javax/javaee-web-api, javax/javaee-api, jboss-jms-api_2.0_spec, jboss-rmi-api_1.0_spec
  - Add Quarkus extensions (without version numbers, managed by BOM):
    ```xml
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
- Why: Replace Java EE APIs with Quarkus extensions for JPA, REST, CDI, and Reactive Messaging
- Depends on: Step 2
- Verify: `grep 'quarkus-hibernate-orm-panache' pom.xml && grep 'quarkus-smallrye-reactive-messaging' pom.xml`

### Step 4: Update Maven Compiler plugin configuration
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do:
  - Update maven-compiler-plugin version to 3.11.0
  - Update source and target to 17
  - Add parameters, annotationProcessorPaths for Quarkus:
    ```xml
    <plugin>
      <artifactId>maven-compiler-plugin</artifactId>
      <version>3.11.0</version>
      <configuration>
        <parameters>true</parameters>
        <source>17</source>
        <target>17</target>
      </configuration>
    </plugin>
    ```
- Why: Quarkus 3 requires Java 17 and parameter name retention
- Depends on: Step 3
- Verify: `grep '<source>17</source>' pom.xml && grep '<parameters>true</parameters>' pom.xml`

### Step 5: Add Quarkus Maven plugin
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do:
  - Add in `<build><plugins>` section:
    ```xml
    <plugin>
      <groupId>io.quarkus.platform</groupId>
      <artifactId>quarkus-maven-plugin</artifactId>
      <version>3.2.0.Final</version>
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
- Why: Required for Quarkus application build and dev mode
- Depends on: Step 4
- Verify: `grep 'quarkus-maven-plugin' pom.xml`

### Step 6: Add Maven Surefire plugin
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do:
  - Add in `<build><plugins>` section:
    ```xml
    <plugin>
      <artifactId>maven-surefire-plugin</artifactId>
      <version>3.0.0</version>
      <configuration>
        <systemPropertyVariables>
          <java.util.logging.manager>org.jboss.logmanager.LogManager</java.util.logging.manager>
          <maven.home>${maven.home}</maven.home>
        </systemPropertyVariables>
      </configuration>
    </plugin>
    ```
- Why: Required for running tests with Quarkus
- Depends on: Step 5
- Verify: `grep 'maven-surefire-plugin' pom.xml`

### Step 7: Add Maven Failsafe plugin
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do:
  - Add in `<build><plugins>` section:
    ```xml
    <plugin>
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
          <maven.home>${maven.home}</maven.home>
        </systemPropertyVariables>
      </configuration>
    </plugin>
    ```
- Why: Required for running integration tests with Quarkus
- Depends on: Step 6
- Verify: `grep 'maven-failsafe-plugin' pom.xml`

### Step 8: Remove maven-war-plugin
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Remove the maven-war-plugin declaration
- Why: No longer needed with JAR packaging
- Depends on: Step 7
- Verify: `! grep 'maven-war-plugin' pom.xml`

### Step 9: Add native build profile
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do:
  - Replace the TODO comment in `<profiles>` section with:
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
- Why: Enables Quarkus native compilation
- Depends on: Step 8
- Verify: `grep 'quarkus.package.type' pom.xml`

### Step 10: Update CatalogItemEntity imports
- Phase: Models
- File: src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` imports with `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 9
- Verify: `grep 'jakarta.persistence' src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java`

### Step 11: Update InventoryEntity imports
- Phase: Models
- File: src/main/java/com/redhat/coolstore/model/InventoryEntity.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` imports with `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 9
- Verify: `grep 'jakarta.persistence' src/main/java/com/redhat/coolstore/model/InventoryEntity.java`

### Step 12: Update Order entity imports
- Phase: Models
- File: src/main/java/com/redhat/coolstore/model/Order.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` imports with `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 9
- Verify: `grep 'jakarta.persistence' src/main/java/com/redhat/coolstore/model/Order.java`

### Step 13: Update OrderItem entity imports
- Phase: Models
- File: src/main/java/com/redhat/coolstore/model/OrderItem.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` imports with `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 9
- Verify: `grep 'jakarta.persistence' src/main/java/com/redhat/coolstore/model/OrderItem.java`

### Step 14: Update Product model imports
- Phase: Models
- File: src/main/java/com/redhat/coolstore/model/Product.java
- Action: MODIFY
- What to do: Replace all `javax.*` imports with `jakarta.*` imports (if any)
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 9
- Verify: Build compiles without javax.* import errors

### Step 15: Update Promotion model imports
- Phase: Models
- File: src/main/java/com/redhat/coolstore/model/Promotion.java
- Action: MODIFY
- What to do: Replace all `javax.*` imports with `jakarta.*` imports (if any)
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 9
- Verify: Build compiles without javax.* import errors

### Step 16: Update ShoppingCart model imports
- Phase: Models
- File: src/main/java/com/redhat/coolstore/model/ShoppingCart.java
- Action: MODIFY
- What to do: Replace all `javax.*` imports with `jakarta.*` imports (if any)
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 9
- Verify: Build compiles without javax.* import errors

### Step 17: Update ShoppingCartItem model imports
- Phase: Models
- File: src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java
- Action: MODIFY
- What to do: Replace all `javax.*` imports with `jakarta.*` imports (if any)
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 9
- Verify: Build compiles without javax.* import errors

### Step 18: Create Quarkus application.properties
- Phase: Persistence & Configuration
- File: src/main/resources/application.properties
- Action: CREATE
- What to do:
  - Create file with Quarkus datasource and Hibernate configuration:
    ```properties
    # Datasource configuration
    quarkus.datasource.db-kind=h2
    quarkus.datasource.username=sa
    quarkus.datasource.password=
    quarkus.datasource.jdbc.url=jdbc:h2:mem:coolstore;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    
    # Hibernate configuration
    quarkus.hibernate-orm.database.generation=none
    quarkus.hibernate-orm.log.sql=false
    quarkus.hibernate-orm.sql-load-script=no-file
    
    # Flyway configuration
    quarkus.flyway.migrate-at-start=true
    quarkus.flyway.baseline-on-migrate=true
    quarkus.flyway.locations=classpath:db/migration
    
    # Reactive Messaging - In-Memory connector for orders topic
    mp.messaging.outgoing.orders.connector=smallrye-in-memory
    mp.messaging.incoming.orders-in.connector=smallrye-in-memory
    mp.messaging.incoming.orders-in.merge=true
    
    # HTTP configuration
    quarkus.http.port=8080
    quarkus.http.test-port=8081
    ```
- Why: Quarkus uses application.properties instead of persistence.xml for configuration
- Depends on: Step 17
- Verify: `test -f src/main/resources/application.properties && grep 'quarkus.datasource' src/main/resources/application.properties`

### Step 19: COMPLEX - Update Resources.java to use CDI injection
- Phase: Persistence & Configuration
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do:
  - BEFORE: Uses @PersistenceContext and @Produces for EntityManager
    ```java
    import javax.enterprise.context.Dependent;
    import javax.enterprise.inject.Produces;
    import javax.persistence.EntityManager;
    import javax.persistence.PersistenceContext;
    
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
  - AFTER: Uses @Inject and removes @Produces (not needed in Quarkus)
    ```java
    import jakarta.enterprise.context.ApplicationScoped;
    import jakarta.enterprise.inject.Produces;
    import jakarta.inject.Inject;
    import jakarta.persistence.EntityManager;
    
    @ApplicationScoped
    public class Resources {
        @Inject
        EntityManager em;
        
        @Produces
        public EntityManager getEntityManager() {
            return em;
        }
    }
    ```
  - Specific changes:
    1. Replace `javax.enterprise.context.Dependent` with `jakarta.enterprise.context.ApplicationScoped`
    2. Replace `javax.enterprise.inject.Produces` with `jakarta.enterprise.inject.Produces`
    3. Replace `javax.persistence.EntityManager` with `jakarta.persistence.EntityManager`
    4. Add `jakarta.inject.Inject` import
    5. Replace `@PersistenceContext` with `@Inject`
- Why: Quarkus injects EntityManager via CDI @Inject, and @Produces for EntityManager is acceptable but @PersistenceContext is replaced
- Depends on: Step 18
- Verify: `grep '@Inject' src/main/java/com/redhat/coolstore/persistence/Resources.java && grep 'jakarta.persistence' src/main/java/com/redhat/coolstore/persistence/Resources.java`

### Step 20: COMPLEX - Update CatalogService from @Stateless to CDI with @Transactional
- Phase: Services
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do:
  - BEFORE: @Stateless EJB with implicit transactions
  - AFTER: CDI @ApplicationScoped bean with explicit @Transactional
  - Specific changes:
    1. Remove `import javax.ejb.Stateless;`
    2. Replace all `javax.*` imports with `jakarta.*` equivalents
    3. Add `import jakarta.enterprise.context.ApplicationScoped;`
    4. Add `import jakarta.transaction.Transactional;`
    5. Replace `@Stateless` with `@ApplicationScoped`
    6. Add `@Transactional` to the class level (entire class needs transactions)
- Why: Quarkus doesn't support EJBs; use CDI beans with @Transactional for transaction management
- Depends on: Step 19
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/CatalogService.java && grep '@Transactional' src/main/java/com/redhat/coolstore/service/CatalogService.java`

### Step 21: COMPLEX - Update OrderService from @Stateless to CDI with @Transactional
- Phase: Services
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do:
  - BEFORE: @Stateless EJB with implicit transactions
  - AFTER: CDI @ApplicationScoped bean with explicit @Transactional
  - Specific changes:
    1. Remove `import javax.ejb.Stateless;`
    2. Replace all `javax.*` imports with `jakarta.*` equivalents
    3. Add `import jakarta.enterprise.context.ApplicationScoped;`
    4. Add `import jakarta.transaction.Transactional;`
    5. Replace `@Stateless` with `@ApplicationScoped`
    6. Add `@Transactional` to the class level
- Why: Quarkus doesn't support EJBs; use CDI beans with @Transactional for transaction management
- Depends on: Step 19
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/OrderService.java && grep '@Transactional' src/main/java/com/redhat/coolstore/service/OrderService.java`

### Step 22: COMPLEX - Update ProductService from @Stateless to CDI with @Transactional
- Phase: Services
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do:
  - BEFORE: @Stateless EJB with implicit transactions
  - AFTER: CDI @ApplicationScoped bean with explicit @Transactional
  - Specific changes:
    1. Remove `import javax.ejb.Stateless;`
    2. Replace all `javax.*` imports with `jakarta.*` equivalents
    3. Add `import jakarta.enterprise.context.ApplicationScoped;`
    4. Add `import jakarta.transaction.Transactional;`
    5. Replace `@Stateless` with `@ApplicationScoped`
    6. Add `@Transactional` to the class level
- Why: Quarkus doesn't support EJBs; use CDI beans with @Transactional for transaction management
- Depends on: Step 19
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ProductService.java && grep '@Transactional' src/main/java/com/redhat/coolstore/service/ProductService.java`

### Step 23: Update PromoService imports
- Phase: Services
- File: src/main/java/com/redhat/coolstore/service/PromoService.java
- Action: MODIFY
- What to do: Replace all `javax.*` imports with `jakarta.*` imports
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 19
- Verify: `grep 'jakarta' src/main/java/com/redhat/coolstore/service/PromoService.java || echo 'No imports to change'`

### Step 24: COMPLEX - Convert ShippingService from Remote EJB to local CDI bean
- Phase: Services
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do:
  - BEFORE: @Stateless @Remote EJB implementing ShippingServiceRemote interface
  - AFTER: @ApplicationScoped CDI bean implementing ShippingServiceRemote interface
  - Specific changes:
    1. Remove `import javax.ejb.Remote;`
    2. Remove `import javax.ejb.Stateless;`
    3. Add `import jakarta.enterprise.context.ApplicationScoped;`
    4. Add `import jakarta.transaction.Transactional;`
    5. Replace `@Stateless @Remote` with `@ApplicationScoped @Transactional`
- Why: Quarkus doesn't support Remote EJBs; converted to local CDI bean that can be injected directly
- Depends on: Step 19
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ShippingService.java && ! grep '@Remote' src/main/java/com/redhat/coolstore/service/ShippingService.java`

### Step 25: Update ShippingServiceRemote interface imports
- Phase: Services
- File: src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java
- Action: MODIFY
- What to do: Remove any `javax.ejb.*` imports (if present), keep it as a plain Java interface
- Why: Interface remains the same but doesn't need EJB annotations in Quarkus
- Depends on: Step 24
- Verify: `! grep 'javax.ejb' src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java || echo 'Clean'`

### Step 26: COMPLEX - Update ShoppingCartService from @Stateful to CDI and replace JNDI
- Phase: Services
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - BEFORE: @Stateful EJB with JNDI lookup for ShippingService
  - AFTER: @SessionScoped CDI bean with @Inject for ShippingService
  - Specific changes:
    1. Remove `import javax.ejb.Stateful;`
    2. Remove `import javax.naming.*;`
    3. Remove `import java.util.Hashtable;`
    4. Replace all `javax.*` imports with `jakarta.*` equivalents
    5. Add `import jakarta.enterprise.context.SessionScoped;`
    6. Add `import jakarta.transaction.Transactional;`
    7. Replace `@Stateful` with `@SessionScoped`
    8. Add `@Inject ShippingServiceRemote shippingService;` field
    9. Remove the `lookupShippingServiceRemote()` method entirely
    10. Replace all calls to `lookupShippingServiceRemote().calculateShipping(sc)` with `shippingService.calculateShipping(sc)`
    11. Replace all calls to `lookupShippingServiceRemote().calculateShippingInsurance(sc)` with `shippingService.calculateShippingInsurance(sc)`
- Why: Quarkus doesn't support JNDI lookups or @Stateful; use CDI @Inject and @SessionScoped for stateful behavior per user session
- Depends on: Step 24, Step 25
- Verify: `grep '@SessionScoped' src/main/java/com/redhat/coolstore/service/ShoppingCartService.java && grep '@Inject' src/main/java/com/redhat/coolstore/service/ShoppingCartService.java | grep -q ShippingServiceRemote && ! grep 'InitialContext' src/main/java/com/redhat/coolstore/service/ShoppingCartService.java`

### Step 27: COMPLEX - Convert ShoppingCartOrderProcessor from JMS to Reactive Messaging
- Phase: Services
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - BEFORE: @Stateless EJB using JMS Topic with @Resource lookup
  - AFTER: @ApplicationScoped CDI bean using Reactive Messaging @Channel Emitter
  - Specific changes:
    1. Remove all JMS imports: `javax.jms.*`, `javax.annotation.Resource`
    2. Replace `javax.*` imports with `jakarta.*` equivalents
    3. Add `import org.eclipse.microprofile.reactive.messaging.Channel;`
    4. Add `import org.eclipse.microprofile.reactive.messaging.Emitter;`
    5. Add `import jakarta.enterprise.context.ApplicationScoped;`
    6. Replace `@Stateless` with `@ApplicationScoped`
    7. Remove `@Inject private transient JMSContext context;`
    8. Remove `@Resource(lookup = "java:/topic/orders") private Topic ordersTopic;`
    9. Add `@Inject @Channel("orders") Emitter<String> ordersEmitter;`
    10. Replace `context.createProducer().send(ordersTopic, Transformers.shoppingCartToJson(cart));` with `ordersEmitter.send(Transformers.shoppingCartToJson(cart));`
- Why: Quarkus doesn't support JMS; SmallRye Reactive Messaging provides async messaging via channels
- Depends on: Step 19
- Verify: `grep '@Channel' src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java && grep 'Emitter' src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java && ! grep 'JMSContext' src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java`

### Step 28: COMPLEX - Convert OrderServiceMDB from JMS MDB to Reactive Messaging
- Phase: Services
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: @MessageDriven EJB implementing MessageListener
  - AFTER: @ApplicationScoped CDI bean with @Incoming method
  - Specific changes:
    1. Remove all JMS and EJB imports: `javax.ejb.*`, `javax.jms.*`
    2. Replace remaining `javax.*` imports with `jakarta.*`
    3. Add `import org.eclipse.microprofile.reactive.messaging.Incoming;`
    4. Add `import jakarta.enterprise.context.ApplicationScoped;`
    5. Add `import jakarta.transaction.Transactional;`
    6. Remove `@MessageDriven(name = "OrderServiceMDB", activationConfig = {...})` annotation
    7. Remove `implements MessageListener`
    8. Add `@ApplicationScoped` and `@Transactional` to class
    9. Replace `public void onMessage(Message rcvMessage)` signature with `@Incoming("orders-in") public void onMessage(String orderStr)`
    10. Remove all the JMS message unwrapping code (TextMessage casting, msg.getBody())
    11. Simplify method body to directly process the String orderStr:
        ```java
        System.out.println("\nMessage recd !");
        System.out.println("Received order: " + orderStr);
        Order order = Transformers.jsonToOrder(orderStr);
        System.out.println("Order object is " + order);
        orderService.save(order);
        order.getItemList().forEach(orderItem -> {
            catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity());
        });
        ```
- Why: Quarkus doesn't support MDBs; use @Incoming to consume from Reactive Messaging channels
- Depends on: Step 20, Step 21, Step 27
- Verify: `grep '@Incoming' src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java && grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java && ! grep 'MessageDriven' src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java`

### Step 29: COMPLEX - Convert InventoryNotificationMDB from JMS MDB to Reactive Messaging
- Phase: Services
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: MessageListener with manual JMS setup and WebLogic JNDI
  - AFTER: @ApplicationScoped CDI bean with @Incoming method
  - Specific changes:
    1. Remove all JMS imports: `javax.jms.*`, `javax.naming.*`, `javax.rmi.*`
    2. Remove `import java.util.Hashtable;`
    3. Replace remaining `javax.*` imports with `jakarta.*`
    4. Add `import org.eclipse.microprofile.reactive.messaging.Incoming;`
    5. Add `import jakarta.enterprise.context.ApplicationScoped;`
    6. Remove `implements MessageListener`
    7. Remove all JMS/JNDI fields: JNDI_FACTORY, JMS_FACTORY, TOPIC, tcon, tsession, tsubscriber
    8. Add `@ApplicationScoped` to class
    9. Replace `public void onMessage(Message rcvMessage)` with `@Incoming("orders-in") public void onMessage(String orderStr)`
    10. Simplify method body - remove try/catch for JMS, remove TextMessage casting:
        ```java
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
        ```
    11. Remove `init()` and `close()` methods - not needed
    12. Remove `getInitialContext()` method - not needed
- Why: Quarkus doesn't support JMS or JNDI; use @Incoming to consume from Reactive Messaging channels
- Depends on: Step 20, Step 27
- Verify: `grep '@Incoming' src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java && grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java && ! grep 'InitialContext' src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java`

### Step 30: COMPLEX - Update DataBaseMigrationStartup from EJB Singleton to Quarkus startup
- Phase: Services
- File: src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- Action: MODIFY
- What to do:
  - BEFORE: @Singleton @Startup EJB with @Resource datasource injection
  - AFTER: CDI bean with @Observes StartupEvent
  - Specific changes:
    1. Remove EJB imports: `javax.ejb.*`, `javax.annotation.Resource`
    2. Replace `javax.*` imports with `jakarta.*`
    3. Add `import io.quarkus.runtime.StartupEvent;`
    4. Add `import jakarta.enterprise.context.ApplicationScoped;`
    5. Add `import jakarta.enterprise.event.Observes;`
    6. Remove `@Singleton`, `@Startup`, `@TransactionManagement` annotations
    7. Add `@ApplicationScoped` to class
    8. Replace `@Resource(mappedName = "java:jboss/datasources/CoolstoreDS")` with `@Inject`
    9. Replace `@PostConstruct private void startup()` with `void onStart(@Observes StartupEvent ev)`
    10. Keep Flyway migration logic unchanged
- Why: Quarkus doesn't support EJB Singleton; use CDI with StartupEvent observer for initialization
- Depends on: Step 19
- Verify: `grep '@Observes StartupEvent' src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java && grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java && ! grep '@Singleton' src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java`

### Step 31: Update Producers utility class
- Phase: Services
- File: src/main/java/com/redhat/coolstore/utils/Producers.java
- Action: MODIFY
- What to do:
  - Replace all `javax.enterprise.*` imports with `jakarta.enterprise.*`
  - Add `import jakarta.enterprise.context.ApplicationScoped;`
  - Add `@ApplicationScoped` annotation to the class
- Why: Quarkus 3 uses Jakarta EE namespace and CDI producers should be in a CDI bean
- Depends on: Step 19
- Verify: `grep 'jakarta.enterprise' src/main/java/com/redhat/coolstore/utils/Producers.java && grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/utils/Producers.java`

### Step 32: Update StartupListener utility class
- Phase: Services
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do: Replace all `javax.*` imports with `jakarta.*` or appropriate Quarkus alternatives
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 19
- Verify: Build compiles without javax.* import errors for this file

### Step 33: Update Transformers utility class
- Phase: Services
- File: src/main/java/com/redhat/coolstore/utils/Transformers.java
- Action: MODIFY
- What to do:
  - Replace all `javax.*` imports with `jakarta.*` imports
  - Add `import jakarta.enterprise.context.ApplicationScoped;`
  - Add `@ApplicationScoped` annotation to the class
- Why: Quarkus 3 uses Jakarta EE namespace and utilities should be CDI beans
- Depends on: Step 19
- Verify: `grep 'jakarta' src/main/java/com/redhat/coolstore/utils/Transformers.java`

### Step 34: Update CartEndpoint REST endpoint
- Phase: REST & Cleanup
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do:
  - Replace all `javax.ws.rs.*` imports with `jakarta.ws.rs.*`
  - Replace `javax.enterprise.context.SessionScoped` with `jakarta.enterprise.context.SessionScoped`
  - Replace `javax.inject.Inject` with `jakarta.inject.Inject`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 26
- Verify: `grep 'jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/CartEndpoint.java && grep 'jakarta.enterprise' src/main/java/com/redhat/coolstore/rest/CartEndpoint.java`

### Step 35: Update OrderEndpoint REST endpoint
- Phase: REST & Cleanup
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do: Replace all `javax.ws.rs.*` and `javax.inject.*` imports with `jakarta.*` equivalents
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 21
- Verify: `grep 'jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java`

### Step 36: Update ProductEndpoint REST endpoint
- Phase: REST & Cleanup
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do: Replace all `javax.ws.rs.*` and `javax.inject.*` imports with `jakarta.*` equivalents
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 22
- Verify: `grep 'jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java`

### Step 37: Remove RestApplication JAX-RS activator
- Phase: REST & Cleanup
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: DELETE
- What to do: Delete this file - Quarkus automatically registers REST endpoints
- Why: Quarkus doesn't require JAX-RS Application class; REST endpoints are auto-discovered
- Depends on: Step 34, Step 35, Step 36
- Verify: `! test -f src/main/java/com/redhat/coolstore/rest/RestApplication.java`

### Step 38: Delete persistence.xml
- Phase: REST & Cleanup
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Delete this file - configuration moved to application.properties
- Why: Quarkus uses application.properties for all configuration
- Depends on: Step 18
- Verify: `! test -f src/main/resources/META-INF/persistence.xml`

### Step 39: Delete beans.xml
- Phase: REST & Cleanup
- File: src/main/webapp/WEB-INF/beans.xml
- Action: DELETE
- What to do: Delete this file - CDI is enabled by default in Quarkus
- Why: Quarkus enables CDI by default; beans.xml is not needed
- Depends on: Step 33
- Verify: `! test -f src/main/webapp/WEB-INF/beans.xml`

### Step 40: Delete web.xml
- Phase: REST & Cleanup
- File: src/main/webapp/WEB-INF/web.xml
- Action: DELETE
- What to do: Delete this file - not used in Quarkus
- Why: Quarkus doesn't use web.xml deployment descriptors
- Depends on: Step 33
- Verify: `! test -f src/main/webapp/WEB-INF/web.xml`

### Step 41: Delete WebLogic ApplicationLifecycleEvent stub
- Phase: REST & Cleanup
- File: src/main/java/weblogic/application/ApplicationLifecycleEvent.java
- Action: DELETE
- What to do: Delete this WebLogic-specific stub class
- Why: WebLogic proprietary classes not needed in Quarkus
- Depends on: Step 32
- Verify: `! test -f src/main/java/weblogic/application/ApplicationLifecycleEvent.java`

### Step 42: Delete WebLogic ApplicationLifecycleListener stub
- Phase: REST & Cleanup
- File: src/main/java/weblogic/application/ApplicationLifecycleListener.java
- Action: DELETE
- What to do: Delete this WebLogic-specific stub class
- Why: WebLogic proprietary classes not needed in Quarkus
- Depends on: Step 32
- Verify: `! test -f src/main/java/weblogic/application/ApplicationLifecycleListener.java`

### Step 43: Delete WebLogic NonCatalogLogger stub
- Phase: REST & Cleanup
- File: src/main/java/weblogic/i18n/logging/NonCatalogLogger.java
- Action: DELETE
- What to do: Delete this WebLogic-specific stub class
- Why: WebLogic proprietary classes not needed in Quarkus
- Depends on: Step 32
- Verify: `! test -f src/main/java/weblogic/i18n/logging/NonCatalogLogger.java`

## Verification

- Build: `mvn clean compile -Dquarkus.package.type=fast-jar`
- Test: Tests are currently skipped (maven.test.skip=true in pom.xml), but after migration can run: `mvn test`
- Blackbox: 
  1. Start the application: `mvn quarkus:dev`
  2. Access the UI: Open browser to `http://localhost:8080`
  3. Verify product catalog loads
  4. Add items to shopping cart
  5. Verify cart operations (add, update quantity, remove items)
  6. Complete checkout
  7. Verify order is processed (check console logs for "Message recd !" and "Received order:")
  8. Verify inventory notifications (check console for threshold warnings)

## Notes

1. **Reactive Messaging Channel Naming**: The "orders" channel is used for both outgoing (from ShoppingCartOrderProcessor) and incoming (to OrderServiceMDB and InventoryNotificationMDB). The in-memory connector configuration uses `merge=true` to allow multiple consumers on the same channel.

2. **Session Scope**: ShoppingCartService uses @SessionScoped to maintain per-user cart state. This requires HTTP session support which is available in Quarkus.

3. **Flyway Migration**: The existing Flyway database migration scripts in src/main/resources/db/migration/ will continue to work with Quarkus. The configuration has been migrated to application.properties.

4. **H2 Database**: The application uses H2 in-memory database. For production, update quarkus.datasource.* properties to use a persistent database like PostgreSQL.

5. **WebLogic Stubs**: The three WebLogic classes in the weblogic package appear to be stubs/placeholders and can be safely deleted. If StartupListener references them, those references should be removed.

6. **Static Web Content**: The webapp directory containing the AngularJS frontend should be moved to src/main/resources/META-INF/resources/ for Quarkus to serve it correctly. This is not included in the steps above as it's a resource relocation, not a migration change, but should be done before final deployment.

7. **Java Version**: Quarkus 3 requires Java 17. Ensure the build and runtime environments use Java 17 or later.

8. **Transaction Boundaries**: All services that perform database operations now have explicit @Transactional annotations. This makes transaction boundaries clear and explicit, which is a best practice in Quarkus.
