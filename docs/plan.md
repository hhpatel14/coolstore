# Migration Plan

## Goal
Migrate a Java EE 7 monolith application from JBoss EAP 7.4 to Quarkus 3

## Source → Target
Java EE 7 (JBoss EAP 7.4) → Quarkus 3

## Scope
- Files affected: 34
- Estimated complexity: High
- Hardest areas: 
  1. JMS message-driven beans to Reactive Messaging
  2. Remote EJB pattern to local CDI beans
  3. WebLogic-specific JNDI and logging code

## Key Decisions Applied
- **Remote EJB replacement**: The ShippingService uses Remote EJB with JNDI lookups. Decision: Convert to local CDI bean injection since Quarkus runs in a single JVM and doesn't support distributed EJBs. The remote interface will be removed and ShippingService will be injected directly via CDI.
- **JMS to Reactive Messaging**: MDB pattern will be replaced with Quarkus Reactive Messaging using SmallRye. Topic-based messaging will use in-memory channels for local processing.
- **WebLogic stub code**: The InventoryNotificationMDB contains WebLogic-specific initialization code that's not actually used (no @MessageDriven annotation). Decision: Remove this file entirely as it's non-functional placeholder code.
- **Session state**: The @Stateful ShoppingCartService will be converted to @ApplicationScoped with session management handled at the REST layer if needed, as Quarkus doesn't support stateful session beans.

## Approach

**Phase 1: Build Configuration**
- Replace Maven packaging from WAR to JAR
- Add Quarkus BOM and Maven plugin
- Replace Java EE dependencies with Quarkus extensions
- Add Quarkus compiler, Surefire, and Failsafe plugins
- Add native build profile

**Phase 2: Models and Persistence**
- Update JPA entity imports from javax.persistence to jakarta.persistence
- Add explicit sequence/table name generation for entities with @GeneratedValue
- Replace @PersistenceContext with @Inject for EntityManager
- Remove @Produces from EntityManager producer
- Convert persistence.xml to application.properties

**Phase 3: CDI and Utilities**
- Update CDI imports from javax to jakarta
- Update @Produces annotation usage per Quarkus requirements
- Remove beans.xml (no longer needed)

**Phase 4: Services Layer**
- Replace @Stateless with @ApplicationScoped
- Replace @Stateful with @ApplicationScoped
- Add @Transactional where needed for persistence operations
- Convert JMS message-driven beans to Reactive Messaging @Incoming methods
- Replace JMS Topic injection with @Channel Emitter for reactive messaging
- Remove JNDI lookups and replace with CDI injection
- Remove Remote EJB usage and convert to local CDI beans

**Phase 5: REST Layer**
- Update JAX-RS imports to jakarta
- Remove JAX-RS Application class (not needed in Quarkus)
- Update @SessionScoped endpoints if needed

**Phase 6: WebLogic Removal**
- Delete WebLogic stub classes
- Remove non-functional MDB stub

**Phase 7: Configuration and Deployment Descriptors**
- Remove web.xml (not needed)
- Configure datasource in application.properties
- Configure Reactive Messaging channels

## Steps

### Step 1: Update Maven packaging to JAR
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Change `<packaging>war</packaging>` to `<packaging>jar</packaging>`
- Why: Quarkus applications are packaged as JAR by default, not WAR
- Depends on: none
- Verify: `grep '<packaging>jar</packaging>' pom.xml`

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
        <version>3.2.0.Final</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
  ```
- Why: Quarkus BOM manages all Quarkus extension versions
- Depends on: Step 1
- Verify: `grep 'quarkus-bom' pom.xml`

### Step 3: Replace Java EE dependencies with Quarkus extensions
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Remove all javax/javaee dependencies and replace with:
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
    <artifactId>quarkus-arc</artifactId>
  </dependency>
  <dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-flyway</artifactId>
  </dependency>
  ```
  Remove: javaee-web-api, javaee-api, jboss-jms-api_2.0_spec, jboss-rmi-api_1.0_spec
