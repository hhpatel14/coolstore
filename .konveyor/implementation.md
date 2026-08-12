# Implementation Plan: Java EE 7 to Quarkus 3 Migration

**Project:** coolstore-monolith  
**Generated:** 2026-08-12  
**Domain Skill:** javaee-to-quarkus (phases: build-config, app-config, ejb-to-cdi, messaging, lifecycle, cleanup)

---

## Overview

This implementation plan provides step-by-step instructions for migrating the coolstore-monolith application from Java EE 7 to Quarkus 3. The plan follows the domain skill's phase order with build gates between phases.

**Total Steps:** 51  
**Estimated Effort:** 6-8 hours  
**Phases:** 6

---

## Phase 1: Build Config (9 steps)

**Goal:** Transform pom.xml from WAR to JAR packaging with Quarkus dependencies.

**Build Command:** `mvn compile`

### Step 1.1: Update Java version to 17
**Type:** SIMPLE  
**Files:** pom.xml  
**Action:** UPDATE  
**Detail:**
```xml
<!-- BEFORE -->
<source>1.8</source>
<target>1.8</target>

<!-- AFTER -->
<source>17</source>
<target>17</target>
<release>17</release>
```

**Rationale:** Quarkus 3 requires Java 17 minimum.

---

### Step 1.2: Change packaging from WAR to JAR
**Type:** SIMPLE  
**Files:** pom.xml  
**Action:** UPDATE  
**Detail:**
```xml
<!-- BEFORE -->
<packaging>war</packaging>

<!-- AFTER -->
<packaging>jar</packaging>
```

**Rationale:** Quarkus applications package as standalone JARs, not WARs.

---

### Step 1.3: Add Quarkus BOM to dependencyManagement
**Type:** SIMPLE  
**Files:** pom.xml  
**Action:** ADD  
**Detail:**
```xml
<!-- Add after </properties> and before <dependencies> -->
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

**Rationale:** Quarkus BOM provides consistent versions for all Quarkus extensions.

---

### Step 1.4: Replace Java EE API dependencies with Quarkus extensions
**Type:** COMPLEX  
**Files:** pom.xml  
**Action:** UPDATE  
**Detail:**
```xml
<!-- REMOVE these dependencies -->
<dependency>
    <groupId>javax</groupId>
    <artifactId>javaee-web-api</artifactId>
    <version>7.0</version>
    <scope>provided</scope>
</dependency>
<dependency>
    <groupId>javax</groupId>
    <artifactId>javaee-api</artifactId>
    <version>7.0</version>
    <scope>provided</scope>
</dependency>
<dependency>
    <groupId>org.jboss.spec.javax.jms</groupId>
    <artifactId>jboss-jms-api_2.0_spec</artifactId>
    <version>2.0.0.Final</version>
</dependency>
<dependency>
    <groupId>org.jboss.spec.javax.rmi</groupId>
    <artifactId>jboss-rmi-api_1.0_spec</artifactId>
    <version>1.0.2.Final</version>
</dependency>

<!-- ADD Quarkus extensions -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-arc</artifactId>
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
    <artifactId>quarkus-resteasy-jackson</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-reactive-messaging</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-reactive-messaging-in-memory</artifactId>
</dependency>
```

**Rationale:** Quarkus provides modular extensions instead of monolithic Java EE APIs.

**Notes:** 
- quarkus-arc = CDI implementation
- quarkus-hibernate-orm = JPA with Hibernate
- quarkus-jdbc-postgresql = Database driver (change if using different DB)
- quarkus-resteasy-jackson = JAX-RS with JSON support
- Messaging extensions = SmallRye Reactive Messaging for async communication

---

### Step 1.5: Update Flyway dependency to Quarkus extension
**Type:** SIMPLE  
**Files:** pom.xml  
**Action:** UPDATE  
**Detail:**
```xml
<!-- REMOVE -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
    <version>4.1.2</version>
</dependency>

<!-- ADD -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-flyway</artifactId>
</dependency>
```

**Rationale:** Quarkus Flyway extension provides better integration and automatic migration on startup.

---

### Step 1.6: Replace maven-war-plugin with quarkus-maven-plugin
**Type:** SIMPLE  
**Files:** pom.xml  
**Action:** UPDATE  
**Detail:**
```xml
<!-- REMOVE -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-war-plugin</artifactId>
    <version>3.2.0</version>
</plugin>

<!-- ADD -->
<plugin>
    <groupId>io.quarkus.platform</groupId>
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

**Rationale:** Quarkus Maven plugin handles build, dev mode, and native compilation.

---

