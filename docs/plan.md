# Migration Plan

## Goal
Migrate a Java EE 7 monolith application from JBoss EAP 7.4 to Quarkus 3

## Source → Target
Java EE 7 on JBoss EAP 7.4 → Quarkus 3

## Scope
- Files affected: 17
- Estimated complexity: High
- Hardest areas:
  1. JMS message-driven beans (OrderServiceMDB, InventoryNotificationMDB) - require conversion to reactive messaging
  2. JNDI lookups and resource injection - need replacement with CDI
  3. Remote EJB (ShippingService) - must be converted to REST endpoints

## Key Decisions Applied
- **Reactive Messaging over JMS**: Convert JMS Topics and MDBs to MicroProfile Reactive Messaging with SmallRye. This is the recommended Quarkus approach for asynchronous messaging.
- **Session Management**: Convert @Stateful ShoppingCartService to @ApplicationScoped with external session state management, following Quarkus best practices of keeping services stateless.
- **Database Configuration**: Migrate persistence.xml to application.properties using Quarkus datasource and Hibernate ORM configuration format.
- **Packaging**: Change from WAR to JAR packaging as Quarkus applications are packaged as uber-jars or native executables.
- **Hibernate Sequences**: Explicitly configure sequence generators to maintain database compatibility with the new Hibernate 6 default behavior in Quarkus 3.

## Approach

**Phase 1: Build Configuration** - Update pom.xml to adopt Quarkus BOM, plugins, and dependencies. Change packaging from WAR to JAR. Remove Java EE dependencies and add Quarkus extensions.

**Phase 2: Configuration Files** - Migrate persistence.xml to application.properties with Quarkus datasource and Hibernate ORM settings. Create application.properties with messaging channels configuration. Remove or mark beans.xml as ignorable.

**Phase 3: Models & Entities** - Update entity classes to handle Hibernate 6 sequence generation changes and ensure compatibility with Quarkus 3.

**Phase 4: Persistence Layer** - Replace @PersistenceContext with @Inject for EntityManager. Remove @Produces from EntityManager producer as Quarkus auto-configures it.

**Phase 5: Service Layer - Simple** - Convert @Stateless EJBs to @ApplicationScoped CDI beans. Add @Transactional annotations where needed.

**Phase 6: Service Layer - Complex** - Convert @Stateful EJB to @ApplicationScoped. Convert @MessageDriven beans to reactive messaging consumers. Replace JMS Topic injection with MicroProfile Emitters. Replace JNDI lookups with CDI injection. Convert @Remote EJB to REST endpoint.

**Phase 7: REST Layer** - Update JAX-RS application class and ensure REST endpoints are Quarkus-compatible.

**Phase 8: Utilities** - Update CDI producers to remove unnecessary @Produces annotations where appropriate.

**Phase 9: Cleanup** - Remove deployment descriptors (web.xml, beans.xml) that are no longer needed in Quarkus.

## Steps

### Step 1: Update POM packaging to JAR
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Change `<packaging>war</packaging>` to `<packaging>jar</packaging>` on line 9
- Why: Quarkus applications are packaged as JAR files, not WAR files
- Depends on: none
- Verify: `grep -q '<packaging>jar</packaging>' pom.xml`

### Step 2: Add Quarkus BOM to POM
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add Quarkus BOM to dependency management section:
  - Add properties section with quarkus.platform.group-id, quarkus.platform.artifact-id, quarkus.platform.version (3.1.0.Final or later)
  - Add dependencyManagement section importing quarkus-bom
  - Set compiler properties: maven.compiler.release=11, compiler-plugin.version=3.10.1, surefire-plugin.version=3.0.0
- Why: Quarkus BOM manages versions of Quarkus dependencies
- Depends on: Step 1
- Verify: `grep -q 'quarkus-bom' pom.xml`

### Step 3: Add Quarkus Maven plugins
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add to build/plugins section:
  - quarkus-maven-plugin with build, generate-code, generate-code-tests goals
  - Update maven-compiler-plugin to version ${compiler-plugin.version} with `-parameters` compiler arg
  - Add maven-surefire-plugin with Quarkus-specific configuration
  - Add maven-failsafe-plugin for integration tests