- Why: Quarkus uses its own extensions instead of Java EE APIs
- Depends on: Step 2
- Verify: `grep 'quarkus-resteasy-reactive-jackson' pom.xml && ! grep 'javaee-web-api' pom.xml`

### Step 4: Add Quarkus Maven plugin
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add Quarkus Maven plugin to build plugins section:
  ```xml
  <plugin>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-maven-plugin</artifactId>
    <version>3.2.0.Final</version>
    <executions>
      <execution>
        <goals>
          <goal>build</goal>
        </goals>
      </execution>
    </executions>
  </plugin>
  ```
- Why: Required to build Quarkus applications
- Depends on: Step 3
- Verify: `grep 'quarkus-maven-plugin' pom.xml`

### Step 5: Update Maven Compiler plugin
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Update compiler plugin version to 3.11.0 and set source/target to 11 or higher:
  ```xml
  <plugin>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.11.0</version>
    <configuration>
      <source>11</source>
      <target>11</target>
      <parameters>true</parameters>
    </configuration>
  </plugin>
  ```
- Why: Quarkus requires Java 11+ and parameter names for CDI
- Depends on: Step 4
- Verify: `grep '<version>3.11.0</version>' pom.xml | grep -A5 compiler`

### Step 6: Add Maven Surefire plugin
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add Surefire plugin for testing:
  ```xml
  <plugin>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.0.0</version>
    <configuration>
      <systemPropertyVariables>
        <java.util.logging.manager>org.jboss.logmanager.LogManager</java.util.logging.manager>
      </systemPropertyVariables>
    </configuration>
  </plugin>
  ```
- Why: Required for running Quarkus tests
- Depends on: Step 5
- Verify: `grep 'maven-surefire-plugin' pom.xml`

### Step 7: Add Maven Failsafe plugin
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add Failsafe plugin for integration testing:
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
  </plugin>
  ```
- Why: Required for Quarkus integration tests
- Depends on: Step 6
- Verify: `grep 'maven-failsafe-plugin' pom.xml`

### Step 8: Add native build profile
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add native profile to profiles section:
  ```xml
  <profile>
    <id>native</id>
    <properties>
      <quarkus.package.type>native</quarkus.package.type>
    </properties>
  </profile>
  ```
- Why: Enables native executable builds
- Depends on: Step 7
- Verify: `grep '<id>native</id>' pom.xml`

### Step 9: Remove Maven WAR plugin
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Remove maven-war-plugin from build plugins
- Why: Not needed for JAR packaging
- Depends on: Step 8
- Verify: `! grep 'maven-war-plugin' pom.xml`

### Step 10: Update Order entity imports
- Phase: Models and Persistence
- File: src/main/java/com/redhat/coolstore/model/Order.java
- Action: MODIFY
- What to do: Replace all `javax.persistence` imports with `jakarta.persistence`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 3
- Verify: `grep 'import jakarta.persistence' src/main/java/com/redhat/coolstore/model/Order.java`

### Step 11: COMPLEX — Add explicit sequence name to Order entity
- Phase: Models and Persistence
- File: src/main/java/com/redhat/coolstore/model/Order.java
- Action: MODIFY
- What to do:
  - BEFORE: `@GeneratedValue` without strategy
  - AFTER: `@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_seq")` with `@SequenceGenerator(name = "order_seq", sequenceName = "order_id_seq", allocationSize = 1)`
  - Specific changes:
    1. Add import: `import jakarta.persistence.SequenceGenerator;` and `import jakarta.persistence.GenerationType;`
    2. Replace: `@GeneratedValue` with `@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_seq")`
    3. Add: `@SequenceGenerator(name = "order_seq", sequenceName = "order_id_seq", allocationSize = 1)` above the @Id annotation
- Why: Hibernate changed implicit sequence naming in newer versions; explicit names avoid migration issues
- Depends on: Step 10
- Verify: `grep '@SequenceGenerator' src/main/java/com/redhat/coolstore/model/Order.java`