### Step 1.7: Add quarkus.platform.version property
**Type:** SIMPLE  
**Files:** pom.xml  
**Action:** ADD  
**Detail:**
```xml
<!-- Add to <properties> section -->
<quarkus.platform.version>3.2.9.Final</quarkus.platform.version>
<compiler-plugin.version>3.11.0</compiler-plugin.version>
```

**Rationale:** Centralized version management for Quarkus platform.

---

### Step 1.8: Update maven-compiler-plugin version
**Type:** SIMPLE  
**Files:** pom.xml  
**Action:** UPDATE  
**Detail:**
```xml
<plugin>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>${compiler-plugin.version}</version>
    <configuration>
        <encoding>${project.encoding}</encoding>
        <release>17</release>
    </configuration>
</plugin>
```

**Rationale:** Newer compiler plugin version supports Java 17.

---

### Step 1.9: Remove finalName override
**Type:** SIMPLE  
**Files:** pom.xml  
**Action:** UPDATE  
**Detail:**
```xml
<!-- REMOVE this line from <build> -->
<finalName>ROOT</finalName>

<!-- Quarkus will use: ${project.artifactId}-${project.version}-runner.jar -->
```

**Rationale:** Quarkus follows standard JAR naming conventions.

---

**Phase 1 Build Gate:** Run `mvn clean compile`. Expected warnings about missing application.properties are acceptable. Compilation errors must be fixed before Phase 2.

---

## Phase 2: App Config (6 steps)

**Goal:** Replace XML configuration files with application.properties.

**Build Command:** `mvn compile`

### Step 2.1: Create application.properties
**Type:** SIMPLE  
**Files:** src/main/resources/application.properties  
**Action:** CREATE  
**Detail:**
```properties
# Datasource configuration
quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=coolstore
quarkus.datasource.password=coolstore
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/coolstore

# Hibernate ORM configuration
quarkus.hibernate-orm.database.generation=none
quarkus.hibernate-orm.log.sql=false
quarkus.hibernate-orm.sql-load-script=no-file

# Flyway configuration
quarkus.flyway.migrate-at-start=true
quarkus.flyway.locations=classpath:db/migration

# Reactive Messaging - in-memory channels
mp.messaging.outgoing.orders.connector=smallrye-in-memory
mp.messaging.incoming.orders-service.connector=smallrye-in-memory
mp.messaging.incoming.orders-service.broadcast=true
mp.messaging.incoming.inventory-notification.connector=smallrye-in-memory
mp.messaging.incoming.inventory-notification.broadcast=true

# Application configuration
quarkus.http.port=8080
quarkus.log.level=INFO
```

**Rationale:** Quarkus uses application.properties for all configuration.

**Notes:**
- Update datasource credentials and URL for your environment
- broadcast=true ensures both MDB replacements receive messages from the same topic
- Flyway locations point to existing db/migration directory

---

### Step 2.2: Delete persistence.xml
**Type:** SIMPLE  
**Files:** src/main/resources/META-INF/persistence.xml  
**Action:** DELETE  
**Detail:**
```
rm src/main/resources/META-INF/persistence.xml
```

**Rationale:** Persistence configuration moved to application.properties.

---

### Step 2.3: Delete web.xml
**Type:** SIMPLE  
**Files:** src/main/webapp/WEB-INF/web.xml  
**Action:** DELETE  
**Detail:**
```
rm src/main/webapp/WEB-INF/web.xml
```

**Rationale:** Quarkus JAR packaging doesn't use servlet descriptors.

---

### Step 2.4: Delete beans.xml
**Type:** SIMPLE  
**Files:** src/main/webapp/WEB-INF/beans.xml  
**Action:** DELETE  
**Detail:**
```
rm src/main/webapp/WEB-INF/beans.xml
```

**Rationale:** CDI is automatically enabled in Quarkus.

---