- Why: Required for Quarkus build process and testing
- Depends on: Step 2
- Verify: `grep -q 'quarkus-maven-plugin' pom.xml`

### Step 4: Add native build profile
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Add Maven profile with id="native" that sets quarkus.package.type=native and skipITs=false when activated
- Why: Enables optional native compilation support
- Depends on: Step 3
- Verify: `grep -q 'quarkus.package.type' pom.xml`

### Step 5: Replace Java EE dependencies with Quarkus extensions
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Remove Java EE dependencies and add Quarkus extensions:
  - Remove: javaee-web-api, javaee-api, jboss-jms-api_2.0_spec, jboss-rmi-api_1.0_spec
  - Add (without version, managed by BOM):
    - quarkus-hibernate-orm
    - quarkus-hibernate-orm-panache (optional, for simplified persistence)
    - quarkus-jdbc-postgresql
    - quarkus-resteasy-reactive-jackson (or quarkus-resteasy-jackson)
    - quarkus-smallrye-reactive-messaging-amqp (for messaging)
    - quarkus-arc (CDI)
    - quarkus-flyway (keep flyway-core or use quarkus-flyway)
    - quarkus-smallrye-health (optional, for health checks)
  - Keep flyway-core dependency but update version if needed
- Why: Quarkus uses extensions instead of Java EE APIs
- Depends on: Step 2
- Verify: `grep -q 'quarkus-hibernate-orm' pom.xml && ! grep -q 'javaee-api' pom.xml`

### Step 6: Create Quarkus application.properties
- Phase: Configuration Files
- File: src/main/resources/application.properties
- Action: CREATE
- What to do: Create application.properties with:
  ```
  # Datasource configuration
  quarkus.datasource.db-kind=postgresql
  quarkus.datasource.username=postgresUser
  quarkus.datasource.password=postgresPW
  quarkus.datasource.jdbc.url=jdbc:postgresql://127.0.0.1:5432/postgresDB
  
  # Hibernate ORM configuration
  quarkus.hibernate-orm.database.generation=none
  quarkus.hibernate-orm.log.sql=false
  quarkus.hibernate-orm.log.format-sql=true
  
  # Flyway configuration
  quarkus.flyway.migrate-at-start=true
  quarkus.flyway.locations=classpath:db/migration
  
  # REST path (JAX-RS application path)
  quarkus.resteasy.path=/services
  
  # Reactive Messaging configuration for orders topic
  mp.messaging.outgoing.orders.connector=smallrye-amqp
  mp.messaging.outgoing.orders.address=orders
  mp.messaging.incoming.orders-in.connector=smallrye-amqp
  mp.messaging.incoming.orders-in.address=orders
  
  # HTTP port
  quarkus.http.port=8080
  ```
- Why: Quarkus uses application.properties for centralized configuration instead of XML files
- Depends on: Step 5
- Verify: `test -f src/main/resources/application.properties`

### Step 7: Mark persistence.xml for deletion awareness
- Phase: Configuration Files
- File: src/main/resources/META-INF/persistence.xml
- Action: MODIFY
- What to do: Add comment at top: `<!-- This file is no longer used in Quarkus. Configuration moved to application.properties. File kept for reference but can be deleted. -->`
- Why: Document that persistence.xml is superseded by application.properties
- Depends on: Step 6
- Verify: `grep -q 'no longer used' src/main/resources/META-INF/persistence.xml`

### Step 8: Update Order entity for Hibernate 6 sequence generation
- Phase: Models & Entities
- File: src/main/java/com/redhat/coolstore/model/Order.java
- Action: MODIFY
- What to do: Update @GeneratedValue annotation on line 24:
  - Change from: `@GeneratedValue(strategy = GenerationType.AUTO)`
  - Change to: `@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_seq")`
  - Add: `@SequenceGenerator(name = "order_seq", sequenceName = "order_seq", allocationSize = 1)`
