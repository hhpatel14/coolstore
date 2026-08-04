# Implementation Plan

## Goal
Migrate a Java EE 7 application running on JBoss EAP 7.4 to Quarkus 3.x
- Domain skill: none

## Project Summary
- Type: Maven WAR / Java EE 7
- Files affected: ~25 files (Java sources, build config, deployment descriptors, resources)
- Estimated complexity: Medium
- Hardest steps: Message-Driven Bean migration, datasource JNDI replacement, reactive messaging setup

## Steps

### Step 1: Create Quarkus application.properties
- Phase: Configuration
- File: src/main/resources/application.properties
- Action: CREATE
- What to do: Create Quarkus configuration file with datasource, OIDC, and application settings
  - Add datasource configuration (replacing JNDI lookup)
  - Add Quarkus OIDC configuration for Keycloak
  - Add Flyway migration settings
  - Add HTTP/CORS settings
  - Configure static resources serving
- Why: Quarkus uses application.properties instead of container configuration
- Depends on: none
- Verify: File exists with required properties for datasource, oidc, flyway

### Step 2: Update pom.xml - Quarkus BOM and platform
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: 
  - Change packaging from `war` to `jar`
  - Add Quarkus BOM in dependencyManagement section
  - Update properties: Java 17, Quarkus version 3.x
  - Update compiler plugin configuration for Java 17
- Why: Set up Quarkus platform and change packaging model
- Depends on: Step 1
- Verify: pom.xml has Quarkus BOM and jar packaging

### Step 3: Update pom.xml - Replace Java EE dependencies with Quarkus extensions
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do:
  - Remove javaee-web-api and javaee-api dependencies
  - Remove jboss-jms-api_2.0_spec dependency
  - Remove jboss-rmi-api_1.0_spec dependency
  - Add quarkus-hibernate-orm dependency
  - Add quarkus-jdbc-postgresql dependency
  - Add quarkus-resteasy-reactive-jackson dependency
  - Add quarkus-oidc dependency
  - Add quarkus-smallrye-reactive-messaging-in-memory dependency
  - Add quarkus-flyway dependency
  - Add quarkus-arc dependency (CDI)
- Why: Replace Java EE APIs with Quarkus extensions
- Depends on: Step 2
- Verify: No javaee-api dependencies remain, Quarkus extensions present

### Step 4: Update pom.xml - Replace Maven plugins
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do:
  - Remove maven-war-plugin
  - Add quarkus-maven-plugin with build goal
  - Update maven-compiler-plugin version to 3.11.0
- Why: Use Quarkus build tooling instead of WAR packaging
- Depends on: Step 3
- Verify: quarkus-maven-plugin present, maven-war-plugin removed

### Step 5: Update persistence.xml for Quarkus
- Phase: Persistence Layer
- File: src/main/resources/META-INF/persistence.xml
- Action: MODIFY
- What to do:
  - Remove `<jta-data-source>java:jboss/datasources/CoolstoreDS</jta-data-source>`
  - Update namespace from javax.persistence to jakarta.persistence
  - Remove hibernate.jdbc.use_get_generated_keys property
  - Simplify to minimal configuration (Quarkus auto-configures datasource)
- Why: Quarkus manages datasource differently, no JNDI
- Depends on: Step 1
- Verify: No JNDI reference, jakarta.persistence namespace

### Step 6: COMPLEX - Migrate Resources.java EntityManager producer
- Phase: Persistence Layer
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do:
  - BEFORE: @PersistenceContext with @Produces pattern
  - AFTER: Direct injection pattern or remove if not needed
  - Specific changes:
    1. Remove: @PersistenceContext annotation
    2. Remove: @Produces method (Quarkus injects EntityManager directly)
    3. Consider: Delete this file entirely if only used for EntityManager
  - Alternative: Keep as CDI producer but inject EntityManager instead of using @PersistenceContext
- Why: Quarkus injects EntityManager directly via @Inject, doesn't need @PersistenceContext
- Depends on: Step 5
- Verify: No @PersistenceContext annotation, or file deleted

### Step 7: Update CatalogItemEntity imports
- Phase: Persistence Layer
- File: src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java
- Action: MODIFY
- What to do: Replace `import javax.persistence.*;` with `import jakarta.persistence.*;`
- Why: Jakarta EE 9+ uses jakarta namespace
- Depends on: Step 5
- Verify: Uses jakarta.persistence imports

### Step 8: Update InventoryEntity imports
- Phase: Persistence Layer
- File: src/main/java/com/redhat/coolstore/model/InventoryEntity.java
- Action: MODIFY
- What to do: Replace javax.persistence imports with jakarta.persistence
- Why: Jakarta EE 9+ uses jakarta namespace
- Depends on: Step 5
- Verify: Uses jakarta.persistence imports

