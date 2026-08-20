# Implementation Plan

## Goal
Migrate a Java EE 7 monolithic application from JBoss/WildFly to Quarkus 3.2 LTS
- Domain skill: javaee-to-quarkus

## Project Summary
- Type: Maven WAR project (Java EE 7)
- Files affected: 34 (30 Java source files, 1 pom.xml, 3 XML config files)
- Estimated complexity: High
- Hardest steps: 
  1. Step 18 - InventoryNotificationMDB conversion (manual JMS → reactive messaging)
  2. Step 21 - ShoppingCartService remote EJB JNDI removal
  3. Step 2 - Build configuration transformation (WAR→JAR, dependencies)

## Steps

### Step 1: Transform pom.xml packaging and add Quarkus infrastructure
- Phase: Build Config
- File: pom.xml
- Action: MODIFY
- What to do:
  - Change `<packaging>war</packaging>` to `<packaging>jar</packaging>`
  - Remove both Java EE dependencies: `javax:javaee-web-api` and `javax:javaee-api`
  - Remove `maven-war-plugin` from `<build><plugins>`
  - Add Quarkus BOM to `<dependencyManagement>`:
    ```xml
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>io.quarkus.platform</groupId>
                <artifactId>quarkus-bom</artifactId>
                <version>3.2.12.Final</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
    ```
  - Add Quarkus Maven plugin to `<build><plugins>`:
    ```xml
    <plugin>
        <groupId>io.quarkus.platform</groupId>
        <artifactId>quarkus-maven-plugin</artifactId>
        <version>3.2.12.Final</version>
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
- Why: Transform from app server deployment (WAR) to Quarkus standalone application (JAR)
- Depends on: none
- Verify: pom.xml has `<packaging>jar</packaging>` and Quarkus BOM + plugin present

### Step 2: Add Quarkus extensions to pom.xml
- Phase: Build Config
- File: pom.xml
- Action: MODIFY
- What to do: Add these Quarkus extensions to `<dependencies>` (no version needed - BOM manages them):
  ```xml
  <!-- Core CDI -->
  <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-arc</artifactId>
  </dependency>
  <!-- REST + JSON -->
  <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-rest-jackson</artifactId>
  </dependency>
  <!-- JPA + Hibernate -->
  <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-hibernate-orm</artifactId>
  </dependency>
  <!-- JDBC drivers -->
  <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-jdbc-postgresql</artifactId>
  </dependency>
  <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-jdbc-h2</artifactId>
  </dependency>
  <!-- Database migration -->
  <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-flyway</artifactId>
  </dependency>
  <!-- Reactive Messaging (JMS) -->
  <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-smallrye-reactive-messaging-amqp</artifactId>
  </dependency>
  <!-- Authentication (Keycloak/OIDC) -->
  <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-oidc</artifactId>
  </dependency>
  ```
  Remove these old dependencies: `org.jboss.spec.javax.jms:jboss-jms-api_2.0_spec`, `org.jboss.spec.javax.rmi:jboss-rmi-api_1.0_spec`
  Keep `org.flywaydb:flyway-core` but change version to `${quarkus.flyway.version}` or remove version entirely (Quarkus manages it)
- Why: Replace Java EE umbrella dependencies with specific Quarkus extensions for CDI, JAX-RS, JPA, messaging, and auth
- Depends on: Step 1
- Verify: All Quarkus extensions present, old `javax:javaee-*` and JBoss spec dependencies removed

### Step 3: Create application.properties with datasource configuration
- Phase: App Config
- File: src/main/resources/application.properties
- Action: CREATE
- What to do: Create file with this content:
  ```properties
  # Datasource - Production (PostgreSQL template - configure based on actual DB)
  quarkus.datasource.db-kind=postgresql
  quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/coolstore
  quarkus.datasource.username=${DB_USER:coolstore}
  quarkus.datasource.password=${DB_PASS:coolstore}
  
  # Hibernate ORM - disable auto-generation (Flyway handles schema)
  quarkus.hibernate-orm.database.generation=none
  quarkus.hibernate-orm.log.sql=false
  
  # Flyway - database migrations
  quarkus.flyway.migrate-at-start=true
  quarkus.flyway.locations=classpath:db/migration
  
  # Datasource - Dev profile (H2 in-memory)
  %dev.quarkus.datasource.db-kind=h2
  %dev.quarkus.datasource.jdbc.url=jdbc:h2:mem:coolstore;DB_CLOSE_DELAY=-1
  %dev.quarkus.datasource.username=sa
  %dev.quarkus.datasource.password=
  %dev.quarkus.hibernate-orm.log.sql=true
  
  # Reactive Messaging - Orders topic
  mp.messaging.incoming.orders.connector=smallrye-amqp
  mp.messaging.incoming.orders.address=orders
  mp.messaging.incoming.orders.broadcast=true
  
  # Reactive Messaging - Dev profile (in-memory)
  %dev.mp.messaging.incoming.orders.connector=smallrye-in-memory
  
  # Keycloak/OIDC - migrated from keycloak.json
  quarkus.oidc.auth-server-url=http://localhost:8081/realms/eap
  quarkus.oidc.client-id=eap-app
  quarkus.oidc.credentials.secret=${KEYCLOAK_SECRET:secret}
  quarkus.oidc.tls.verification=none
  
  # Application
  quarkus.application.name=coolstore-monolith
  quarkus.http.port=8080
  ```
- Why: Quarkus uses application.properties instead of persistence.xml, web.xml, and keycloak.json
- Depends on: Step 2
- Verify: File exists with datasource, Flyway, messaging, and OIDC configuration

### Step 4: Delete persistence.xml
- Phase: App Config
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Delete this file - replaced by application.properties datasource configuration
- Why: Quarkus manages JPA configuration via application.properties
- Depends on: Step 3
- Verify: File no longer exists

### Step 5: Delete web.xml
- Phase: App Config
- File: src/main/webapp/WEB-INF/web.xml
- Action: DELETE
- What to do: Delete this file - not needed for Quarkus JAR packaging
- Why: Quarkus does not use deployment descriptors
- Depends on: Step 3
- Verify: File no longer exists

### Step 6: Delete beans.xml
- Phase: App Config
- File: src/main/webapp/WEB-INF/beans.xml
- Action: DELETE
- What to do: Delete this file - Quarkus enables CDI automatically
- Why: Quarkus ArC (CDI implementation) does not require beans.xml
- Depends on: Step 3
- Verify: File no longer exists

### Step 7: Migrate CatalogItemEntity imports
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java
- Action: MODIFY
- What to do: Replace all javax.persistence.* imports with jakarta.persistence.*
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 2
- Verify: No javax.persistence imports remain, only jakarta.persistence

### Step 8: Migrate InventoryEntity imports
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/model/InventoryEntity.java
- Action: MODIFY
- What to do: Replace all javax.persistence.* imports with jakarta.persistence.*
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 2
- Verify: No javax.persistence imports remain

### Step 9: Migrate Order imports
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/model/Order.java
- Action: MODIFY
- What to do: Replace all javax.persistence.* imports with jakarta.persistence.*
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 2
- Verify: No javax.persistence imports remain

### Step 10: Migrate OrderItem imports
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/model/OrderItem.java
- Action: MODIFY
- What to do: Replace all javax.persistence.* imports with jakarta.persistence.*
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 2
- Verify: No javax.persistence imports remain

### Step 11: Migrate Product imports
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/model/Product.java
- Action: MODIFY
- What to do: Replace all javax.persistence.* imports with jakarta.persistence.*
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 2
- Verify: No javax.persistence imports remain

### Step 12: Migrate Promotion imports
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/model/Promotion.java
- Action: MODIFY
- What to do: Replace all javax.persistence.* imports with jakarta.persistence.*
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 2
- Verify: No javax.persistence imports remain

### Step 13: Migrate ShoppingCart imports
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/model/ShoppingCart.java
- Action: MODIFY
- What to do: Replace all javax.persistence.* imports with jakarta.persistence.*
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 2
- Verify: No javax.persistence imports remain

### Step 14: Migrate ShoppingCartItem imports
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java
- Action: MODIFY
- What to do: Replace all javax.persistence.* imports with jakarta.persistence.*
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 2
- Verify: No javax.persistence imports remain

### Step 15: Migrate Resources (EntityManager producer)
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do:
  - Replace `import javax.enterprise.inject.Produces;` with `import jakarta.enterprise.inject.Produces;`
  - Replace `import javax.persistence.EntityManager;` with `import jakarta.persistence.EntityManager;`
  - Replace `import javax.persistence.PersistenceContext;` with `import jakarta.persistence.PersistenceContext;`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 2
- Verify: All imports use jakarta.* namespace

### Step 16: Migrate CartEndpoint
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do:
  - Replace `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
  - Replace all `javax.ws.rs.*` imports with `jakarta.ws.rs.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 2
- Verify: All imports use jakarta.* namespace

### Step 17: Migrate OrderEndpoint
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do:
  - Replace `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
  - Replace all `javax.ws.rs.*` imports with `jakarta.ws.rs.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 2
- Verify: All imports use jakarta.* namespace

### Step 18: Migrate ProductEndpoint
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do:
  - Replace `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
  - Replace all `javax.ws.rs.*` imports with `jakarta.ws.rs.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 2
- Verify: All imports use jakarta.* namespace

### Step 19: Migrate RestApplication
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: MODIFY
- What to do: Replace `import javax.ws.rs.ApplicationPath;` and `import javax.ws.rs.core.Application;` with `jakarta.ws.rs.*` equivalents
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 2
- Verify: All imports use jakarta.ws.rs.*

### Step 20: Migrate CatalogService from EJB to CDI
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless;` with `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `@Stateless` annotation with `@ApplicationScoped`
  - Replace `import javax.persistence.EntityManager;` with `import jakarta.persistence.EntityManager;`
  - Replace `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