### Step 2.5: Move webapp resources to META-INF/resources
**Type:** SIMPLE  
**Files:** src/main/webapp/*  
**Action:** MOVE  
**Detail:**
```bash
# Quarkus serves static content from META-INF/resources
mkdir -p src/main/resources/META-INF/resources
mv src/main/webapp/*.jsp src/main/resources/META-INF/resources/
mv src/main/webapp/*.json src/main/resources/META-INF/resources/
mv src/main/webapp/app src/main/resources/META-INF/resources/
mv src/main/webapp/bower_components src/main/resources/META-INF/resources/
mv src/main/webapp/partials src/main/resources/META-INF/resources/
```

**Rationale:** Quarkus serves static resources from classpath META-INF/resources.

**Notes:** WEB-INF directory can be deleted after moving resources.

---

### Step 2.6: Delete WEB-INF directory
**Type:** SIMPLE  
**Files:** src/main/webapp/WEB-INF  
**Action:** DELETE  
**Detail:**
```bash
rm -rf src/main/webapp/WEB-INF
rm -rf src/main/webapp  # if empty after moving resources
```

**Rationale:** No longer needed in JAR packaging.

---

**Phase 2 Build Gate:** Run `mvn clean compile`. Expect javax.* import errors in Java files - these will be fixed in Phase 3.

---

## Phase 3: EJB to CDI (16 steps)

**Goal:** Replace EJB annotations with CDI, remove JNDI lookups, convert to Jakarta namespace.

**Build Command:** `mvn compile`

### Step 3.1: Update all javax.persistence imports to jakarta.persistence
**Type:** SIMPLE  
**Files:** All JPA entities (8 files)  
**Action:** UPDATE  
**Detail:**
```bash
# Automated replacement across all Java files
find src/main/java -name "*.java" -exec sed -i 's/javax\.persistence/jakarta.persistence/g' {} \;
```

**Affected Files:**
- CatalogItemEntity.java
- InventoryEntity.java
- Order.java
- OrderItem.java
- Product.java
- Promotion.java
- ShoppingCart.java
- ShoppingCartItem.java

**Rationale:** Quarkus 3 uses Jakarta EE 10 namespace.

---

### Step 3.2: Update all javax.inject imports to jakarta.inject
**Type:** SIMPLE  
**Files:** All service and REST classes  
**Action:** UPDATE  
**Detail:**
```bash
find src/main/java -name "*.java" -exec sed -i 's/javax\.inject/jakarta.inject/g' {} \;
```

**Rationale:** CDI moved to jakarta.inject in Jakarta EE 9+.

---

### Step 3.3: Update all javax.enterprise imports to jakarta.enterprise
**Type:** SIMPLE  
**Files:** CartEndpoint.java, Resources.java  
**Action:** UPDATE  
**Detail:**
```bash
find src/main/java -name "*.java" -exec sed -i 's/javax\.enterprise/jakarta.enterprise/g' {} \;
```

**Rationale:** CDI context annotations moved to jakarta.enterprise.

---

### Step 3.4: Update all javax.ws.rs imports to jakarta.ws.rs
**Type:** SIMPLE  
**Files:** All REST endpoints (4 files)  
**Action:** UPDATE  
**Detail:**
```bash
find src/main/java -name "*.java" -exec sed -i 's/javax\.ws\.rs/jakarta.ws.rs/g' {} \;
```

**Affected Files:**
- CartEndpoint.java
- OrderEndpoint.java
- ProductEndpoint.java
- RestApplication.java

**Rationale:** JAX-RS moved to jakarta.ws.rs in Jakarta EE 9+.

---

### Step 3.5: Replace @Stateless with @ApplicationScoped in OrderService
**Type:** SIMPLE  
**Files:** src/main/java/com/redhat/coolstore/service/OrderService.java  
**Action:** UPDATE  
**Detail:**
```java
// REMOVE
import javax.ejb.Stateless;
@Stateless
public class OrderService {

// ADD
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class OrderService {
    
    // Add @Transactional to save method
    @Transactional
    public void save(Order order) {
        em.persist(order);
    }
```

**Rationale:** @ApplicationScoped CDI beans replace @Stateless EJBs; @Transactional provides transaction management.

---

### Step 3.6: Replace @Stateless with @ApplicationScoped in ShoppingCartOrderProcessor
**Type:** SIMPLE  
**Files:** src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java  
**Action:** UPDATE  
**Detail:**
```java
// REMOVE
import javax.ejb.Stateless;
import javax.annotation.Resource;
import javax.jms.JMSContext;
import javax.jms.Topic;

@Stateless
public class ShoppingCartOrderProcessor {
    @Inject
    private transient JMSContext context;

    @Resource(lookup = "java:/topic/orders")
    private Topic ordersTopic;
    
    public void process(ShoppingCart cart) {
        log.info("Sending order from processor: ");
        context.createProducer().send(ordersTopic, Transformers.shoppingCartToJson(cart));
    }
}

// ADD (Reactive Messaging replacement in Phase 4)
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@ApplicationScoped
public class ShoppingCartOrderProcessor {
    
    @Inject
    @Channel("orders")
    Emitter<String> ordersEmitter;
    
    public void process(ShoppingCart cart) {
        log.info("Sending order from processor: ");
        ordersEmitter.send(Transformers.shoppingCartToJson(cart));
    }
}
```

**Rationale:** Replace JMS producer with Reactive Messaging Emitter.

---

### Step 3.7: Replace @Stateless @Remote with @ApplicationScoped in ShippingService
**Type:** COMPLEX  
**Files:** src/main/java/com/redhat/coolstore/service/ShippingService.java  
**Action:** UPDATE  
**Detail:**
```java
// REMOVE
import javax.ejb.Remote;
import javax.ejb.Stateless;

@Stateless
@Remote
public class ShippingService implements ShippingServiceRemote {

// ADD
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ShippingService {
    // Remove "implements ShippingServiceRemote"
    // Keep all method implementations unchanged
```

**Rationale:** Remove EJB remote interface (no longer needed without EJB).

---

### Step 3.8: Delete ShippingServiceRemote interface
**Type:** SIMPLE  
**Files:** src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java  
**Action:** DELETE  
**Detail:**
```bash
rm src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java
```

**Rationale:** Remote interfaces are an EJB concept; not needed in CDI.

---

### Step 3.9: Replace @Stateful with @ApplicationScoped in ShoppingCartService
**Type:** COMPLEX  
**Files:** src/main/java/com/redhat/coolstore/service/ShoppingCartService.java  
**Action:** UPDATE  
**Detail:**
```java
// REMOVE
import javax.ejb.Stateful;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.Hashtable;

@Stateful
public class ShoppingCartService {
    
    private ShoppingCart cart = new ShoppingCart();
    
    private static ShippingServiceRemote lookupShippingServiceRemote() {
        try {
            final Hashtable<String, String> jndiProperties = new Hashtable<>();
            jndiProperties.put(Context.INITIAL_CONTEXT_FACTORY, "org.wildfly.naming.client.WildFlyInitialContextFactory");
            final Context context = new InitialContext(jndiProperties);
            return (ShippingServiceRemote) context.lookup("ejb:/ROOT/ShippingService!" + ShippingServiceRemote.class.getName());
        } catch (NamingException e) {
            throw new RuntimeException(e);
        }
    }

// ADD
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ShoppingCartService {
    
    @Inject
    ShippingService shippingService;
    
    // REMOVE: private ShoppingCart cart instance variable (state moved to CartEndpoint)
    
    // UPDATE: All methods to accept cartId and work with passed cart instances
    // Replace lookupShippingServiceRemote() calls with direct shippingService injection
```

**Rationale:** Stateful session state doesn't belong in services; move to @SessionScoped REST endpoint. Replace JNDI lookup with direct injection.

**Notes:** This is a structural change. See Step 3.10 for CartEndpoint updates.

---

### Step 3.10: Update CartEndpoint to manage ShoppingCart state
**Type:** COMPLEX  
**Files:** src/main/java/com/redhat/coolstore/rest/CartEndpoint.java  
**Action:** UPDATE  
**Detail:**
```java
// Keep existing @SessionScoped annotation
import jakarta.enterprise.context.SessionScoped;
import java.util.HashMap;
import java.util.Map;

@SessionScoped
@Path("/cart")
public class CartEndpoint implements Serializable {
    
    // ADD: Cart storage per session
    private Map<String, ShoppingCart> carts = new HashMap<>();
    
    @Inject
    private ShoppingCartService shoppingCartService;
    
    @GET
    @Path("/{cartId}")
    @Produces(MediaType.APPLICATION_JSON)
    public ShoppingCart getCart(@PathParam("cartId") String cartId) {
        return carts.computeIfAbsent(cartId, id -> new ShoppingCart());
    }
    
    // UPDATE all methods to use local cart storage:
    // ShoppingCart cart = carts.computeIfAbsent(cartId, id -> new ShoppingCart());
    // Pass cart to shoppingCartService methods instead of just cartId
}
```

**Rationale:** Session-scoped endpoint manages cart instances; service becomes stateless.

---

### Step 3.11: Update ShoppingCartService method signatures
**Type:** COMPLEX  
**Files:** src/main/java/com/redhat/coolstore/service/ShoppingCartService.java  
**Action:** UPDATE  
**Detail:**
```java
// BEFORE
public ShoppingCart getShoppingCart(String cartId) {
    return cart;
}

public ShoppingCart checkOutShoppingCart(String cartId) {
    ShoppingCart cart = this.getShoppingCart(cartId);
    // ...
}

// AFTER
public ShoppingCart getShoppingCart(ShoppingCart cart) {
    return cart;
}

public ShoppingCart checkOutShoppingCart(ShoppingCart cart) {
    log.info("Sending order: ");
    shoppingCartOrderProcessor.process(cart);
    cart.resetShoppingCartItemList();
    priceShoppingCart(cart);
    return cart;
}
```

**Rationale:** Service methods receive cart instances instead of looking them up.

---

### Step 3.12: Remove JNDI lookups from ShoppingCartService
**Type:** SIMPLE  
**Files:** src/main/java/com/redhat/coolstore/service/ShoppingCartService.java  
**Action:** UPDATE  
**Detail:**
```java
// REMOVE entire lookupShippingServiceRemote() method

// REPLACE all calls to lookupShippingServiceRemote()
sc.setShippingTotal(lookupShippingServiceRemote().calculateShipping(sc));

// WITH direct injection
sc.setShippingTotal(shippingService.calculateShipping(sc));
```

**Rationale:** Direct CDI injection replaces JNDI lookups.

---

### Step 3.13: Add @ApplicationScoped to CatalogService
**Type:** SIMPLE  
**Files:** src/main/java/com/redhat/coolstore/service/CatalogService.java  
**Action:** UPDATE  
**Detail:**
```java
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class CatalogService {
    
    @Transactional
    public void updateInventoryItems(String itemId, int quantity) {
        // existing implementation
    }
}
```

**Rationale:** Explicit scope annotation; @Transactional for database writes.

---

### Step 3.14: Add @ApplicationScoped to ProductService
**Type:** SIMPLE  
**Files:** src/main/java/com/redhat/coolstore/service/ProductService.java  
**Action:** UPDATE  
**Detail:**
```java
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProductService {
    // No changes to methods needed
}
```

**Rationale:** Explicit CDI scope.

---

### Step 3.15: Add @ApplicationScoped to PromoService
**Type:** SIMPLE  
**Files:** src/main/java/com/redhat/coolstore/service/PromoService.java  
**Action:** UPDATE  
**Detail:**
```java
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PromoService {
    // No changes to methods needed
}
```

**Rationale:** Explicit CDI scope.

---

### Step 3.16: Update Resources.java to remove @Produces EntityManager
**Type:** SIMPLE  
**Files:** src/main/java/com/redhat/coolstore/persistence/Resources.java  
**Action:** DELETE  
**Detail:**
```bash
rm src/main/java/com/redhat/coolstore/persistence/Resources.java
```

**Rationale:** Quarkus auto-injects EntityManager; producer not needed.

**Alternative:** If keeping file, remove @PersistenceContext and @Produces:
```java
// Quarkus injects EntityManager directly - this file can be deleted
```

---

**Phase 3 Build Gate:** Run `mvn clean compile`. Expect javax.jms errors in MDB classes - fixed in Phase 4.

---

## Phase 4: Messaging (8 steps)

**Goal:** Convert MDBs to Reactive Messaging, replace JMS producers.

**Build Command:** `mvn compile`

### Step 4.1: Convert OrderServiceMDB to Reactive Messaging
**Type:** COMPLEX  
**Files:** src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java  
**Action:** UPDATE  
**Detail:**
```java
// REMOVE
import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

@MessageDriven(name = "OrderServiceMDB", activationConfig = {
    @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "topic/orders"),
    @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
    @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge")
})
public class OrderServiceMDB implements MessageListener {
    
    @Override
    public void onMessage(Message rcvMessage) {
        System.out.println("\nMessage recd !");
        TextMessage msg = null;
        try {
            if (rcvMessage instanceof TextMessage) {
                msg = (TextMessage) rcvMessage;
                String orderStr = msg.getBody(String.class);
                System.out.println("Received order: " + orderStr);
                Order order = Transformers.jsonToOrder(orderStr);
                System.out.println("Order object is " + order);
                orderService.save(order);
                order.getItemList().forEach(orderItem -> {
                    catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity());
                });
            }
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }
}

// ADD
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class OrderServiceMDB {
    
    @Inject
    OrderService orderService;

    @Inject
    CatalogService catalogService;
    
    @Incoming("orders-service")
    public void onMessage(String orderStr) {
        System.out.println("\nMessage received!");
        System.out.println("Received order: " + orderStr);
        Order order = Transformers.jsonToOrder(orderStr);
        System.out.println("Order object is " + order);
        orderService.save(order);
        order.getItemList().forEach(orderItem -> {
            catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity());
        });
    }
}
```

**Rationale:** @Incoming replaces MDB @MessageDriven; method receives deserialized payload directly.

**Notes:** Channel name "orders-service" matches application.properties config.

---

### Step 4.2: Convert InventoryNotificationMDB to Reactive Messaging
**Type:** COMPLEX  
**Files:** src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java  
**Action:** UPDATE  
**Detail:**
```java
// REMOVE all JMS and JNDI imports
import javax.inject.Inject;
import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.rmi.PortableRemoteObject;
import java.util.Hashtable;

public class InventoryNotificationMDB implements MessageListener {
    
    private final static String JNDI_FACTORY = "weblogic.jndi.WLInitialContextFactory";
    private final static String JMS_FACTORY = "TCF";
    private final static String TOPIC = "topic/orders";
    private TopicConnection tcon;
    private TopicSession tsession;
    private TopicSubscriber tsubscriber;
    
    public void onMessage(Message rcvMessage) {
        TextMessage msg;
        {
            try {
                System.out.println("received message inventory");
                if (rcvMessage instanceof TextMessage) {
                    msg = (TextMessage) rcvMessage;
                    String orderStr = msg.getBody(String.class);
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
                }
            } catch (JMSException jmse) {
                System.err.println("An exception occurred: " + jmse.getMessage());
            }
        }
    }
    
    public void init() throws NamingException, JMSException { ... }
    public void close() throws JMSException { ... }
    private static InitialContext getInitialContext() throws NamingException { ... }
}

// ADD
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class InventoryNotificationMDB {

    private static final int LOW_THRESHOLD = 50;

    @Inject
    private CatalogService catalogService;

    @Incoming("inventory-notification")
    public void onMessage(String orderStr) {
        System.out.println("Received message inventory");
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
    }
}
```

**Rationale:** Remove all JNDI and JMS boilerplate; @Incoming handles message consumption.

**Notes:** 
- Removed init(), close(), getInitialContext() methods - not needed
- Channel name "inventory-notification" matches application.properties

---

### Step 4.3: Update ShoppingCartOrderProcessor (already done in Step 3.6)
**Type:** REFERENCE  
**Files:** src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java  
**Action:** VERIFY  
**Detail:**
Verify that Step 3.6 was applied correctly:
```java
@ApplicationScoped
public class ShoppingCartOrderProcessor {
    
    @Inject
    @Channel("orders")
    Emitter<String> ordersEmitter;
    
    public void process(ShoppingCart cart) {
        log.info("Sending order from processor: ");
        ordersEmitter.send(Transformers.shoppingCartToJson(cart));
    }
}
```

**Rationale:** Emitter replaces JMS producer.

---

### Step 4.4: Verify channel configuration in application.properties
**Type:** SIMPLE  
**Files:** src/main/resources/application.properties  
**Action:** VERIFY  
**Detail:**
Ensure these lines exist (added in Step 2.1):
```properties
mp.messaging.outgoing.orders.connector=smallrye-in-memory
mp.messaging.incoming.orders-service.connector=smallrye-in-memory
mp.messaging.incoming.orders-service.broadcast=true
mp.messaging.incoming.inventory-notification.connector=smallrye-in-memory
mp.messaging.incoming.inventory-notification.broadcast=true
```

**Rationale:** In-memory connector bridges outgoing "orders" to incoming "orders-service" and "inventory-notification".

**Notes:** broadcast=true ensures both consumers receive each message (pub/sub pattern).

---

### Step 4.5: Remove javax.annotation.Resource imports
**Type:** SIMPLE  
**Files:** ShoppingCartOrderProcessor.java (if not already removed)  
**Action:** UPDATE  
**Detail:**
```bash
find src/main/java -name "*.java" -exec sed -i '/import javax\.annotation\.Resource;/d' {} \;
```

**Rationale:** @Resource not used in Quarkus messaging.

---

### Step 4.6: Update Transformers to handle JSON conversion
**Type:** SIMPLE  
**Files:** src/main/java/com/redhat/coolstore/utils/Transformers.java  
**Action:** VERIFY  
**Detail:**
Verify existing methods are compatible:
```java
public class Transformers {
    public static String shoppingCartToJson(ShoppingCart cart) { ... }
    public static Order jsonToOrder(String json) { ... }
}
```

**Rationale:** These utility methods should work unchanged with Reactive Messaging.

**Notes:** If implementation uses Java EE JSON-B, may need to update to Jakarta JSON-B imports.

---

### Step 4.7: Add javax.json to jakarta.json imports if present
**Type:** SIMPLE  
**Files:** Transformers.java (if using JSON-B)  
**Action:** UPDATE  
**Detail:**
```bash
find src/main/java -name "*.java" -exec sed -i 's/javax\.json/jakarta.json/g' {} \;
```

**Rationale:** JSON-B moved to jakarta.json namespace.

---

### Step 4.8: Rename MDB classes (optional)
**Type:** SIMPLE  
**Files:** OrderServiceMDB.java, InventoryNotificationMDB.java  
**Action:** OPTIONAL  
**Detail:**
Consider renaming for clarity:
- OrderServiceMDB.java → OrderMessageConsumer.java
- InventoryNotificationMDB.java → InventoryNotificationConsumer.java

**Rationale:** "MDB" suffix is misleading since they're no longer Message-Driven Beans.

**Notes:** This is optional and requires updating references in other files.

---

**Phase 4 Build Gate:** Run `mvn clean compile`. Should succeed with no javax.* import errors.

---

## Phase 5: Lifecycle (5 steps)

**Goal:** Replace WebLogic lifecycle listeners with Quarkus events.

**Build Command:** `mvn compile`

### Step 5.1: Delete WebLogic ApplicationLifecycleListener stub
**Type:** SIMPLE  
**Files:** src/main/java/weblogic/application/ApplicationLifecycleListener.java  
**Action:** DELETE  
**Detail:**
```bash
rm src/main/java/weblogic/application/ApplicationLifecycleListener.java
```

**Rationale:** WebLogic-specific API not needed in Quarkus.

---

### Step 5.2: Delete WebLogic ApplicationLifecycleEvent stub
**Type:** SIMPLE  
**Files:** src/main/java/weblogic/application/ApplicationLifecycleEvent.java  
**Action:** DELETE  
**Detail:**
```bash
rm src/main/java/weblogic/application/ApplicationLifecycleEvent.java
```

**Rationale:** WebLogic-specific API not needed in Quarkus.

---

### Step 5.3: Delete weblogic.i18n.logging package
**Type:** SIMPLE  
**Files:** src/main/java/weblogic/i18n/logging/NonCatalogLogger.java  
**Action:** DELETE  
**Detail:**
```bash
rm -rf src/main/java/weblogic
```

**Rationale:** Remove all WebLogic stub classes.

---

### Step 5.4: Convert StartupListener to Quarkus lifecycle events
**Type:** SIMPLE  
**Files:** src/main/java/com/redhat/coolstore/utils/StartupListener.java  
**Action:** UPDATE  
**Detail:**
```java
// REMOVE
package com.redhat.coolstore.utils;

import weblogic.application.ApplicationLifecycleEvent;
import weblogic.application.ApplicationLifecycleListener;

import javax.inject.Inject;
import java.util.logging.Logger;

public class StartupListener extends ApplicationLifecycleListener {

    @Inject
    Logger log;

    @Override
    public void postStart(ApplicationLifecycleEvent evt) {
        log.info("AppListener(postStart)");
    }

    @Override
    public void preStop(ApplicationLifecycleEvent evt) {
        log.info("AppListener(preStop)");
    }
}

// ADD
package com.redhat.coolstore.utils;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.logging.Logger;

@ApplicationScoped
public class StartupListener {

    @Inject
    Logger log;

    void onStart(@Observes StartupEvent ev) {
        log.info("AppListener(postStart)");
    }

    void onStop(@Observes ShutdownEvent ev) {
        log.info("AppListener(preStop)");
    }
}
```

**Rationale:** Quarkus uses CDI observer pattern for lifecycle events.

---

### Step 5.5: Verify DataBaseMigrationStartup compatibility
**Type:** SIMPLE  
**Files:** src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java  
**Action:** VERIFY  
**Detail:**
Check if this class uses WebLogic-specific APIs. If it only uses Flyway, verify:
1. Imports are compatible with Quarkus Flyway extension
2. No javax.* imports remain

**Rationale:** Flyway integration is handled by Quarkus extension; custom startup may not be needed.

**Notes:** If class is redundant (Quarkus Flyway runs automatically), consider deleting it.

---

**Phase 5 Build Gate:** Run `mvn clean compile`. Should succeed with no compilation errors.

---

## Phase 6: Cleanup (7 steps)

**Goal:** Remove legacy artifacts, verify migration completeness, final build.

**Build Command:** `mvn clean package`

### Step 6.1: Verify no javax.* EE imports remain
**Type:** VERIFICATION  
**Files:** All Java files  
**Action:** VERIFY  
**Detail:**
```bash
# Should return no results
grep -r "import javax\." src/main/java --include="*.java" | grep -v "javax.annotation.processing" | grep -v "javax.lang.model"
```

**Rationale:** Ensure complete migration to Jakarta namespace.

**Notes:** javax.annotation.processing and javax.lang.model are JDK packages (not Java EE), so they're allowed.

---

### Step 6.2: Verify no EJB annotations remain
**Type:** VERIFICATION  
**Files:** All Java files  
**Action:** VERIFY  
**Detail:**
```bash
# Should return no results
grep -r "@Stateless\|@Stateful\|@MessageDriven\|@Remote\|@Local" src/main/java --include="*.java"
```

**Rationale:** Ensure all EJB code converted to CDI.

---

### Step 6.3: Verify no JNDI lookups remain
**Type:** VERIFICATION  
**Files:** All Java files  
**Action:** VERIFY  
**Detail:**
```bash
# Should return no results
grep -r "InitialContext\|lookup(" src/main/java --include="*.java"
```

**Rationale:** Ensure all JNDI replaced with direct injection.

---

### Step 6.4: Verify no JMS API usage remains
**Type:** VERIFICATION  
**Files:** All Java files  
**Action:** VERIFY  
**Detail:**
```bash
# Should return no results
grep -r "import javax\.jms\|import jakarta\.jms" src/main/java --include="*.java"
```

**Rationale:** Ensure all JMS replaced with Reactive Messaging.

---

### Step 6.5: Delete DataBaseMigrationStartup if redundant
**Type:** OPTIONAL  
**Files:** src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java  
**Action:** DELETE (if applicable)  
**Detail:**
```bash
# Only if Step 5.5 determined it's redundant
rm src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
```

**Rationale:** Quarkus Flyway extension handles migrations automatically.

---

### Step 6.6: Run full build with tests
**Type:** VERIFICATION  
**Files:** N/A  
**Action:** BUILD  
**Detail:**
```bash
mvn clean package
```

**Expected Output:**
- BUILD SUCCESS
- JAR file in target/: `monolith-1.0.0-SNAPSHOT-runner.jar`

**Rationale:** Verify complete application compiles and packages.

---

### Step 6.7: Test application startup
**Type:** VERIFICATION  
**Files:** N/A  
**Action:** RUN  
**Detail:**
```bash
# Start in dev mode
mvn quarkus:dev

# Verify output includes:
# - Quarkus version info
# - "AppListener(postStart)" from StartupListener
# - "Listening on: http://localhost:8080"

# Test REST endpoint:
curl http://localhost:8080/services/cart/123

# Expected: JSON response with empty shopping cart
```

**Rationale:** Verify application runs successfully in Quarkus runtime.

---

**Phase 6 Complete:** Migration finished. Application is now a Quarkus 3 application.

---

## Post-Migration Validation

### Functional Testing Checklist

- [ ] **Database connectivity**: Verify datasource connection and JPA queries work
- [ ] **REST endpoints**: Test all CartEndpoint, OrderEndpoint, ProductEndpoint operations
- [ ] **Messaging**: Verify order messages are sent and received by both consumers
- [ ] **Shopping cart state**: Test cart persistence across requests in same session
- [ ] **Lifecycle events**: Verify startup/shutdown log messages appear
- [ ] **Flyway migrations**: Check database schema created correctly

### Runtime Verification

```bash
# Start application
mvn quarkus:dev

# Check dev UI (optional)
# Open: http://localhost:8080/q/dev

# View datasources, messaging channels, CDI beans
```

### Build Verification

```bash
# Native build (optional - requires GraalVM)
mvn package -Pnative

# Container build (optional)
docker build -f src/main/docker/Dockerfile.jvm -t coolstore:quarkus .
docker run -p 8080:8080 coolstore:quarkus
```

---

## Rollback Plan

If migration fails at any phase:

1. **Revert to last successful phase**: Git checkout to previous build gate commit
2. **Review errors**: Check compilation errors, runtime exceptions
3. **Consult domain skill**: Review /opt/skills/javaee-to-quarkus references
4. **Report blockers**: Document specific failure for resolution

---

## Next Steps After Migration

### Optional Enhancements

1. **Switch to Reactive Messaging with Kafka**
   - Add `quarkus-smallrye-reactive-messaging-kafka`
   - Update application.properties to use Kafka broker
   
2. **Add Panache for simplified JPA**
   - Extend entities from `PanacheEntity`
   - Use Panache query methods
   
3. **Enable Health Checks**
   - Add `quarkus-smallrye-health`
   - Implement custom health checks
   
4. **Add Metrics**
   - Add `quarkus-micrometer-registry-prometheus`
   - Expose /q/metrics endpoint
   
5. **OpenAPI Documentation**
   - Add `quarkus-smallrye-openapi`
   - Access API docs at /q/swagger-ui

### Production Readiness

- Configure production datasource
- Externalize configuration
- Set up container registry
- Configure Kubernetes deployment manifests
- Implement distributed tracing
- Set up centralized logging

---

**Implementation plan complete. Proceed with Phase 1.**