### Step 12: Update OrderItem entity imports
- Phase: Models and Persistence
- File: src/main/java/com/redhat/coolstore/model/OrderItem.java
- Action: MODIFY
- What to do: Replace all `javax.persistence` imports with `jakarta.persistence`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 3
- Verify: `grep 'import jakarta.persistence' src/main/java/com/redhat/coolstore/model/OrderItem.java`

### Step 13: COMPLEX — Add explicit sequence name to OrderItem entity
- Phase: Models and Persistence
- File: src/main/java/com/redhat/coolstore/model/OrderItem.java
- Action: MODIFY
- What to do:
  - BEFORE: `@GeneratedValue` without strategy
  - AFTER: `@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_item_seq")` with `@SequenceGenerator(name = "order_item_seq", sequenceName = "order_item_id_seq", allocationSize = 1)`
  - Specific changes:
    1. Add import: `import jakarta.persistence.SequenceGenerator;` and `import jakarta.persistence.GenerationType;`
    2. Replace: `@GeneratedValue` with `@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_item_seq")`
    3. Add: `@SequenceGenerator(name = "order_item_seq", sequenceName = "order_item_id_seq", allocationSize = 1)` above the @Id annotation
- Why: Hibernate changed implicit sequence naming in newer versions; explicit names avoid migration issues
- Depends on: Step 12
- Verify: `grep '@SequenceGenerator' src/main/java/com/redhat/coolstore/model/OrderItem.java`

### Step 14: Update CatalogItemEntity imports
- Phase: Models and Persistence
- File: src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java
- Action: MODIFY
- What to do: Replace all `javax.persistence` imports with `jakarta.persistence`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 3
- Verify: `grep 'import jakarta.persistence' src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java`

### Step 15: Update InventoryEntity imports
- Phase: Models and Persistence
- File: src/main/java/com/redhat/coolstore/model/InventoryEntity.java
- Action: MODIFY
- What to do: Replace all `javax.persistence` imports with `jakarta.persistence`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 3
- Verify: `grep 'import jakarta.persistence' src/main/java/com/redhat/coolstore/model/InventoryEntity.java`

### Step 16: Update Product model
- Phase: Models and Persistence
- File: src/main/java/com/redhat/coolstore/model/Product.java
- Action: MODIFY
- What to do: Replace any `javax` imports with `jakarta` equivalents (if present)
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 3
- Verify: `! grep 'import javax' src/main/java/com/redhat/coolstore/model/Product.java || grep 'import jakarta' src/main/java/com/redhat/coolstore/model/Product.java`

### Step 17: Update Promotion model
- Phase: Models and Persistence
- File: src/main/java/com/redhat/coolstore/model/Promotion.java
- Action: MODIFY
- What to do: Replace any `javax` imports with `jakarta` equivalents (if present)
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 3
- Verify: `! grep 'import javax' src/main/java/com/redhat/coolstore/model/Promotion.java || grep 'import jakarta' src/main/java/com/redhat/coolstore/model/Promotion.java`

### Step 18: Update ShoppingCart model
- Phase: Models and Persistence
- File: src/main/java/com/redhat/coolstore/model/ShoppingCart.java
- Action: MODIFY
- What to do: Replace any `javax` imports with `jakarta` equivalents (if present)
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 3
- Verify: `! grep 'import javax' src/main/java/com/redhat/coolstore/model/ShoppingCart.java || grep 'import jakarta' src/main/java/com/redhat/coolstore/model/ShoppingCart.java`

### Step 19: Update ShoppingCartItem model
- Phase: Models and Persistence
- File: src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java
- Action: MODIFY
- What to do: Replace any `javax` imports with `jakarta` equivalents (if present)
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 3
- Verify: `! grep 'import javax' src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java || grep 'import jakarta' src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java`

### Step 20: COMPLEX — Replace @PersistenceContext with @Inject in Resources
- Phase: Models and Persistence
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do:
  - BEFORE: `@PersistenceContext private EntityManager em;`
  - AFTER: `@Inject EntityManager em;`
  - Specific changes:
    1. Remove import: `import jakarta.persistence.PersistenceContext;`
    2. Replace `@PersistenceContext` with `@Inject`
    3. Update other imports from javax to jakarta