- Why: Quarkus uses CDI managed beans instead of EJB
- Depends on: Step 2
- Verify: No @Stateless annotation, using @ApplicationScoped and jakarta.* imports

### Step 21: COMPLEX - Convert OrderServiceMDB from MDB to reactive messaging
- Phase: Messaging
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: Uses @MessageDriven with JMS Topic listener
  - AFTER: Convert to SmallRye Reactive Messaging
  - Specific changes:
    1. Remove all imports: `javax.ejb.*`, `javax.jms.*`
    2. Add imports:
       - `import jakarta.enterprise.context.ApplicationScoped;`
       - `import org.eclipse.microprofile.reactive.messaging.Incoming;`
       - `import jakarta.inject.Inject;`
    3. Remove `@MessageDriven` annotation and all `@ActivationConfigProperty` entries
    4. Add `@ApplicationScoped` to class
    5. Replace `implements MessageListener` - remove the interface
    6. Replace method signature from:
       ```java
       public void onMessage(Message rcvMessage) {
           TextMessage msg = (TextMessage) rcvMessage;
           String orderStr = msg.getBody(String.class);
       ```
       to:
       ```java
       @Incoming("orders")
       public void onMessage(String orderStr) {
       ```
    7. Remove all JMS message handling code - the String parameter arrives directly
    8. Remove the try-catch for JMSException - no longer needed