- Why: Hibernate 6 in Quarkus 3 uses sequence-per-entity instead of single hibernate_sequence
- Depends on: none
- Verify: `grep -q 'order_seq' src/main/java/com/redhat/coolstore/model/Order.java`

### Step 9: Update OrderItem entity for Hibernate 6 sequence generation
- Phase: Models & Entities
- File: src/main/java/com/redhat/coolstore/model/OrderItem.java
- Action: MODIFY
- What to do: Update @GeneratedValue annotation on line 18:
  - Change from: `@GeneratedValue(strategy = GenerationType.AUTO)`
  - Change to: `@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "orderitem_seq")`
  - Add: `@SequenceGenerator(name = "orderitem_seq", sequenceName = "orderitem_seq", allocationSize = 1)`
- Why: Hibernate 6 in Quarkus 3 uses sequence-per-entity instead of single hibernate_sequence
- Depends on: none
- Verify: `grep -q 'orderitem_seq' src/main/java/com/redhat/coolstore/model/OrderItem.java`

### Step 10: Replace @PersistenceContext with @Inject in Resources
- Phase: Persistence Layer
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do: 
  - Replace import: `javax.persistence.PersistenceContext` with `javax.inject.Inject`
  - Replace annotation on line 11: `@PersistenceContext` with `@Inject`
  - Remove @Produces annotation from line 14
  - Remove getEntityManager() method on line 15
  - This class may no longer be needed; the EntityManager can be injected directly where needed
- Why: Quarkus auto-configures EntityManager and @Produces is not needed (and conflicts with Quarkus)
- Depends on: Step 6
- Verify: `grep -q '@Inject' src/main/java/com/redhat/coolstore/persistence/Resources.java && ! grep -q '@PersistenceContext' src/main/java/com/redhat/coolstore/persistence/Resources.java`

### Step 11: Convert CatalogService to CDI bean with transactions
- Phase: Service Layer - Simple
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do:
  - Replace import: `javax.ejb.Stateless` with `javax.enterprise.context.ApplicationScoped`
  - Replace annotation on line 17: `@Stateless` with `@ApplicationScoped`
  - Add import: `javax.transaction.Transactional`
  - Add `@Transactional` annotation to updateInventoryItems method (line 45) since it uses em.merge()
  - Update all javax.* imports to jakarta.* equivalents for Quarkus 3
- Why: Quarkus uses CDI beans instead of EJBs; methods with persistence operations need explicit @Transactional
- Depends on: Step 10
- Verify: `grep -q '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/CatalogService.java && grep -q '@Transactional' src/main/java/com/redhat/coolstore/service/CatalogService.java`

### Step 12: Convert OrderService to CDI bean with transactions
- Phase: Service Layer - Simple
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do:
  - Replace import: `javax.ejb.Stateless` with `javax.enterprise.context.ApplicationScoped`
  - Replace annotation on line 12: `@Stateless` with `@ApplicationScoped`
  - Add import: `javax.transaction.Transactional`
  - Add `@Transactional` annotation to the class or save method (line 19) since it uses em.persist()
  - Update all javax.* imports to jakarta.* equivalents for Quarkus 3
- Why: Quarkus uses CDI beans instead of EJBs; methods with persistence operations need explicit @Transactional
- Depends on: Step 10
- Verify: `grep -q '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/OrderService.java && grep -q '@Transactional' src/main/java/com/redhat/coolstore/service/OrderService.java`

### Step 13: Convert ProductService to CDI bean
- Phase: Service Layer - Simple
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do:
  - Replace import: `javax.ejb.Stateless` with `javax.enterprise.context.ApplicationScoped`
  - Replace annotation on line 14: `@Stateless` with `@ApplicationScoped`
  - Update all javax.* imports to jakarta.* equivalents for Quarkus 3
- Why: Quarkus uses CDI beans instead of EJBs
- Depends on: Step 10
- Verify: `grep -q '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ProductService.java && ! grep -q '@Stateless' src/main/java/com/redhat/coolstore/service/ProductService.java`