- Why: Quarkus injects EntityManager via CDI, not @PersistenceContext
- Depends on: Step 3
- Verify: `grep '@Inject' src/main/java/com/redhat/coolstore/persistence/Resources.java | grep EntityManager`

### Step 21: COMPLEX — Remove @Produces from EntityManager in Resources
- Phase: Models and Persistence
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do:
  - BEFORE: `@Produces public EntityManager getEntityManager() { return em; }`
  - AFTER: Remove the @Produces annotation and the getEntityManager() method entirely
  - Specific changes:
    1. Remove the @Produces annotation
    2. Remove the getEntityManager() method
    3. Keep the @Inject EntityManager em field
    4. Change class to have EntityManager injected directly where needed
- Why: In Quarkus, EntityManager cannot be produced; it must be injected directly via @Inject
- Depends on: Step 20
- Verify: `! grep '@Produces' src/main/java/com/redhat/coolstore/persistence/Resources.java`

### Step 22: Delete Resources.java file
- Phase: Models and Persistence
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: DELETE
- What to do: Delete this file — EntityManager will be injected directly in services
- Why: After removing @Produces, this file serves no purpose; Quarkus provides EntityManager directly
- Depends on: Step 21
- Verify: `! test -f src/main/java/com/redhat/coolstore/persistence/Resources.java`

### Step 23: Create application.properties for persistence configuration
- Phase: Models and Persistence
- File: src/main/resources/application.properties
- Action: CREATE
- What to do: Create file with persistence and datasource configuration:
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
  quarkus.hibernate-orm.jdbc.statement-fetch-size=10
  
  # Flyway configuration
  quarkus.flyway.migrate-at-start=true
  ```
- Why: Quarkus uses application.properties instead of persistence.xml for configuration
- Depends on: Step 3
- Verify: `grep 'quarkus.datasource.db-kind' src/main/resources/application.properties`

### Step 24: Update Producers CDI imports
- Phase: CDI and Utilities
- File: src/main/java/com/redhat/coolstore/utils/Producers.java
- Action: MODIFY
- What to do: Replace `javax.enterprise` imports with `jakarta.enterprise`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 3
- Verify: `grep 'import jakarta.enterprise' src/main/java/com/redhat/coolstore/utils/Producers.java`

### Step 25: Update Transformers imports
- Phase: CDI and Utilities
- File: src/main/java/com/redhat/coolstore/utils/Transformers.java
- Action: MODIFY
- What to do: Replace `javax.json` imports with `jakarta.json` equivalents
- Why: Quarkus 3 uses Jakarta EE namespace for JSON-P
- Depends on: Step 3
- Verify: `grep 'import jakarta.json' src/main/java/com/redhat/coolstore/utils/Transformers.java`

### Step 26: Update DataBaseMigrationStartup imports
- Phase: CDI and Utilities
- File: src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- Action: MODIFY
- What to do: Replace `javax.annotation.PostConstruct` with `jakarta.annotation.PostConstruct` and other javax imports with jakarta
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 3
- Verify: `grep 'import jakarta.annotation' src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java`

### Step 27: Update StartupListener imports
- Phase: CDI and Utilities
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do: Replace any `javax` imports with `jakarta` equivalents
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 3
- Verify: `! grep 'import javax' src/main/java/com/redhat/coolstore/utils/StartupListener.java || grep 'import jakarta' src/main/java/com/redhat/coolstore/utils/StartupListener.java`

### Step 28: COMPLEX — Replace @Stateless with @ApplicationScoped in CatalogService
- Phase: Services Layer
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do:
  - BEFORE: `@Stateless public class CatalogService`
  - AFTER: `@ApplicationScoped public class CatalogService`
  - Specific changes:
    1. Remove import: `import jakarta.ejb.Stateless;`
    2. Add import: `import jakarta.enterprise.context.ApplicationScoped;`
    3. Replace `@Stateless` with `@ApplicationScoped`
    4. Update other javax imports to jakarta
- Why: Quarkus doesn't support EJBs; use CDI scopes instead
- Depends on: Step 22
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/CatalogService.java`