- Why: Quarkus uses SmallRye Reactive Messaging instead of JMS MDBs. The "orders" channel is configured in application.properties (Step 3)
- Depends on: Step 3
- Verify: Class has @ApplicationScoped and @Incoming("orders"), no JMS imports, method receives String directly

### Step 22: COMPLEX - Convert InventoryNotificationMDB from manual JMS to reactive messaging
- Phase: Messaging
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: Manual WebLogic JNDI + JMS connection setup
  - AFTER: Convert to SmallRye Reactive Messaging
  - Specific changes:
    1. Remove all imports: `javax.inject.Inject` (will re-add jakarta version), `javax.jms.*`, `javax.naming.*`, `javax.rmi.PortableRemoteObject`, `java.util.Hashtable`
    2. Add imports:
       - `import jakarta.enterprise.context.ApplicationScoped;`
       - `import org.eclipse.microprofile.reactive.messaging.Incoming;`
       - `import jakarta.inject.Inject;`
    3. Remove all static fields: `JNDI_FACTORY`, `JMS_FACTORY`, `TOPIC`
    4. Remove all instance fields: `tcon`, `tsession`, `tsubscriber`
    5. Add `@ApplicationScoped` to class
    6. Remove `implements MessageListener`
    7. Replace method signature from:
       ```java
       public void onMessage(Message rcvMessage) {
           TextMessage msg = (TextMessage) rcvMessage;
           String orderStr = msg.getBody(String.class);
       ```
       to:
       ```java
       @Incoming("orders")
       public void onMessage(String orderStr) {
       ```
    8. Remove all JMS message handling and the try-catch for JMSException
    9. Delete the entire `init()` method - no longer needed
    10. Delete the entire `close()` method - no longer needed
    11. Delete the entire `getInitialContext()` method - no longer needed