### Step 14: Convert DataBaseMigrationStartup to CDI bean
- Phase: Service Layer - Simple
- File: src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- Action: MODIFY
- What to do:
  - Replace import: `javax.ejb.Stateless` with `javax.enterprise.context.ApplicationScoped`
  - Replace annotation: `@Stateless` with `@ApplicationScoped`
  - Update all javax.* imports to jakarta.* equivalents for Quarkus 3
- Why: Quarkus uses CDI beans instead of EJBs
- Depends on: Step 10
- Verify: `grep -q '@ApplicationScoped' src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java`

### Step 15: COMPLEX - Convert ShoppingCartService from Stateful to ApplicationScoped
- Phase: Service Layer - Complex
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - BEFORE: `@Stateful` EJB with instance state
  - AFTER: `@ApplicationScoped` CDI bean with externalized state
  - Specific changes:
    1. Replace import: `javax.ejb.Stateful` with `javax.enterprise.context.ApplicationScoped`
    2. Replace annotation on line 16: `@Stateful` with `@ApplicationScoped`
    3. Remove JNDI lookup code (lines 119-121 with InitialContext)
    4. Replace: JNDI lookup for ShoppingCartOrderProcessor with direct CDI @Inject
    5. Add import: `javax.transaction.Transactional`
    6. Add `@Transactional` to methods that need transactions
    7. Update all javax.* imports to jakarta.* equivalents
    8. Remove javax.naming.* imports (Context, InitialContext, NamingException, Hashtable)
  - Note: State management will need to be handled externally (e.g., via HTTP session, database, or cache). For now, convert to stateless pattern.
- Why: @Stateful EJBs are not supported in Quarkus; JNDI is not supported; Quarkus recommends stateless services
- Depends on: Step 10
- Verify: `grep -q '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ShoppingCartService.java && ! grep -q 'InitialContext' src/main/java/com/redhat/coolstore/service/ShoppingCartService.java`

### Step 16: COMPLEX - Convert ShoppingCartOrderProcessor to use Reactive Messaging Emitter
- Phase: Service Layer - Complex
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - BEFORE: `@Stateless` EJB with JMS Topic using `@Resource(lookup = "java:/topic/orders")`
  - AFTER: `@ApplicationScoped` CDI bean with MicroProfile Reactive Messaging Emitter
  - Specific changes:
    1. Replace import: `javax.ejb.Stateless` with `javax.enterprise.context.ApplicationScoped`
    2. Replace annotation on line 12: `@Stateless` with `@ApplicationScoped`
    3. Remove imports: `javax.annotation.Resource`, `javax.jms.JMSContext`, `javax.jms.Topic`
    4. Add imports: `org.eclipse.microprofile.reactive.messaging.Channel`, `org.eclipse.microprofile.reactive.messaging.Emitter`, `io.smallrye.mutiny.Uni`
    5. Replace lines 19-23:
       - Remove: `@Inject private transient JMSContext context;`
       - Remove: `@Resource(lookup = "java:/topic/orders") private Topic ordersTopic;`
       - Add: `@Inject @Channel("orders") Emitter<String> ordersEmitter;`
    6. Update process() method (line 27):
       - Remove: `context.createProducer().send(ordersTopic, Transformers.shoppingCartToJson(cart));`
       - Add: `ordersEmitter.send(Transformers.shoppingCartToJson(cart));`
    7. Update all javax.* imports to jakarta.* equivalents
- Why: JMS is not supported in Quarkus; use MicroProfile Reactive Messaging with SmallRye
- Depends on: Step 6, Step 11
- Verify: `grep -q 'Emitter<String>' src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java && ! grep -q 'javax.jms' src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java`