### Step 29: Add @Transactional to updateInventoryItems in CatalogService
- Phase: Services Layer
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do: Add `@Transactional` annotation to the updateInventoryItems method and add import `import jakarta.transaction.Transactional;`
- Why: EntityManager merge operations require @Transactional in Quarkus
- Depends on: Step 28
- Verify: `grep '@Transactional' src/main/java/com/redhat/coolstore/service/CatalogService.java`

### Step 30: COMPLEX — Replace @Stateless with @ApplicationScoped in OrderService
- Phase: Services Layer
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do:
  - BEFORE: `@Stateless public class OrderService`
  - AFTER: `@ApplicationScoped public class OrderService`
  - Specific changes:
    1. Remove import: `import jakarta.ejb.Stateless;`
    2. Add import: `import jakarta.enterprise.context.ApplicationScoped;`
    3. Replace `@Stateless` with `@ApplicationScoped`
    4. Update other javax imports to jakarta
- Why: Quarkus doesn't support EJBs; use CDI scopes instead
- Depends on: Step 22
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/OrderService.java`

### Step 31: Add @Transactional to save method in OrderService
- Phase: Services Layer
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do: Add `@Transactional` annotation to the save method and add import `import jakarta.transaction.Transactional;`
- Why: EntityManager persist operations require @Transactional in Quarkus
- Depends on: Step 30
- Verify: `grep '@Transactional' src/main/java/com/redhat/coolstore/service/OrderService.java`

### Step 32: COMPLEX — Replace @Stateless with @ApplicationScoped in ProductService
- Phase: Services Layer
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do:
  - BEFORE: `@Stateless public class ProductService`
  - AFTER: `@ApplicationScoped public class ProductService`
  - Specific changes:
    1. Remove import: `import jakarta.ejb.Stateless;`
    2. Add import: `import jakarta.enterprise.context.ApplicationScoped;`
    3. Replace `@Stateless` with `@ApplicationScoped`
    4. Update other javax imports to jakarta
- Why: Quarkus doesn't support EJBs; use CDI scopes instead
- Depends on: Step 22
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ProductService.java`

### Step 33: COMPLEX — Replace @Stateless with @ApplicationScoped in PromoService
- Phase: Services Layer
- File: src/main/java/com/redhat/coolstore/service/PromoService.java
- Action: MODIFY
- What to do:
  - BEFORE: `@Stateless public class PromoService`
  - AFTER: `@ApplicationScoped public class PromoService`
  - Specific changes:
    1. Remove import: `import jakarta.ejb.Stateless;`
    2. Add import: `import jakarta.enterprise.context.ApplicationScoped;`
    3. Replace `@Stateless` with `@ApplicationScoped`
    4. Update other javax imports to jakarta
- Why: Quarkus doesn't support EJBs; use CDI scopes instead
- Depends on: Step 22
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/PromoService.java`

### Step 34: COMPLEX — Replace @Stateful with @ApplicationScoped in ShoppingCartService
- Phase: Services Layer
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - BEFORE: `@Stateful public class ShoppingCartService`
  - AFTER: `@ApplicationScoped public class ShoppingCartService`
  - Specific changes:
    1. Remove import: `import jakarta.ejb.Stateful;`
    2. Add import: `import jakarta.enterprise.context.ApplicationScoped;`
    3. Replace `@Stateful` with `@ApplicationScoped`
    4. Update other javax imports to jakarta
- Why: Quarkus doesn't support stateful EJBs; session state should be managed at REST layer or externalized
- Depends on: Step 22
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ShoppingCartService.java`

### Step 35: COMPLEX — Remove JNDI lookup and inject ShippingService in ShoppingCartService
- Phase: Services Layer
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - BEFORE: `lookupShippingServiceRemote()` method with JNDI InitialContext
  - AFTER: `@Inject ShippingService shippingService;` and direct method calls
  - Specific changes:
    1. Remove: `lookupShippingServiceRemote()` method
    2. Remove: All JNDI-related imports (Context, InitialContext, NamingException, Hashtable)
    3. Add: `@Inject ShippingService shippingService;` field
    4. Replace: `lookupShippingServiceRemote().calculateShipping(sc)` with `shippingService.calculateShipping(sc)`
    5. Replace: `lookupShippingServiceRemote().calculateShippingInsurance(sc)` with `shippingService.calculateShippingInsurance(sc)`