- Why: Quarkus reactive messaging eliminates manual JMS connection management. The WebLogic JNDI code is completely replaced by declarative configuration in application.properties
- Depends on: Step 3, Step 20
- Verify: Class has @ApplicationScoped and @Incoming("orders"), no JMS/JNDI/naming imports, all manual connection code removed

### Step 23: Migrate OrderService from EJB to CDI
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless;` with `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `@Stateless` with `@ApplicationScoped`
  - Replace `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
  - Replace `import javax.persistence.EntityManager;` with `import jakarta.persistence.EntityManager;`
- Why: Convert EJB to CDI managed bean
- Depends on: Step 2
- Verify: Using @ApplicationScoped and jakarta.* imports

### Step 24: Migrate ProductService from EJB to CDI
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless;` with `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `@Stateless` with `@ApplicationScoped`
  - Replace `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
- Why: Convert EJB to CDI managed bean
- Depends on: Step 2
- Verify: Using @ApplicationScoped and jakarta.* imports

### Step 25: Migrate PromoService from EJB to CDI
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/PromoService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless;` with `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `@Stateless` with `@ApplicationScoped`
- Why: Convert EJB to CDI managed bean
- Depends on: Step 2
- Verify: Using @ApplicationScoped and jakarta.* imports

### Step 26: Migrate ShippingService from EJB to CDI
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless;` with `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `@Stateless` with `@ApplicationScoped`
- Why: Convert EJB to CDI managed bean
- Depends on: Step 2
- Verify: Using @ApplicationScoped and jakarta.* imports

### Step 27: Delete ShippingServiceRemote interface
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java
- Action: DELETE
- What to do: Delete this @Remote interface - no longer needed in Quarkus
- Why: Quarkus uses local CDI injection, not remote EJB interfaces
- Depends on: Step 26
- Verify: File no longer exists

### Step 28: COMPLEX - Remove remote EJB lookup from ShoppingCartService
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - BEFORE: Uses remote JNDI lookup for ShippingService
  - AFTER: Use local CDI injection
  - Specific changes:
    1. Replace `import javax.ejb.Stateful;` with `import jakarta.enterprise.context.ApplicationScoped;`
    2. Replace `@Stateful` with `@ApplicationScoped`
    3. Replace `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
    4. Remove these imports: `java.util.Hashtable`, `javax.naming.Context`, `javax.naming.InitialContext`, `javax.naming.NamingException`
    5. Add field injection for ShippingService:
       ```java
       @Inject
       ShippingService shippingService;
       ```
    6. Delete the entire `lookupShippingServiceRemote()` static method
    7. Replace all calls to `lookupShippingServiceRemote()` with direct use of the injected `shippingService` field:
       - `lookupShippingServiceRemote().calculateShipping(sc)` → `shippingService.calculateShipping(sc)`
       - `lookupShippingServiceRemote().calculateShippingInsurance(sc)` → `shippingService.calculateShippingInsurance(sc)`
    8. Remove the private `ShoppingCart cart` field and change to request-scoped pattern if needed (or keep as ApplicationScoped with proper state management)
- Why: ShippingService is in the same application - remote EJB lookup is unnecessary complexity. CDI injection is simpler and faster
- Depends on: Step 26, Step 27
- Verify: No JNDI/InitialContext code remains, ShippingService is @Inject'd, no references to ShippingServiceRemote

### Step 29: Migrate ShoppingCartOrderProcessor from EJB to CDI
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless;` with `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `@Stateless` with `@ApplicationScoped`
  - Replace `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
  - If JMS producer code exists, convert to use `@Channel` and `Emitter` pattern from SmallRye Reactive Messaging