### Step 17: COMPLEX - Convert OrderServiceMDB to Reactive Messaging consumer
- Phase: Service Layer - Complex
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: `@MessageDriven` EJB with JMS MessageListener
  - AFTER: `@ApplicationScoped` CDI bean with `@Incoming` reactive messaging method
  - Specific changes:
    1. Remove imports: `javax.ejb.ActivationConfigProperty`, `javax.ejb.MessageDriven`, `javax.jms.*`
    2. Add imports: `javax.enterprise.context.ApplicationScoped`, `org.eclipse.microprofile.reactive.messaging.Incoming`, `io.smallrye.mutiny.Uni`
    3. Replace annotations on lines 14-17:
       - Remove: `@MessageDriven(name = "OrderServiceMDB", activationConfig = {...})`
       - Add: `@ApplicationScoped`
    4. Remove: `implements MessageListener`
    5. Replace onMessage method:
       - Remove: `public void onMessage(Message rcvMessage)` signature
       - Add: `@Incoming("orders-in")` annotation
       - Add: `public void processOrder(String orderStr)` signature
       - Remove JMS message unwrapping code
       - Keep business logic: Order order = Transformers.jsonToOrder(orderStr); etc.
    6. Update all javax.* imports to jakarta.* equivalents
    7. Add `@Transactional` to the method since it performs database operations
- Why: @MessageDriven EJBs are not supported in Quarkus; use MicroProfile Reactive Messaging
- Depends on: Step 6, Step 12, Step 11
- Verify: `grep -q '@Incoming' src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java && ! grep -q '@MessageDriven' src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java`

### Step 18: COMPLEX - Convert InventoryNotificationMDB to Reactive Messaging consumer
- Phase: Service Layer - Complex
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: Manual JNDI-based JMS Topic subscriber with WebLogic-specific code
  - AFTER: `@ApplicationScoped` CDI bean with `@Incoming` reactive messaging method
  - Specific changes:
    1. Remove all imports: `javax.jms.*`, `javax.naming.*`, `javax.rmi.PortableRemoteObject`, `java.util.Hashtable`
    2. Add imports: `javax.enterprise.context.ApplicationScoped`, `org.eclipse.microprofile.reactive.messaging.Incoming`
    3. Add: `@ApplicationScoped` annotation to class
    4. Remove: All JNDI-related fields (JNDI_FACTORY, JMS_FACTORY, TOPIC, tcon, tsession, tsubscriber)
    5. Replace onMessage method:
       - Remove: `public void onMessage(Message rcvMessage)` signature
       - Add: `@Incoming("orders-in")` annotation  
       - Add: `public void checkInventory(String orderStr)` signature
       - Remove JMS message unwrapping, keep business logic
    6. Remove: init() and close() methods entirely (lines 49-74)
    7. Remove: getInitialContext() method entirely (lines 76-81)
    8. Update all javax.* imports to jakarta.* equivalents
    9. Add `@Transactional` if needed for any database operations
- Why: JNDI lookups and manual JMS setup are not supported in Quarkus; use declarative reactive messaging
- Depends on: Step 6, Step 11
- Verify: `grep -q '@Incoming' src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java && ! grep -q 'InitialContext' src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java`

### Step 19: COMPLEX - Convert ShippingService from Remote EJB to REST endpoint
- Phase: Service Layer - Complex
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do:
  - BEFORE: `@Stateless @Remote` EJB
  - AFTER: `@ApplicationScoped` CDI bean with JAX-RS REST endpoints
  - Specific changes:
    1. Remove imports: `javax.ejb.Remote`, `javax.ejb.Stateless`
    2. Add imports: `javax.enterprise.context.ApplicationScoped`, `javax.ws.rs.*` (Path, GET, POST, Produces, Consumes, etc.)
    3. Replace annotations on lines 10-12:
       - Remove: `@Stateless` and `@Remote`
       - Add: `@ApplicationScoped` and `@Path("/shipping")`
    4. Remove: `implements ShippingServiceRemote`
    5. Add JAX-RS annotations to methods:
       - calculateShipping: Add `@POST`, `@Path("/calculate")`, `@Consumes(MediaType.APPLICATION_JSON)`, `@Produces(MediaType.APPLICATION_JSON)`
       - calculateShippingInsurance: Add `@POST`, `@Path("/insurance")`, `@Consumes(MediaType.APPLICATION_JSON)`, `@Produces(MediaType.APPLICATION_JSON)`
    6. Update all javax.* imports to jakarta.* equivalents