- Why: JNDI is not supported in Quarkus; use CDI injection instead
- Depends on: Step 34
- Verify: `grep '@Inject' src/main/java/com/redhat/coolstore/service/ShoppingCartService.java | grep ShippingService`

### Step 36: COMPLEX — Replace @Stateless and @Remote in ShippingService
- Phase: Services Layer
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do:
  - BEFORE: `@Stateless @Remote public class ShippingService implements ShippingServiceRemote`
  - AFTER: `@ApplicationScoped public class ShippingService`
  - Specific changes:
    1. Remove import: `import jakarta.ejb.Stateless;` and `import jakarta.ejb.Remote;`
    2. Add import: `import jakarta.enterprise.context.ApplicationScoped;`
    3. Replace `@Stateless @Remote` with `@ApplicationScoped`
    4. Remove: `implements ShippingServiceRemote`
    5. Keep all method implementations as-is
- Why: Remote EJBs are not supported in Quarkus; use local CDI beans
- Depends on: Step 22
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ShippingService.java && ! grep '@Remote' src/main/java/com/redhat/coolstore/service/ShippingService.java`

### Step 37: COMPLEX — Replace @Stateless with @ApplicationScoped in ShoppingCartOrderProcessor
- Phase: Services Layer
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - BEFORE: `@Stateless public class ShoppingCartOrderProcessor`
  - AFTER: `@ApplicationScoped public class ShoppingCartOrderProcessor`
  - Specific changes:
    1. Remove import: `import jakarta.ejb.Stateless;`
    2. Add import: `import jakarta.enterprise.context.ApplicationScoped;`
    3. Replace `@Stateless` with `@ApplicationScoped`
    4. Update other javax imports to jakarta
- Why: Quarkus doesn't support EJBs; use CDI scopes instead
- Depends on: Step 22
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java`

### Step 38: COMPLEX — Replace JMS Topic with Reactive Messaging Emitter in ShoppingCartOrderProcessor
- Phase: Services Layer
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - BEFORE: `@Inject JMSContext context; @Resource(lookup = "java:/topic/orders") Topic ordersTopic; context.createProducer().send(ordersTopic, ...)`
  - AFTER: `@Channel("orders") Emitter<String> ordersEmitter; ordersEmitter.send(...)`
  - Specific changes:
    1. Remove imports: `import jakarta.jms.JMSContext;`, `import jakarta.jms.Topic;`, `import jakarta.annotation.Resource;`
    2. Add imports: `import org.eclipse.microprofile.reactive.messaging.Channel;`, `import org.eclipse.microprofile.reactive.messaging.Emitter;`
    3. Remove fields: `@Inject private transient JMSContext context;` and `@Resource(lookup = "java:/topic/orders") private Topic ordersTopic;`
    4. Add field: `@Channel("orders") Emitter<String> ordersEmitter;`
    5. Replace: `context.createProducer().send(ordersTopic, Transformers.shoppingCartToJson(cart))` with `ordersEmitter.send(Transformers.shoppingCartToJson(cart))`
- Why: JMS is not supported in Quarkus; use Reactive Messaging with SmallRye instead
- Depends on: Step 37
- Verify: `grep '@Channel' src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java`