- Why: Convert EJB to CDI managed bean
- Depends on: Step 2
- Verify: Using @ApplicationScoped and jakarta.* imports

### Step 30: COMPLEX - Convert DataBaseMigrationStartup lifecycle
- Phase: Lifecycle
- File: src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- Action: MODIFY
- What to do:
  - BEFORE: Uses @Singleton with @PostConstruct to run Flyway manually
  - AFTER: Delete manual Flyway code - Quarkus handles this
  - Specific changes:
    1. Replace `import javax.annotation.PostConstruct;` with `import jakarta.annotation.PostConstruct;`
    2. Replace `import javax.ejb.Singleton;` with `import jakarta.enterprise.context.ApplicationScoped;`
    3. Replace `import javax.ejb.Startup;` with `import io.quarkus.runtime.Startup;`
    4. Replace `@Singleton` with `@ApplicationScoped`
    5. Keep `@Startup` (Quarkus version)
    6. Simplify or remove the `init()` method - Flyway runs automatically via `quarkus.flyway.migrate-at-start=true` in application.properties
    7. If keeping logging, inject a Logger and log that Flyway is configured to run automatically
- Why: Quarkus Flyway extension handles migrations automatically at startup
- Depends on: Step 3
- Verify: No manual Flyway execution code, using jakarta.annotation.PostConstruct and io.quarkus.runtime.Startup

### Step 31: Migrate Producers
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/utils/Producers.java
- Action: MODIFY
- What to do:
  - Replace `import javax.enterprise.inject.Produces;` with `import jakarta.enterprise.inject.Produces;`
  - Replace `import javax.enterprise.inject.spi.InjectionPoint;` with `import jakarta.enterprise.inject.spi.InjectionPoint;`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 2
- Verify: Using jakarta.enterprise.* imports