- Why: Remote EJBs are not supported in Quarkus; convert to REST API
- Depends on: none
- Verify: `grep -q '@Path' src/main/java/com/redhat/coolstore/service/ShippingService.java && ! grep -q '@Remote' src/main/java/com/redhat/coolstore/service/ShippingService.java`

### Step 20: Update Producers to remove unnecessary @Produces
- Phase: Utilities
- File: src/main/java/com/redhat/coolstore/utils/Producers.java
- Action: MODIFY
- What to do:
  - Review the @Produces annotation on line 12
  - If it's producing a Logger or other standard CDI type, it can likely be removed or simplified
  - Update all javax.* imports to jakarta.* equivalents
  - Note: Without seeing the full file content, keep the producer but add comment explaining it may be removable in Quarkus
- Why: Quarkus provides many built-in CDI producers; unnecessary @Produces can be removed
- Depends on: Step 10
- Verify: `grep -q 'jakarta' src/main/java/com/redhat/coolstore/utils/Producers.java`

### Step 21: Update RestApplication for Quarkus
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: MODIFY
- What to do:
  - Keep `@ApplicationPath("/services")` annotation (lines 7-8) OR remove it and use quarkus.resteasy.path in application.properties (already configured in Step 6)
  - Add comment: `// JAX-RS activation is automatic in Quarkus. This class can be removed, or kept for explicit path configuration.`
  - Update all javax.* imports to jakarta.* equivalents
  - The class can remain minimal or be deleted entirely
- Why: JAX-RS activation is automatic in Quarkus
- Depends on: Step 6
- Verify: `grep -q 'jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/RestApplication.java || ! test -f src/main/java/com/redhat/coolstore/rest/RestApplication.java`

### Step 22: Update CartEndpoint imports
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do:
  - Update all javax.* imports to jakarta.* equivalents (javax.ws.rs.* → jakarta.ws.rs.*, javax.inject.Inject → jakarta.inject.Inject)
  - No other changes needed if the endpoint only uses standard JAX-RS annotations
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 15
- Verify: `grep -q 'jakarta' src/main/java/com/redhat/coolstore/rest/CartEndpoint.java`

### Step 23: Update OrderEndpoint imports
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do:
  - Update all javax.* imports to jakarta.* equivalents (javax.ws.rs.* → jakarta.ws.rs.*, javax.inject.Inject → jakarta.inject.Inject)
  - No other changes needed if the endpoint only uses standard JAX-RS annotations
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 12
- Verify: `grep -q 'jakarta' src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java`

### Step 24: Update ProductEndpoint imports
- Phase: REST Layer
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do:
  - Update all javax.* imports to jakarta.* equivalents (javax.ws.rs.* → jakarta.ws.rs.*, javax.inject.Inject → jakarta.inject.Inject)
  - No other changes needed if the endpoint only uses standard JAX-RS annotations
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 13
- Verify: `grep -q 'jakarta' src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java`

### Step 25: Update all model classes imports
- Phase: Models & Entities
- File: src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java
- Action: MODIFY
- What to do: Update all javax.persistence.* imports to jakarta.persistence.*
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: none
- Verify: `grep -q 'jakarta.persistence' src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java`

### Step 26: Update all model classes imports
- Phase: Models & Entities
- File: src/main/java/com/redhat/coolstore/model/InventoryEntity.java
- Action: MODIFY
- What to do: Update all javax.persistence.* imports to jakarta.persistence.*
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: none
- Verify: `grep -q 'jakarta.persistence' src/main/java/com/redhat/coolstore/model/InventoryEntity.java`