### Step 39: COMPLEX — Convert OrderServiceMDB to Reactive Messaging
- Phase: Services Layer
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: `@MessageDriven(...) public class OrderServiceMDB implements MessageListener { public void onMessage(Message rcvMessage) {...} }`
  - AFTER: `@ApplicationScoped public class OrderServiceMDB { @Incoming("orders") public void onMessage(String orderStr) {...} }`
  - Specific changes:
    1. Remove imports: All JMS imports (MessageDriven, ActivationConfigProperty, Message, MessageListener, TextMessage, JMSException)
    2. Add imports: `import jakarta.enterprise.context.ApplicationScoped;`, `import org.eclipse.microprofile.reactive.messaging.Incoming;`
    3. Remove: `@MessageDriven` annotation and all activation config
    4. Remove: `implements MessageListener`
    5. Add: `@ApplicationScoped` class annotation
    6. Change method signature: `public void onMessage(Message rcvMessage)` to `@Incoming("orders") public void onMessage(String orderStr)`
    7. Remove: All JMS message unwrapping code (TextMessage casting, msg.getBody())
    8. Simplify: Use orderStr directly (it's already a String from the channel)
    9. Remove: try-catch for JMSException
    10. Keep: Business logic for order processing
- Why: Message-driven beans are not supported in Quarkus; use @Incoming from Reactive Messaging
- Depends on: Step 22
- Verify: `grep '@Incoming("orders")' src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java`

### Step 40: Add @Transactional to onMessage in OrderServiceMDB
- Phase: Services Layer
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do: Add `@Transactional` annotation to the onMessage method and add import `import jakarta.transaction.Transactional;`
- Why: EntityManager operations require @Transactional in Quarkus
- Depends on: Step 39
- Verify: `grep '@Transactional' src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java`

### Step 41: Update CartEndpoint imports
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do: Replace all `javax.enterprise`, `javax.inject`, and `javax.ws.rs` imports with `jakarta.enterprise`, `jakarta.inject`, and `jakarta.ws.rs` equivalents
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 3
- Verify: `grep 'import jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/CartEndpoint.java`

### Step 42: Update OrderEndpoint imports
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do: Replace all `javax` imports with `jakarta` equivalents
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 3
- Verify: `grep 'import jakarta' src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java`

### Step 43: Update ProductEndpoint imports
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do: Replace all `javax` imports with `jakarta` equivalents
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 3
- Verify: `grep 'import jakarta' src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java`

### Step 44: Delete RestApplication.java
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: DELETE
- What to do: Delete this file — JAX-RS activation is automatic in Quarkus
- Why: Quarkus automatically discovers and activates JAX-RS resources; ApplicationPath is not needed
- Depends on: Step 41, Step 42, Step 43
- Verify: `! test -f src/main/java/com/redhat/coolstore/rest/RestApplication.java`

### Step 45: Delete InventoryNotificationMDB.java
- Phase: WebLogic Removal
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: DELETE
- What to do: Delete this file — it's WebLogic stub code with no @MessageDriven annotation and won't work in JBoss or Quarkus
- Why: This file contains WebLogic-specific code (WLInitialContextFactory) and is not functional; it's dead code
- Depends on: none
- Verify: `! test -f src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java`

### Step 46: Delete ShippingServiceRemote.java interface
- Phase: WebLogic Removal
- File: src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java
- Action: DELETE
- What to do: Delete this file — remote interface no longer needed after removing @Remote
- Why: Remote EJB interfaces are not used in Quarkus; ShippingService is now a local CDI bean
- Depends on: Step 36
- Verify: `! test -f src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java`

### Step 47: Delete WebLogic ApplicationLifecycleEvent stub
- Phase: WebLogic Removal
- File: src/main/java/weblogic/application/ApplicationLifecycleEvent.java
- Action: DELETE
- What to do: Delete this file — WebLogic-specific stub not needed
- Why: WebLogic APIs are not used in Quarkus
- Depends on: none
- Verify: `! test -f src/main/java/weblogic/application/ApplicationLifecycleEvent.java`

### Step 48: Delete WebLogic ApplicationLifecycleListener stub
- Phase: WebLogic Removal
- File: src/main/java/weblogic/application/ApplicationLifecycleListener.java
- Action: DELETE
- What to do: Delete this file — WebLogic-specific stub not needed
- Why: WebLogic APIs are not used in Quarkus
- Depends on: none
- Verify: `! test -f src/main/java/weblogic/application/ApplicationLifecycleListener.java`

### Step 49: Delete WebLogic NonCatalogLogger stub
- Phase: WebLogic Removal
- File: src/main/java/weblogic/i18n/logging/NonCatalogLogger.java
- Action: DELETE
- What to do: Delete this file — WebLogic-specific stub not needed
- Why: WebLogic APIs are not used in Quarkus
- Depends on: none
- Verify: `! test -f src/main/java/weblogic/i18n/logging/NonCatalogLogger.java`

### Step 50: Delete beans.xml
- Phase: Configuration and Deployment Descriptors
- File: src/main/webapp/WEB-INF/beans.xml
- Action: DELETE
- What to do: Delete this file — not required in Quarkus (CDI is enabled by default)
- Why: Quarkus enables CDI by default; beans.xml is ignored
- Depends on: none
- Verify: `! test -f src/main/webapp/WEB-INF/beans.xml`

### Step 51: Delete web.xml
- Phase: Configuration and Deployment Descriptors
- File: src/main/webapp/WEB-INF/web.xml
- Action: DELETE
- What to do: Delete this file — not required in Quarkus
- Why: Quarkus doesn't use web.xml for configuration
- Depends on: none
- Verify: `! test -f src/main/webapp/WEB-INF/web.xml`

### Step 52: Delete persistence.xml
- Phase: Configuration and Deployment Descriptors
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Delete this file — configuration moved to application.properties
- Why: Quarkus uses application.properties for persistence configuration
- Depends on: Step 23
- Verify: `! test -f src/main/resources/META-INF/persistence.xml`

### Step 53: Add Reactive Messaging channel configuration
- Phase: Configuration and Deployment Descriptors
- File: src/main/resources/application.properties
- Action: MODIFY
- What to do: Add reactive messaging channel configuration:
  ```properties
  # Reactive Messaging configuration
  mp.messaging.outgoing.orders.connector=smallrye-in-memory
  mp.messaging.incoming.orders.connector=smallrye-in-memory
  ```
- Why: Configure the in-memory channel for order messaging to replace JMS topic
- Depends on: Step 23, Step 38, Step 39
- Verify: `grep 'mp.messaging.outgoing.orders' src/main/resources/application.properties`

## Verification

- Build: `mvn clean package`
- Test: Tests are currently skipped (maven.test.skip=true in pom.xml); after migration, enable tests with `mvn test`
- Blackbox: 
  1. Start PostgreSQL: `podman run --name myPostgresDb -p 5432:5432 -e POSTGRES_USER=postgresUser -e POSTGRES_PASSWORD=postgresPW -e POSTGRES_DB=postgresDB -d postgres`
  2. Run application: `mvn quarkus:dev`
  3. Navigate to http://localhost:8080
  4. Test adding items to cart
  5. Test checkout process — verify order messages are processed (check logs for "Message recd !" and "Received order:")
  6. Verify inventory updates are applied

## Notes

- **Session state**: The original @Stateful ShoppingCartService maintained cart state per user session. After migration to @ApplicationScoped, the cart field is shared across all users. This may require additional work to implement proper session management (e.g., using a Map<String, ShoppingCart> keyed by session ID, or externalizing session state to Redis).

- **Keycloak integration**: The application uses Keycloak for authentication. After migration, you'll need to add `quarkus-oidc` extension and configure Keycloak in application.properties using the keycloak.json configuration.

- **Reactive Messaging**: The migration uses in-memory channels for simplicity. For production with multiple instances, consider using Kafka (`quarkus-smallrye-reactive-messaging-kafka`) or AMQP connectors.

- **Clustering**: The original application used JMS topics for clustering across JBoss instances. The in-memory channel doesn't support clustering. For distributed deployments, switch to Kafka or another message broker.

- **Database migration**: Flyway is configured to run at startup. Ensure migration scripts in src/main/resources/db/migration are compatible with the new Hibernate sequence naming.

- **Native build**: After successful JVM mode testing, you can build a native executable with `mvn package -Pnative`. This may require additional configuration for reflection and resources.

- **REST endpoint paths**: Verify that JAX-RS path mappings work correctly after removing the Application class. Quarkus defaults to `/` as the root path for JAX-RS resources.