### Step 32: COMPLEX - Convert StartupListener lifecycle
- Phase: Lifecycle
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do:
  - BEFORE: Extends WebLogic ApplicationLifecycleListener
  - AFTER: Use Quarkus lifecycle events
  - Specific changes:
    1. Remove `import weblogic.application.ApplicationLifecycleEvent;`
    2. Remove `import weblogic.application.ApplicationLifecycleListener;`
    3. Add imports:
       - `import io.quarkus.runtime.StartupEvent;`
       - `import io.quarkus.runtime.ShutdownEvent;`
       - `import jakarta.enterprise.context.ApplicationScoped;`
       - `import jakarta.enterprise.event.Observes;`
    4. Replace `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
    5. Add `@ApplicationScoped` to the class
    6. Remove `extends ApplicationLifecycleListener`
    7. Replace method signature:
       ```java
       // Before
       public void postStart(ApplicationLifecycleEvent evt) {
       
       // After
       void onStart(@Observes StartupEvent ev) {
       ```
    8. Replace method signature:
       ```java
       // Before
       public void preStop(ApplicationLifecycleEvent evt) {
       
       // After
       void onStop(@Observes ShutdownEvent ev) {
       ```
- Why: Quarkus uses CDI observer pattern for lifecycle events instead of server-specific listeners
- Depends on: Step 2
- Verify: Class has @ApplicationScoped, methods use @Observes with StartupEvent/ShutdownEvent, no weblogic imports

### Step 33: Migrate Transformers
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/utils/Transformers.java
- Action: MODIFY
- What to do: If any javax.* imports exist (like javax.json.*), replace with jakarta.json.* equivalents
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 2
- Verify: All imports use jakarta.* namespace if applicable

### Step 34: Delete WebLogic ApplicationLifecycleEvent stub
- Phase: Cleanup
- File: src/main/java/weblogic/application/ApplicationLifecycleEvent.java
- Action: DELETE
- What to do: Delete this WebLogic stub class - no longer needed
- Why: Replaced by Quarkus lifecycle events in Step 32
- Depends on: Step 32
- Verify: File no longer exists

### Step 35: Delete WebLogic ApplicationLifecycleListener stub
- Phase: Cleanup
- File: src/main/java/weblogic/application/ApplicationLifecycleListener.java
- Action: DELETE
- What to do: Delete this WebLogic stub class - no longer needed
- Why: Replaced by Quarkus lifecycle events in Step 32
- Depends on: Step 32
- Verify: File no longer exists

### Step 36: Delete WebLogic NonCatalogLogger stub
- Phase: Cleanup
- File: src/main/java/weblogic/i18n/logging/NonCatalogLogger.java
- Action: DELETE
- What to do: Delete this WebLogic stub class - no longer needed
- Why: Cleanup server-specific stub classes
- Depends on: Step 32
- Verify: File no longer exists

### Step 37: Delete weblogic stub directory
- Phase: Cleanup
- File: src/main/java/weblogic/
- Action: DELETE
- What to do: Delete the entire weblogic directory and all subdirectories
- Why: All WebLogic-specific code has been migrated to Quarkus equivalents
- Depends on: Step 34, Step 35, Step 36
- Verify: Directory src/main/java/weblogic/ no longer exists

## Verification

- Build: `mvn clean compile`
  - Must complete without errors
  - Verify all Quarkus extensions are resolved
  - Verify no javax.ejb, javax.jms references in compile output
  
- Test: `mvn test`
  - Tests may need updates for Quarkus test framework (not in scope for this migration)
  - Document any test failures - they are expected and not blocking
  
- Blackbox: 
  1. Start the application: `mvn quarkus:dev`
  2. Verify startup completes without errors
  3. Check that Flyway migrations run: look for "Flyway" in startup logs
  4. Test REST endpoints:
     - GET http://localhost:8080/services/products - should return product list
     - GET http://localhost:8080/services/cart/{cartId} - should return shopping cart
  5. If messaging broker is available, verify MDB message consumption works
  6. Verify database connection: check logs for successful datasource initialization

## Notes

- **Database configuration**: Step 3 provides PostgreSQL template configuration. The actual production database type is unknown per questionnaire decision #4. Adjust `quarkus.datasource.db-kind` and JDBC URL based on actual database (could be Oracle, MySQL, etc.). The H2 dev profile will work immediately for development/testing.

- **Keycloak configuration**: The OIDC configuration in Step 3 is derived from the existing keycloak.json file. The `quarkus.oidc.credentials.secret` should be provided via environment variable `KEYCLOAK_SECRET`.

- **Messaging broker**: The reactive messaging configuration assumes an AMQP broker (Artemis) for production. The dev profile uses in-memory channels for local development without requiring a broker. If deploying to production, ensure AMQP broker connection details are configured.

- **@Stateful to @ApplicationScoped**: ShoppingCartService was @Stateful in the original code, maintaining per-user cart state. Converting to @ApplicationScoped may require additional session management logic or moving to a different scoping strategy. Consider using `@RequestScoped` or external session storage if multi-user cart isolation is critical.

- **No JSP migration**: Per questionnaire decision #2, JSPs are being removed (API-only backend). The JSP files in src/main/webapp/ are not migrated and can be deleted or kept as static resources for the AngularJS frontend.

- **Build verification after each phase**: Run `mvn compile` after completing all steps in each phase (after Step 2, Step 6, Step 29, Step 29, Step 32, Step 37) to catch issues early.