### Step 9: Update Order model imports
- Phase: Persistence Layer
- File: src/main/java/com/redhat/coolstore/model/Order.java
- Action: MODIFY
- What to do: Replace javax.persistence imports with jakarta.persistence
- Why: Jakarta EE 9+ uses jakarta namespace
- Depends on: Step 5
- Verify: Uses jakarta.persistence imports

### Step 10: Update OrderItem model imports
- Phase: Persistence Layer
- File: src/main/java/com/redhat/coolstore/model/OrderItem.java
- Action: MODIFY
- What to do: Replace javax.persistence imports with jakarta.persistence
- Why: Jakarta EE 9+ uses jakarta namespace
- Depends on: Step 5
- Verify: Uses jakarta.persistence imports

### Step 11: Update service classes - CatalogService imports
- Phase: Business Logic
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do: Replace all javax imports with jakarta equivalents (javax.inject → jakarta.inject, javax.persistence → jakarta.persistence, javax.ejb → jakarta.ejb if present, javax.transaction → jakarta.transaction if present)
- Why: Jakarta EE namespace migration
- Depends on: Step 6
- Verify: No javax imports remain (except javax.annotation which may stay)

### Step 12: Update service classes - OrderService imports
- Phase: Business Logic
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do: Replace all javax imports with jakarta equivalents
- Why: Jakarta EE namespace migration
- Depends on: Step 6
- Verify: No javax imports remain

### Step 13: Update service classes - ProductService imports
- Phase: Business Logic
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do: Replace all javax imports with jakarta equivalents
- Why: Jakarta EE namespace migration
- Depends on: Step 6
- Verify: No javax imports remain

### Step 14: Update service classes - PromoService imports
- Phase: Business Logic
- File: src/main/java/com/redhat/coolstore/service/PromoService.java
- Action: MODIFY
- What to do: Replace all javax imports with jakarta equivalents
- Why: Jakarta EE namespace migration
- Depends on: Step 6
- Verify: No javax imports remain

### Step 15: Update service classes - ShippingService imports
- Phase: Business Logic
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do: Replace all javax imports with jakarta equivalents
- Why: Jakarta EE namespace migration
- Depends on: Step 6
- Verify: No javax imports remain

### Step 16: Update service classes - ShoppingCartService imports
- Phase: Business Logic
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do: Replace all javax imports with jakarta equivalents
- Why: Jakarta EE namespace migration
- Depends on: Step 6
- Verify: No javax imports remain