### Step 27: Update all model classes imports
- Phase: Models & Entities
- File: src/main/java/com/redhat/coolstore/model/Product.java
- Action: MODIFY
- What to do: Update all javax.* imports to jakarta.* equivalents
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: none
- Verify: `grep -q 'jakarta' src/main/java/com/redhat/coolstore/model/Product.java || ! grep -q 'javax' src/main/java/com/redhat/coolstore/model/Product.java`

### Step 28: Update all model classes imports
- Phase: Models & Entities
- File: src/main/java/com/redhat/coolstore/model/Promotion.java
- Action: MODIFY
- What to do: Update all javax.* imports to jakarta.* equivalents
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: none
- Verify: `grep -q 'jakarta' src/main/java/com/redhat/coolstore/model/Promotion.java || ! grep -q 'javax' src/main/java/com/redhat/coolstore/model/Promotion.java`

### Step 29: Update all model classes imports
- Phase: Models & Entities
- File: src/main/java/com/redhat/coolstore/model/ShoppingCart.java
- Action: MODIFY
- What to do: Update all javax.* imports to jakarta.* equivalents
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: none
- Verify: `grep -q 'jakarta' src/main/java/com/redhat/coolstore/model/ShoppingCart.java || ! grep -q 'javax' src/main/java/com/redhat/coolstore/model/ShoppingCart.java`

### Step 30: Update all model classes imports
- Phase: Models & Entities
- File: src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java
- Action: MODIFY
- What to do: Update all javax.* imports to jakarta.* equivalents
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: none
- Verify: `grep -q 'jakarta' src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java || ! grep -q 'javax' src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java`

### Step 31: Update utility classes imports
- Phase: Utilities
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do: Update all javax.* imports to jakarta.* equivalents
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: none
- Verify: `grep -q 'jakarta' src/main/java/com/redhat/coolstore/utils/StartupListener.java || ! grep -q 'javax' src/main/java/com/redhat/coolstore/utils/StartupListener.java`

### Step 32: Update utility classes imports
- Phase: Utilities
- File: src/main/java/com/redhat/coolstore/utils/Transformers.java
- Action: MODIFY
- What to do: Update all javax.* imports to jakarta.* equivalents (if any)
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: none
- Verify: `grep -q 'jakarta' src/main/java/com/redhat/coolstore/utils/Transformers.java || ! grep -q 'javax' src/main/java/com/redhat/coolstore/utils/Transformers.java`

### Step 33: Delete beans.xml
- Phase: Cleanup
- File: src/main/webapp/WEB-INF/beans.xml
- Action: DELETE
- What to do: Delete this file - CDI is enabled by default in Quarkus and beans.xml content is ignored
- Why: beans.xml descriptor content is ignored in Quarkus and can be removed
- Depends on: All service layer steps (11-19)
- Verify: `! test -f src/main/webapp/WEB-INF/beans.xml`

### Step 34: Delete web.xml
- Phase: Cleanup
- File: src/main/webapp/WEB-INF/web.xml
- Action: DELETE
- What to do: Delete this file - deployment descriptors are not used in Quarkus
- Why: Quarkus does not use web.xml; configuration is done via annotations and application.properties
- Depends on: All service layer steps (11-19), Step 21
- Verify: `! test -f src/main/webapp/WEB-INF/web.xml`

### Step 35: Delete persistence.xml
- Phase: Cleanup
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Delete this file - persistence configuration has been moved to application.properties
- Why: Persistence configuration is now in application.properties
- Depends on: Step 6, Step 7
- Verify: `! test -f src/main/resources/META-INF/persistence.xml`

### Step 36: Handle WebLogic application lifecycle files
- Phase: Cleanup
- File: src/main/java/weblogic/application/ApplicationLifecycleListener.java
- Action: DELETE
- What to do: Delete this file and the entire weblogic package - WebLogic-specific code is not needed in Quarkus
- Why: WebLogic-specific lifecycle management is not applicable to Quarkus
- Depends on: All migration steps complete
- Verify: `! test -d src/main/java/weblogic`