### Step 17: COMPLEX - Migrate OrderServiceMDB to Reactive Messaging
- Phase: Business Logic - Messaging
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: @MessageDriven with @ActivationConfigProperty for JMS topic
  - AFTER: @Incoming with SmallRye Reactive Messaging
  - Specific changes:
    1. Remove: @MessageDriven annotation and all @ActivationConfigProperty
    2. Remove: implements MessageListener
    3. Remove: javax.jms imports
    4. Add: import org.eclipse.microprofile.reactive.messaging.Incoming;
    5. Add: import jakarta.inject.Inject;
    6. Change method signature: `@Incoming("orders") public void onMessage(String orderStr)`
    7. Update method body: remove TextMessage casting, work directly with String
    8. Remove: JMSException handling (method doesn't throw it anymore)
- Why: Quarkus doesn't support JMS MDBs, uses SmallRye Reactive Messaging
- Depends on: Step 11, Step 12
- Verify: No @MessageDriven, has @Incoming annotation, no JMS imports

### Step 18: COMPLEX - Migrate InventoryNotificationMDB to Reactive Messaging
- Phase: Business Logic - Messaging
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: @MessageDriven with JMS configuration
  - AFTER: @Incoming with SmallRye Reactive Messaging
  - Specific changes: Same pattern as Step 17
    1. Remove @MessageDriven, @ActivationConfigProperty
    2. Remove implements MessageListener
    3. Replace with @Incoming annotation
    4. Update imports: remove javax.jms, add org.eclipse.microprofile.reactive.messaging
    5. Replace all javax imports with jakarta
- Why: Quarkus doesn't support JMS MDBs
- Depends on: Step 11
- Verify: No @MessageDriven, has @Incoming annotation

### Step 19: Update REST Application class
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ws.rs.*` with `import jakarta.ws.rs.*`
  - Keep @ApplicationPath("/services")
  - Keep extends Application
- Why: Jakarta namespace migration
- Depends on: Step 3
- Verify: Uses jakarta.ws.rs imports

### Step 20: Update CartEndpoint REST class
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do:
  - Replace all javax.ws.rs imports with jakarta.ws.rs
  - Replace all javax.inject imports with jakarta.inject
  - Replace all javax.enterprise imports with jakarta.enterprise
  - Keep @SessionScoped (Quarkus supports it)
  - Update method signatures if needed (should work as-is)
- Why: Jakarta namespace migration
- Depends on: Step 19
- Verify: Uses jakarta imports, no javax imports

### Step 21: Update OrderEndpoint REST class
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do: Replace all javax imports with jakarta equivalents (javax.ws.rs → jakarta.ws.rs, javax.inject → jakarta.inject)
- Why: Jakarta namespace migration
- Depends on: Step 19
- Verify: Uses jakarta imports

### Step 22: Update ProductEndpoint REST class
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do: Replace all javax imports with jakarta equivalents
- Why: Jakarta namespace migration
- Depends on: Step 19
- Verify: Uses jakarta imports

### Step 23: Update ShoppingCartService messaging integration
- Phase: Business Logic - Messaging
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - Find any JMS-related code (JMSContext, Topic, MessageProducer)
  - Replace with @Channel and Emitter from SmallRye Reactive Messaging
  - Add: `@Inject @Channel("orders") Emitter<String> ordersEmitter;`
  - Replace JMS send calls with: `ordersEmitter.send(orderJson);`
  - Remove JMS imports
- Why: Replace JMS topic publishing with reactive messaging
- Depends on: Step 16, Step 17
- Verify: No JMS imports, uses @Channel and Emitter

### Step 24: Add reactive messaging channel configuration to application.properties
- Phase: Configuration
- File: src/main/resources/application.properties
- Action: MODIFY
- What to do:
  - Add SmallRye Reactive Messaging in-memory connector configuration
  - Configure "orders" channel as in-memory
  - Example:
    ```
    mp.messaging.outgoing.orders.connector=smallrye-in-memory
    mp.messaging.incoming.orders.connector=smallrye-in-memory
    ```
- Why: Configure the reactive messaging channels used by the application
- Depends on: Step 1, Step 17, Step 18, Step 23
- Verify: Messaging channel configuration present

### Step 25: Update beans.xml for Jakarta namespace
- Phase: Configuration
- File: src/main/webapp/WEB-INF/beans.xml
- Action: MODIFY
- What to do:
  - Update XML namespace from javax to jakarta if present
  - Or move to src/main/resources/META-INF/beans.xml (Quarkus standard location)
  - Or delete entirely (beans.xml is optional in Quarkus with Arc)
- Why: Jakarta namespace and Quarkus conventions
- Depends on: none
- Verify: File updated or moved or deleted

### Step 26: Handle web.xml
- Phase: Configuration
- File: src/main/webapp/WEB-INF/web.xml
- Action: DELETE
- What to do: Delete this file - it only contains <distributable/> which is not needed for initial migration
- Why: Quarkus doesn't use web.xml. Clustering/session distribution handled differently
- Depends on: Step 1
- Verify: File deleted

### Step 27: Update Flyway migration configuration in application.properties
- Phase: Configuration
- File: src/main/resources/application.properties
- Action: MODIFY
- What to do:
  - Add Flyway configuration properties
  - Example:
    ```
    quarkus.flyway.migrate-at-start=true
    quarkus.flyway.locations=db/migration
    ```
- Why: Configure Flyway to run migrations on startup
- Depends on: Step 1
- Verify: Flyway configuration present

### Step 28: Move static resources for Quarkus serving
- Phase: Configuration
- File: src/main/resources/META-INF/resources/
- Action: CREATE
- What to do:
  - Create directory src/main/resources/META-INF/resources/
  - Copy all content from src/main/webapp/ (except WEB-INF/) to src/main/resources/META-INF/resources/
  - This includes: app/, bower_components/, partials/, index.jsp → index.html, etc.
- Why: Quarkus serves static content from META-INF/resources
- Depends on: Step 26
- Verify: Static files copied to META-INF/resources

### Step 29: Convert index.jsp to index.html
- Phase: Configuration
- File: src/main/resources/META-INF/resources/index.html
- Action: MODIFY
- What to do:
  - Rename index.jsp to index.html
  - Remove any JSP directives if present
  - Keep HTML content as-is
- Why: Quarkus doesn't support JSP, serve as static HTML
- Depends on: Step 28
- Verify: index.html exists, no JSP syntax

### Step 30: Handle health.jsp
- Phase: Configuration
- File: src/main/webapp/health.jsp
- Action: DELETE
- What to do: Delete health.jsp - Quarkus provides built-in health endpoints
- Why: Use Quarkus SmallRye Health instead
- Depends on: Step 28
- Verify: File deleted

### Step 31: Add Quarkus health extension to pom.xml
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do:
  - Add quarkus-smallrye-health dependency
  - Health endpoints will be available at /q/health
- Why: Replace custom health.jsp with Quarkus health checks
- Depends on: Step 4
- Verify: quarkus-smallrye-health dependency present

### Step 32: Remove WebLogic legacy classes
- Phase: Cleanup
- File: src/main/java/weblogic/application/ApplicationLifecycleListener.java
- Action: DELETE
- What to do: Delete this file - appears to be unused legacy code
- Why: Not applicable to Quarkus
- Depends on: none
- Verify: File deleted

### Step 33: Remove WebLogic ApplicationLifecycleEvent
- Phase: Cleanup
- File: src/main/java/weblogic/application/ApplicationLifecycleEvent.java
- Action: DELETE
- What to do: Delete this file - appears to be unused legacy code
- Why: Not applicable to Quarkus
- Depends on: none
- Verify: File deleted

### Step 34: Update keycloak.json location
- Phase: Configuration
- File: src/main/resources/META-INF/resources/keycloak.json
- Action: CREATE
- What to do: Copy keycloak.json from src/main/webapp/ to src/main/resources/META-INF/resources/ if not already done in Step 28
- Why: Frontend needs access to Keycloak config
- Depends on: Step 28
- Verify: keycloak.json in correct location for static serving

## Verification

### Build
```bash
./mvnw clean package -DskipTests
```

### Test
```bash
./mvnw test
```
(Note: The pom.xml currently has `<maven.test.skip>true</maven.test.skip>` - tests are skipped)

### Runtime
Start the application:
```bash
java -jar target/quarkus-app/quarkus-run.jar
```

Or in dev mode:
```bash
./mvnw quarkus:dev
```

Access points:
- Application: http://localhost:8080
- Health check: http://localhost:8080/q/health
- Dev UI: http://localhost:8080/q/dev (dev mode only)

### Blackbox
Based on README instructions:

1. **Prerequisites**: Start PostgreSQL database:
   ```bash
   podman run --name myPostgresDb \
      -p 5432:5432 \
      -e POSTGRES_USER=postgresUser \
      -e POSTGRES_PASSWORD=postgresPW \
      -e POSTGRES_DB=postgresDB \
      -d postgres
   ```

2. **Start Keycloak** on port 8081 and import realm-export.json

3. **Start Application**: `java -jar target/quarkus-app/quarkus-run.jar`

4. **Test key flows**:
   - Navigate to http://localhost:8080
   - Click "Sign in" and authenticate with Keycloak user
   - Browse products
   - Add items to cart
   - Complete checkout process
   - Verify order is processed (check application logs for message processing)

5. **Expected results**:
   - UI loads correctly
   - Keycloak authentication works
   - Cart operations work (add/remove items)
   - Checkout creates order
   - Order message is processed by reactive messaging consumer
   - Inventory updated after order

## Notes

### Important Considerations
1. **Session Clustering**: The original application uses `standalone-full-ha.xml` for clustering. For clustering in Quarkus:
   - Add `quarkus-infinispan-client` extension
   - Configure Infinispan for distributed sessions
   - Or use stateless approach with JWT tokens

2. **Reactive Messaging**: Current plan uses in-memory connector. For production with multiple instances:
   - Switch to Kafka or AMQP connector
   - Update configuration in application.properties

3. **Transaction Management**: Java EE container-managed transactions are replaced by Quarkus transaction management (ArC + Hibernate)

4. **EntityManager Injection**: Services that inject EntityManager via the Resources producer will need updates:
   - Either inject EntityManager directly with `@Inject`
   - Or adopt Panache repositories for simplified data access

5. **CORS**: If frontend and backend are on different ports in dev, add CORS configuration:
   ```
   quarkus.http.cors=true
   quarkus.http.cors.origins=http://localhost:4200
   ```

### Migration Gotchas
- **Session scope**: @SessionScoped works in Quarkus but requires HTTP session. Verify session handling
- **JNDI**: No JNDI in Quarkus - all resources configured via application.properties
- **Deployment descriptors**: Most are optional in Quarkus
- **Static resources**: Must be in META-INF/resources, not WEB-INF

### Post-Migration Optimizations
Consider these after successful migration:
1. Adopt Panache for repository pattern
2. Use Quarkus RestClient for external service calls
3. Add native compilation support
4. Implement Quarkus-specific health checks
5. Add metrics with micrometer
6. Consider reactive endpoints with Mutiny