### Step 37: Handle WebLogic application lifecycle files
- Phase: Cleanup
- File: src/main/java/weblogic/application/ApplicationLifecycleEvent.java
- Action: DELETE
- What to do: Delete this file (part of weblogic package deletion)
- Why: WebLogic-specific lifecycle management is not applicable to Quarkus
- Depends on: Step 36
- Verify: `! test -f src/main/java/weblogic/application/ApplicationLifecycleEvent.java`

## Verification

- Build: `mvn clean compile`
- Test: Application has `maven.test.skip=true` in current pom.xml, so no tests to run. After migration: `mvn test` (if tests are enabled)
- Blackbox: 
  1. Start PostgreSQL database: `podman run --name myPostgresDb -p 5432:5432 -e POSTGRES_USER=postgresUser -e POSTGRES_PASSWORD=postgresPW -e POSTGRES_DB=postgresDB -d postgres`
  2. Start Quarkus application: `mvn quarkus:dev`
  3. Navigate to http://localhost:8080
  4. Verify the coolstore application loads
  5. Test product catalog display
  6. Test adding items to cart
  7. Note: Authentication will need to be reconfigured for Quarkus (Keycloak integration via quarkus-oidc extension) - this is beyond the current migration scope but should be addressed
  8. Test checkout process (this will test the reactive messaging chain: ShoppingCartOrderProcessor → orders topic → OrderServiceMDB consumer)

## Notes

### Messaging Infrastructure
The current application uses JBoss/WildFly ActiveMQ for JMS messaging. In Quarkus, you have several options:
- **AMQP/ActiveMQ Artemis**: Use `quarkus-smallrye-reactive-messaging-amqp` (configured in step 5) with an external ActiveMQ Artemis broker
- **Kafka**: Alternatively use `quarkus-smallrye-reactive-messaging-kafka` for production deployments
- **In-memory**: For development/testing, you can use `quarkus-smallrye-reactive-messaging-in-memory`

The application.properties in Step 6 configures AMQP. You'll need to run an AMQP broker (e.g., Apache ActiveMQ Artemis) or switch to Kafka.

### Keycloak/Authentication
The current application uses Keycloak for authentication. This is not migrated in the current plan but will need:
- Add `quarkus-oidc` extension
- Configure OIDC properties in application.properties pointing to Keycloak
- Update the frontend keycloak.json to work with Quarkus CORS and OIDC settings

### Static Resources (Frontend)
The Angular frontend in `src/main/webapp` needs to be reorganized:
- Move to `src/main/resources/META-INF/resources/` for Quarkus to serve them
- Or serve the frontend separately (recommended for production)

### Stateful Session Management
ShoppingCartService was `@Stateful` in Java EE, maintaining per-user cart state. In Quarkus:
- Option 1: Use HTTP session scope (add `quarkus-undertow` and use `@SessionScoped`)
- Option 2: Store cart in database with user session ID
- Option 3: Use Redis/Infinispan for distributed session storage (recommended for cloud)
- The current migration converts it to `@ApplicationScoped` as a starting point

### Database Schema
The migration assumes the existing PostgreSQL schema is compatible. The Hibernate sequence generation changes (Steps 8-9) may require database migration:
- Run with `quarkus.hibernate-orm.database.generation=update` initially to let Hibernate create sequences
- Or manually create sequences: `CREATE SEQUENCE order_seq START 1;` and `CREATE SEQUENCE orderitem_seq START 1;`
- After verification, set to `quarkus.hibernate-orm.database.generation=none`

### Dependencies Not Migrated
- **Flyway**: Kept as-is, but verify compatibility. Consider using `quarkus-flyway` extension instead.
- **Frontend dependencies**: bower_components and Angular 1.x are outdated. Consider modernizing the frontend separately.

### Java Version
- Current application uses Java 8
- Quarkus 3 requires Java 11 minimum (Java 17 recommended)
- Ensure JDK 11+ is used for compilation and runtime

### Performance Considerations
- Quarkus starts much faster than JBoss EAP
- Native compilation can reduce memory footprint by 90%+
- Reactive messaging is more efficient than traditional JMS
