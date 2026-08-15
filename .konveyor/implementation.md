# Implementation Plan: Java EE 7 to Quarkus 3 Migration

## Overview

This document provides detailed, step-by-step instructions for migrating the Coolstore Monolith application from Java EE 7 (WebLogic/JBoss) to Quarkus 3. Execute phases sequentially and run the build after each phase to catch issues early.

**Build Command:** `mvn clean compile`  
**Total Steps:** 38  
**Estimated Duration:** 6-7 hours

---

## Phase 1: Build Configuration

**Goal:** Transform Maven POM from Java EE WAR to Quarkus JAR project.

**Files Modified:** 1 (pom.xml)

### Step 1.1: Update Project Packaging
**Type:** SIMPLE  
**File:** `pom.xml`  
**Action:** Change packaging from WAR to JAR

**Details:**
- Find: `<packaging>war</packaging>`
- Replace: `<packaging>jar</packaging>`
- Reason: Quarkus applications are packaged as executable JARs, not WARs

### Step 1.2: Add Java Version Properties
**Type:** SIMPLE  
**File:** `pom.xml`  
**Action:** Update Java version to 17 (Quarkus 3 minimum requirement)

**Details:**
- Add to `<properties>` section:
  ```xml
  <java.version>17</java.version>
  <quarkus.platform.version>3.8.1</quarkus.platform.version>
  ```
- Update compiler plugin configuration:
  ```xml
  <source>17</source>
  <target>17</target>
  ```
- Reason: Quarkus 3 requires Java 17+; 3.8.1 is a stable LTS version

### Step 1.3: Add Quarkus BOM
**Type:** SIMPLE  
**File:** `pom.xml`  
**Action:** Add Quarkus Bill of Materials for dependency management

**Details:**
- Add to `<dependencyManagement>` section (create if not exists):
  ```xml
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>io.quarkus.platform</groupId>
        <artifactId>quarkus-bom</artifactId>
        <version>${quarkus.platform.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
  ```
- Reason: Centralizes Quarkus dependency versions for compatibility

### Step 1.4: Replace Java EE Dependencies with Quarkus Extensions
**Type:** COMPLEX  
**File:** `pom.xml`  
**Action:** Remove Java EE APIs and add Quarkus extensions

**Details:**
Remove these dependencies:
```xml
<!-- REMOVE -->
<dependency>
  <groupId>javax</groupId>
  <artifactId>javaee-web-api</artifactId>
</dependency>
<dependency>
  <groupId>javax</groupId>
  <artifactId>javaee-api</artifactId>
</dependency>
<dependency>
  <groupId>org.jboss.spec.javax.jms</groupId>
  <artifactId>jboss-jms-api_2.0_spec</artifactId>
</dependency>
<dependency>
  <groupId>org.jboss.spec.javax.rmi</groupId>
  <artifactId>jboss-rmi-api_1.0_spec</artifactId>
</dependency>
```

Add these Quarkus extensions:
```xml
<!-- Quarkus Core -->
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-arc</artifactId>
</dependency>

<!-- REST (JAX-RS) -->
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-resteasy-reactive-jackson</artifactId>
</dependency>

<!-- Persistence (JPA + Hibernate) -->
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-hibernate-orm</artifactId>
</dependency>

<!-- Database (adjust to your DB: h2, postgresql, mysql) -->
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-jdbc-h2</artifactId>
</dependency>

<!-- Reactive Messaging (for MDB replacement) -->
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-smallrye-reactive-messaging</artifactId>
</dependency>
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-smallrye-reactive-messaging-kafka</artifactId>
</dependency>

<!-- Keep Flyway -->
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-flyway</artifactId>
</dependency>
```

**Mapping Reference:**
- `javaee-api` → `quarkus-arc` (CDI), `quarkus-resteasy-reactive` (JAX-RS)
- `javaee-web-api` → (included in Quarkus extensions)
- `jboss-jms-api` → `quarkus-smallrye-reactive-messaging`
- EJB support → (removed, use CDI)
- `flyway-core` → `quarkus-flyway` (managed version)

### Step 1.5: Add Quarkus Maven Plugin
**Type:** SIMPLE  
**File:** `pom.xml`  
**Action:** Add Quarkus Maven plugin for building and dev mode

**Details:**
- Remove `maven-war-plugin` from `<plugins>` section
- Add Quarkus plugin:
  ```xml
  <plugin>
    <groupId>io.quarkus</groupId>
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
- Reason: Enables `mvn quarkus:dev`, `mvn package` for Quarkus apps

### Step 1.6: Build Validation
**Type:** VALIDATION  
**Action:** Verify POM changes compile

**Command:**
```bash
mvn clean compile
```

**Expected Outcome:**
- Build may fail due to missing classes (EJB annotations, etc.) - this is expected
- Goal: Ensure Maven can resolve Quarkus dependencies
- Look for: "Downloading from central: io.quarkus..." messages
- Acceptable errors at this stage: Java compilation failures (will fix in later phases)

---

## Phase 2: Application Configuration

**Goal:** Migrate XML configuration to Quarkus application.properties and remove unnecessary files.

**Files Created:** 1  
**Files Deleted:** 2

### Step 2.1: Create application.properties
**Type:** SIMPLE  
**File:** `src/main/resources/application.properties` (create new)  
**Action:** Create Quarkus configuration file

**Details:**
Create file with initial configuration:
```properties
# Application
quarkus.application.name=coolstore-monolith
quarkus.http.port=8080

# Datasource (migrate from persistence.xml)
quarkus.datasource.db-kind=h2
quarkus.datasource.username=sa
quarkus.datasource.password=
quarkus.datasource.jdbc.url=jdbc:h2:mem:coolstore;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE

# Hibernate ORM (migrate from persistence.xml)
quarkus.hibernate-orm.database.generation=none
quarkus.hibernate-orm.log.sql=false
quarkus.hibernate-orm.log.format-sql=true
quarkus.hibernate-orm.jdbc.statement-batch-size=0

# Flyway (for database migrations)
quarkus.flyway.migrate-at-start=true
quarkus.flyway.locations=db/migration

# Reactive Messaging (for MDB replacement - in-memory for dev)
mp.messaging.incoming.orders.connector=smallrye-in-memory

# Dev mode settings
%dev.quarkus.hibernate-orm.log.sql=true
```

**Configuration Mapping from persistence.xml:**
- `<jta-data-source>java:jboss/datasources/CoolstoreDS</jta-data-source>` → `quarkus.datasource.*` properties
- `javax.persistence.schema-generation.database.action=none` → `quarkus.hibernate-orm.database.generation=none`
- `hibernate.show_sql=false` → `quarkus.hibernate-orm.log.sql=false`
- `hibernate.format_sql=true` → `quarkus.hibernate-orm.log.format-sql=true`
- `hibernate.jdbc.use_get_generated_keys=false` → `quarkus.hibernate-orm.jdbc.statement-batch-size=0`

**Note:** Adjust `quarkus.datasource.db-kind` and JDBC URL for production database (postgresql, mysql, etc.)

### Step 2.2: Delete persistence.xml
**Type:** SIMPLE  
**File:** `src/main/resources/META-INF/persistence.xml`  
**Action:** Delete file (configuration moved to application.properties)

**Command:**
```bash
rm src/main/resources/META-INF/persistence.xml
```

**Reason:** Quarkus uses application.properties for datasource and Hibernate configuration; persistence.xml is not needed

### Step 2.3: Delete web.xml
**Type:** SIMPLE  
**File:** `src/main/webapp/WEB-INF/web.xml`  
**Action:** Delete file (not needed for JAR packaging)

**Command:**
```bash
rm src/main/webapp/WEB-INF/web.xml
```

**Reason:** Quarkus does not use web.xml; JAX-RS applications are auto-configured

### Step 2.4: Handle beans.xml
**Type:** SIMPLE  
**File:** `src/main/webapp/WEB-INF/beans.xml`  
**Action:** Move to META-INF or delete

**Details:**
- Option 1 (Recommended): Delete beans.xml entirely
  ```bash
  rm src/main/webapp/WEB-INF/beans.xml
  ```
  Quarkus Arc (CDI implementation) uses annotation-based discovery by default

- Option 2: Move to `src/main/resources/META-INF/beans.xml` if you need explicit CDI configuration
  ```bash
  mkdir -p src/main/resources/META-INF
  mv src/main/webapp/WEB-INF/beans.xml src/main/resources/META-INF/
  ```

**Recommendation:** Delete beans.xml unless you have specific CDI discovery requirements

### Step 2.5: Verify Resource Files
**Type:** SIMPLE  
**Action:** Ensure database migration files are in correct location

**Command:**
```bash
ls -la src/main/resources/db/
```

**Expected:** Flyway migration scripts should be in `src/main/resources/db/migration/`  
**Action Required:** If migrations are elsewhere, move them to this location

### Step 2.6: Build Validation
**Type:** VALIDATION  
**Action:** Verify configuration changes

**Command:**
```bash
mvn clean compile
```

**Expected Outcome:**
- Configuration files loaded successfully
- Still may have Java compilation errors (EJB annotations) - will fix next
- Check logs for "Quarkus" startup messages

---

## Phase 3: EJB to CDI Conversion

**Goal:** Replace EJB programming model with CDI beans, remove JNDI lookups.

**Files Modified:** 6  
**Files Deleted:** 1

### Step 3.1: Convert CatalogService (@Stateless → @ApplicationScoped)
**Type:** SIMPLE  
**File:** `src/main/java/com/redhat/coolstore/service/CatalogService.java`  
**Action:** Replace EJB annotations with CDI

**Find:**
```java
import javax.ejb.Stateless;

@Stateless
public class CatalogService {
```

**Replace:**
```java
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CatalogService {
```

**Details:**
- Remove: `import javax.ejb.Stateless;`
- Add: `import jakarta.enterprise.context.ApplicationScoped;`
- Replace: `@Stateless` → `@ApplicationScoped`
- Reason: Stateless EJBs map naturally to @ApplicationScoped CDI beans (singleton-like, thread-safe)

**Note:** Also update other imports from `javax.*` to `jakarta.*`:
- `javax.inject.Inject` → `jakarta.inject.Inject`
- `javax.persistence.*` → `jakarta.persistence.*`

### Step 3.2: Convert OrderService (@Stateless → @ApplicationScoped)
**Type:** SIMPLE  
**File:** `src/main/java/com/redhat/coolstore/service/OrderService.java`  
**Action:** Replace EJB annotations with CDI

**Apply same transformation as Step 3.1:**
- Remove: `import javax.ejb.Stateless;`
- Add: `import jakarta.enterprise.context.ApplicationScoped;`
- Replace: `@Stateless` → `@ApplicationScoped`
- Update `javax.*` → `jakarta.*` imports

### Step 3.3: Convert ShippingService (@Stateless @Remote → @ApplicationScoped)
**Type:** MEDIUM  
**File:** `src/main/java/com/redhat/coolstore/service/ShippingService.java`  
**Action:** Remove Remote interface and convert to CDI bean

**Find:**
```java
import javax.ejb.Remote;
import javax.ejb.Stateless;

@Stateless
@Remote
public class ShippingService implements ShippingServiceRemote {
```

**Replace:**
```java
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ShippingService implements ShippingServiceRemote {
```

**Details:**
- Remove: `@Stateless`, `@Remote` annotations
- Add: `@ApplicationScoped`
- Keep: `implements ShippingServiceRemote` (will extract interface methods later)
- Update `javax.*` → `jakarta.*` imports

**Note:** ShippingServiceRemote will be deleted in Step 3.5 after removing JNDI lookups

### Step 3.4: Convert ShoppingCartService (@Stateful → @RequestScoped)
**Type:** COMPLEX  
**File:** `src/main/java/com/redhat/coolstore/service/ShoppingCartService.java`  
**Action:** Replace Stateful EJB with appropriate CDI scope

**Find:**
```java
import javax.ejb.Stateful;

@Stateful
public class ShoppingCartService {
    private ShoppingCart cart = new ShoppingCart();
```

**Replace Option 1 (Stateless - Recommended):**
```java
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ShoppingCartService {
    // Remove instance variable, make methods stateless
```

**Replace Option 2 (Keep State - if session required):**
```java
import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class ShoppingCartService {
    private ShoppingCart cart = new ShoppingCart();
```

**Details:**
- **Stateful EJB** maintains conversational state across method calls
- **Recommended approach:** Make service stateless, manage cart state in CartEndpoint or database
- **Alternative:** Use `@RequestScoped` for request-level state
- **Not recommended:** `@SessionScoped` (HTTP session-based, requires careful testing)

**Code Changes for Stateless Approach:**
1. Remove `private ShoppingCart cart = new ShoppingCart();` instance variable
2. Modify methods to accept `ShoppingCart` as parameter or retrieve from persistence
3. Update callers (CartEndpoint) to manage cart lifecycle

**Code Changes for RequestScoped Approach:**
1. Keep instance variable
2. Change `@Stateful` → `@RequestScoped`
3. Test thoroughly - request scope differs from conversation scope

**Decision Required:** Review application requirements with stakeholders

### Step 3.5: Remove JNDI Lookup in ShoppingCartService
**Type:** COMPLEX  
**File:** `src/main/java/com/redhat/coolstore/service/ShoppingCartService.java`  
**Action:** Replace JNDI lookup with CDI injection

**Find:**
```java
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
```

And method calls:
```java
sc.setShippingTotal(lookupShippingServiceRemote().calculateShipping(sc));
```

**Replace:**
Add injection at class level:
```java
@Inject
ShippingService shippingService;
```

Replace method calls:
```java
sc.setShippingTotal(shippingService.calculateShipping(sc));
sc.setShippingTotal(sc.getShippingTotal() + shippingService.calculateShippingInsurance(sc));
```

Remove entire `lookupShippingServiceRemote()` method and related imports:
```java
// REMOVE
import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
```

**Reason:** Direct injection replaces remote EJB lookup; services are now in-process CDI beans

### Step 3.6: Delete ShippingServiceRemote Interface
**Type:** SIMPLE  
**File:** `src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java`  
**Action:** Delete remote interface

**Command:**
```bash
rm src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java
```

**Also update ShippingService.java:**
```java
// Before
public class ShippingService implements ShippingServiceRemote {

// After
public class ShippingService {
```

Move interface methods directly to class (already present in ShippingService):
- `calculateShipping(ShoppingCart sc)`
- `calculateShippingInsurance(ShoppingCart sc)`

**Reason:** No remote interface needed for local CDI beans

### Step 3.7: Update Persistence Resource Producer
**Type:** SIMPLE  
**File:** `src/main/java/com/redhat/coolstore/persistence/Resources.java`  
**Action:** Update imports to Jakarta namespace

**Find:**
```java
import javax.enterprise.inject.Produces;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
```

**Replace:**
```java
import jakarta.enterprise.inject.Produces;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
```

**Note:** Verify `@PersistenceContext` still works in Quarkus (it should, but `@Inject` is also valid)

### Step 3.8: Update All Model Entities
**Type:** SIMPLE (repeated 8 times)  
**Files:** All files in `src/main/java/com/redhat/coolstore/model/`  
**Action:** Update imports from javax to jakarta

**Affected Files:**
- CatalogItemEntity.java
- InventoryEntity.java
- Order.java
- OrderItem.java
- Product.java
- Promotion.java
- ShoppingCart.java
- ShoppingCartItem.java

**Find (in each file):**
```java
import javax.persistence.*;
```

**Replace:**
```java
import jakarta.persistence.*;
```

**Details:**
- This is a bulk find/replace across all model files
- No logic changes needed
- JPA annotations remain the same (@Entity, @Table, @Id, etc.)

### Step 3.9: Update REST Endpoints
**Type:** SIMPLE  
**Files:** `rest/CartEndpoint.java`, `rest/OrderEndpoint.java`, `rest/ProductEndpoint.java`, `rest/RestApplication.java`  
**Action:** Update JAX-RS imports to jakarta namespace

**Find (in each file):**
```java
import javax.ws.rs.*;
import javax.enterprise.context.*;
import javax.inject.Inject;
```

**Replace:**
```java
import jakarta.ws.rs.*;
import jakarta.enterprise.context.*;
import jakarta.inject.Inject;
```

**Special attention to CartEndpoint.java:**
```java
import java.io.Serializable;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@SessionScoped
@Path("/cart")
public class CartEndpoint implements Serializable {
```

**Note:** Review `@SessionScoped` usage in Quarkus - may need testing/adjustment

### Step 3.10: Build Validation
**Type:** VALIDATION  
**Action:** Verify EJB to CDI conversion compiles

**Command:**
```bash
mvn clean compile
```

**Expected Outcome:**
- All EJB references resolved
- No `javax.ejb.*` imports
- May still have MDB compilation errors (will fix in Phase 4)
- Look for successful compilation of model, service, rest packages

---

## Phase 4: Messaging Migration

**Goal:** Convert JMS Message-Driven Beans to SmallRye Reactive Messaging.

**Files Modified:** 2

### Step 4.1: Convert OrderServiceMDB to Reactive Messaging
**Type:** COMPLEX  
**File:** `src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java`  
**Action:** Replace @MessageDriven with @Incoming reactive method

**Find:**
```java
package com.redhat.coolstore.service;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.inject.Inject;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

@MessageDriven(name = "OrderServiceMDB", activationConfig = {
	@ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "topic/orders"),
	@ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
	@ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge")})
public class OrderServiceMDB implements MessageListener { 

	@Inject
	OrderService orderService;

	@Inject
	CatalogService catalogService;

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
```

**Replace:**
```java
package com.redhat.coolstore.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Incoming;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

@ApplicationScoped
public class OrderServiceMDB {

	@Inject
	OrderService orderService;

	@Inject
	CatalogService catalogService;

	@Incoming("orders")
	public void processOrder(String orderStr) {
		System.out.println("\nMessage received: " + orderStr);
		try {
			Order order = Transformers.jsonToOrder(orderStr);
			System.out.println("Order object is " + order);
			orderService.save(order);
			order.getItemList().forEach(orderItem -> {
				catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity());
			});
		} catch (Exception e) {
			System.err.println("Error processing order: " + e.getMessage());
			throw new RuntimeException(e);
		}
	}
}
```

**Key Changes:**
1. **Removed:**
   - `@MessageDriven` and all activation config properties
   - `implements MessageListener`
   - `onMessage(Message rcvMessage)` method signature
   - JMS-specific imports (`javax.jms.*`, `javax.ejb.*`)

2. **Added:**
   - `@ApplicationScoped` (standard CDI bean)
   - `@Incoming("orders")` annotation (Reactive Messaging channel)
   - Simplified method: `processOrder(String orderStr)` - framework handles deserialization

3. **Configuration (in application.properties - already added in Step 2.1):**
   ```properties
   mp.messaging.incoming.orders.connector=smallrye-in-memory
   ```

**For Production (Kafka):**
```properties
mp.messaging.incoming.orders.connector=smallrye-kafka
mp.messaging.incoming.orders.topic=orders
mp.messaging.incoming.orders.value.deserializer=org.apache.kafka.common.serialization.StringDeserializer
```

### Step 4.2: Convert InventoryNotificationMDB to Reactive Messaging
**Type:** COMPLEX  
**File:** `src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java`  
**Action:** Replace manual JMS listener with @Incoming reactive method

**Find:**
```java
package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

import javax.inject.Inject;
import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.rmi.PortableRemoteObject;
import java.util.Hashtable;

public class InventoryNotificationMDB implements MessageListener {

    private static final int LOW_THRESHOLD = 50;

    @Inject
    private CatalogService catalogService;

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

    public void init() throws NamingException, JMSException {
        Context ctx = getInitialContext();
        TopicConnectionFactory tconFactory = (TopicConnectionFactory) PortableRemoteObject.narrow(ctx.lookup(JMS_FACTORY), TopicConnectionFactory.class);
        tcon = tconFactory.createTopicConnection();
        tsession = tcon.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic topic = (Topic) PortableRemoteObject.narrow(ctx.lookup(TOPIC), Topic.class);
        tsubscriber = tsession.createSubscriber(topic);
        tsubscriber.setMessageListener(this);
        tcon.start();
    }

    public void close() throws JMSException {
        tsubscriber.close();
        tsession.close();
        tcon.close();
    }

    private static InitialContext getInitialContext() throws NamingException {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, JNDI_FACTORY);
        env.put(Context.PROVIDER_URL, "t3://localhost:7001");
        env.put("weblogic.jndi.createIntermediateContexts", "true");
        return new InitialContext(env);
    }
}
```

**Replace:**
```java
package com.redhat.coolstore.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Incoming;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

@ApplicationScoped
public class InventoryNotificationMDB {

    private static final int LOW_THRESHOLD = 50;

    @Inject
    private CatalogService catalogService;

    @Incoming("orders")
    public void checkInventory(String orderStr) {
        try {
            System.out.println("Received message - checking inventory");
            Order order = Transformers.jsonToOrder(orderStr);
            
            order.getItemList().forEach(orderItem -> {
                int old_quantity = catalogService.getCatalogItemById(orderItem.getProductId())
                    .getInventory()
                    .getQuantity();
                int new_quantity = old_quantity - orderItem.getQuantity();
                
                if (new_quantity < LOW_THRESHOLD) {
                    System.out.println("Inventory for item " + orderItem.getProductId() 
                        + " is below threshold (" + LOW_THRESHOLD + "), contact supplier!");
                } else {
                    orderItem.setQuantity(new_quantity);
                }
            });
        } catch (Exception e) {
            System.err.println("Error checking inventory: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
```

**Key Changes:**
1. **Removed:**
   - All JNDI-related code (`init()`, `close()`, `getInitialContext()`)
   - Manual JMS connection management (TopicConnection, TopicSession, etc.)
   - `implements MessageListener`
   - WebLogic-specific JNDI properties

2. **Added:**
   - `@ApplicationScoped` (CDI bean)
   - `@Incoming("orders")` (same channel as OrderServiceMDB - both consume from same topic)
   - Simplified method signature: `checkInventory(String orderStr)`

3. **Reactive Messaging handles:**
   - Connection lifecycle
   - Message acknowledgment
   - Error handling and retries
   - Deserialization

**Note:** Both MDBs listen to the same "orders" channel - this is correct for pub/sub (topic) semantics

### Step 4.3: Update Utilities Package
**Type:** SIMPLE  
**Files:** `utils/Transformers.java`, `utils/Producers.java`, `utils/DataBaseMigrationStartup.java`  
**Action:** Update imports to jakarta namespace

**For each file, replace:**
```java
import javax.enterprise.inject.*;
import javax.inject.*;
```

**With:**
```java
import jakarta.enterprise.inject.*;
import jakarta.inject.*;
```

### Step 4.4: Configure Messaging Channels
**Type:** SIMPLE  
**File:** `src/main/resources/application.properties`  
**Action:** Verify messaging configuration (already added in Step 2.1)

**Verify these lines exist:**
```properties
# In-memory messaging for development
mp.messaging.incoming.orders.connector=smallrye-in-memory

# For production (Kafka) - comment out in-memory and use these:
# mp.messaging.incoming.orders.connector=smallrye-kafka
# mp.messaging.incoming.orders.topic=orders
# mp.messaging.incoming.orders.value.deserializer=org.apache.kafka.common.serialization.StringDeserializer
# kafka.bootstrap.servers=localhost:9092
```

**Note:** In-memory connector allows testing without external messaging infrastructure

### Step 4.5: Build Validation
**Type:** VALIDATION  
**Action:** Verify messaging migration compiles

**Command:**
```bash
mvn clean compile
```

**Expected Outcome:**
- No JMS imports remaining
- No `@MessageDriven` annotations
- Reactive Messaging dependencies resolved
- All services compile successfully

---

## Phase 5: Lifecycle Migration

**Goal:** Replace WebLogic lifecycle hooks with Quarkus startup/shutdown events.

**Files Modified:** 1  
**Files Deleted:** 2

### Step 5.1: Rewrite StartupListener with Quarkus Events
**Type:** MEDIUM  
**File:** `src/main/java/com/redhat/coolstore/utils/StartupListener.java`  
**Action:** Replace WebLogic ApplicationLifecycleListener with Quarkus events

**Find:**
```java
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
```

**Replace:**
```java
package com.redhat.coolstore.utils;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.ShutdownEvent;

import java.util.logging.Logger;

@ApplicationScoped
public class StartupListener {

    @Inject
    Logger log;

    void onStart(@Observes StartupEvent event) {
        log.info("Application started (Quarkus StartupEvent)");
    }

    void onStop(@Observes ShutdownEvent event) {
        log.info("Application stopping (Quarkus ShutdownEvent)");
    }
}
```

**Key Changes:**
1. **Removed:**
   - `extends ApplicationLifecycleListener`
   - `weblogic.application.*` imports
   - `@Override` annotations
   - `ApplicationLifecycleEvent` parameter

2. **Added:**
   - `@ApplicationScoped` (CDI bean)
   - `@Observes` annotation for CDI event observation
   - `io.quarkus.runtime.StartupEvent` and `ShutdownEvent`

3. **Mapping:**
   - `postStart()` → `onStart(@Observes StartupEvent)`
   - `preStop()` → `onStop(@Observes ShutdownEvent)`

### Step 5.2: Delete WebLogic Stub Classes
**Type:** SIMPLE  
**Files:** `weblogic/application/ApplicationLifecycleListener.java`, `weblogic/application/ApplicationLifecycleEvent.java`  
**Action:** Delete entire weblogic package

**Command:**
```bash
rm -rf src/main/java/weblogic/
```

**Reason:** No WebLogic dependencies in Quarkus; stubs no longer needed

### Step 5.3: Build Validation
**Type:** VALIDATION  
**Action:** Verify lifecycle migration compiles

**Command:**
```bash
mvn clean compile
```

**Expected Outcome:**
- No `weblogic.*` imports
- StartupListener compiles with Quarkus events
- All packages compile successfully

---

## Phase 6: Cleanup and Final Validation

**Goal:** Remove legacy files, verify migration completeness, run final build.

**Files Deleted:** Multiple

### Step 6.1: Remove WEB-INF Directory
**Type:** SIMPLE  
**Action:** Delete entire WEB-INF directory (no longer needed for JAR packaging)

**Command:**
```bash
rm -rf src/main/webapp/WEB-INF/
```

**Reason:** JAR packaging doesn't use WEB-INF; already deleted web.xml and beans.xml in Phase 2

### Step 6.2: Handle Static Web Resources
**Type:** SIMPLE  
**Action:** Move static web resources to Quarkus location

**Current:** `src/main/webapp/*` (JSP, HTML, JS, CSS)  
**Target:** `src/main/resources/META-INF/resources/`

**Command:**
```bash
mkdir -p src/main/resources/META-INF/resources
cp -r src/main/webapp/* src/main/resources/META-INF/resources/
rm -rf src/main/webapp/
```

**Files to move:**
- index.jsp → index.html (convert JSP to static HTML if needed)
- health.jsp → health endpoint (implement as REST endpoint)
- coolstore.json, keycloak.json
- app/, bower_components/, partials/

**Note:** 
- Quarkus serves static files from `META-INF/resources/`
- JSPs are not supported in Quarkus; convert to HTML + REST API calls
- Consider using Quarkus Qute templating if server-side rendering needed

### Step 6.3: Verify No Legacy Imports
**Type:** VALIDATION  
**Action:** Search for any remaining javax.* EE imports

**Command:**
```bash
grep -r "import javax.ejb" src/main/java/ || echo "No EJB imports found (good)"
grep -r "import javax.jms" src/main/java/ || echo "No JMS imports found (good)"
grep -r "import weblogic" src/main/java/ || echo "No WebLogic imports found (good)"
grep -r "import org.jboss" src/main/java/ || echo "No JBoss imports found (good)"
```

**Expected:** All commands should output "No ... found (good)"

**If found:** Update remaining files to use jakarta.* or Quarkus equivalents

### Step 6.4: Verify Jakarta EE Namespace
**Type:** VALIDATION  
**Action:** Confirm migration to jakarta.* namespace

**Command:**
```bash
grep -r "import javax\\.persistence" src/main/java/ || echo "All persistence imports migrated (good)"
grep -r "import javax\\.inject" src/main/java/ || echo "All inject imports migrated (good)"
grep -r "import javax\\.ws\\.rs" src/main/java/ || echo "All JAX-RS imports migrated (good)"
```

**Expected:** All should be migrated to `jakarta.*`

**Acceptable exceptions:**
- `javax.naming.*` (only if absolutely necessary, should be removed)
- Standard Java SE packages (`javax.crypto.*`, `javax.net.*` - these are fine)

### Step 6.5: Review and Update README
**Type:** SIMPLE  
**File:** `README.md`  
**Action:** Update documentation for Quarkus

**Update build/run instructions:**
```markdown
# Coolstore Monolith - Quarkus Application

## Prerequisites
- Java 17 or later
- Maven 3.8+

## Build
```bash
mvn clean package
```

## Run (Development Mode)
```bash
mvn quarkus:dev
```

## Run (Production)
```bash
java -jar target/quarkus-app/quarkus-run.jar
```

## Endpoints
- REST API: http://localhost:8080/
- Health: http://localhost:8080/q/health
- Metrics: http://localhost:8080/q/metrics
```

### Step 6.6: Final Compilation
**Type:** VALIDATION  
**Action:** Full clean build

**Command:**
```bash
mvn clean compile
```

**Expected Outcome:**
- BUILD SUCCESS
- No compilation errors
- All Quarkus extensions loaded
- No warnings about missing dependencies

### Step 6.7: Package Application
**Type:** VALIDATION  
**Action:** Build executable JAR

**Command:**
```bash
mvn clean package
```

**Expected Outcome:**
- BUILD SUCCESS
- JAR created: `target/quarkus-app/quarkus-run.jar`
- Or uber-jar: `target/monolith-1.0.0-SNAPSHOT-runner.jar` (if configured)

### Step 6.8: Test Application Startup
**Type:** VALIDATION  
**Action:** Start Quarkus in dev mode

**Command:**
```bash
mvn quarkus:dev
```

**Expected Outcome:**
- Application starts successfully
- Console shows "Quarkus X.X.X started in XXXms"
- No errors in startup logs
- REST endpoints accessible

**Test endpoints:**
```bash
curl http://localhost:8080/api/products
curl http://localhost:8080/api/cart/123
```

### Step 6.9: Verify Database Connectivity
**Type:** VALIDATION  
**Action:** Check database initialization

**Expected in logs:**
- Flyway migration executed (if configured)
- Hibernate schema validation passed
- Datasource connection successful

**Test query:**
```bash
curl http://localhost:8080/api/products
```
Should return product catalog from database

### Step 6.10: Test Messaging (if applicable)
**Type:** VALIDATION  
**Action:** Verify reactive messaging channels

**Expected in logs:**
- SmallRye Reactive Messaging initialized
- Channels "orders" connected (in-memory or Kafka)

**Test:**
Trigger a cart checkout (which should publish order message):
```bash
curl -X POST http://localhost:8080/api/cart/checkout/123
```

Check logs for:
- "Message received: ..." in OrderServiceMDB
- "Received message - checking inventory" in InventoryNotificationMDB

---

## Post-Migration Tasks

### Task 1: Performance Testing
- Compare startup time (Quarkus should be <1 second)
- Memory footprint (Quarkus should use <100MB)
- Response times for REST endpoints

### Task 2: Integration Testing
- Test all REST endpoints
- Verify database CRUD operations
- Confirm message processing
- Check error handling

### Task 3: Production Configuration
**File:** `src/main/resources/application.properties`

Add production profile:
```properties
# Production configuration
%prod.quarkus.datasource.jdbc.url=jdbc:postgresql://prod-db:5432/coolstore
%prod.quarkus.datasource.username=${DB_USER}
%prod.quarkus.datasource.password=${DB_PASSWORD}
%prod.quarkus.hibernate-orm.database.generation=none

# Production messaging (Kafka)
%prod.mp.messaging.incoming.orders.connector=smallrye-kafka
%prod.mp.messaging.incoming.orders.topic=orders
%prod.kafka.bootstrap.servers=${KAFKA_BOOTSTRAP_SERVERS}

# Production logging
%prod.quarkus.log.level=INFO
%prod.quarkus.log.console.json=true
```

### Task 4: Native Compilation (Optional)
For GraalVM native image:

**Update pom.xml:**
```xml
<profiles>
  <profile>
    <id>native</id>
    <properties>
      <quarkus.package.type>native</quarkus.package.type>
    </properties>
  </profile>
</profiles>
```

**Build native:**
```bash
mvn package -Pnative
```

**Run native:**
```bash
./target/monolith-1.0.0-SNAPSHOT-runner
```

### Task 5: Containerization
**Create Dockerfile.jvm:**
```dockerfile
FROM registry.access.redhat.com/ubi8/openjdk-17-runtime:latest

COPY target/quarkus-app /deployments/

EXPOSE 8080
USER 185

ENTRYPOINT [ "java", "-jar", "/deployments/quarkus-run.jar" ]
```

**Build:**
```bash
docker build -f Dockerfile.jvm -t coolstore-monolith:latest .
```

**Run:**
```bash
docker run -p 8080:8080 coolstore-monolith:latest
```

---

## Troubleshooting

### Issue 1: Compilation Errors with javax.* imports
**Symptom:** "package javax.ejb does not exist"  
**Solution:** Ensure all `javax.*` imports updated to `jakarta.*` (except Java SE packages)

### Issue 2: EntityManager injection fails
**Symptom:** "Unsatisfied dependency for type EntityManager"  
**Solution:** Verify `quarkus-hibernate-orm` extension in pom.xml; check `@PersistenceContext` or use `@Inject`

### Issue 3: @SessionScoped bean not working
**Symptom:** "No active request context" or session state lost  
**Solution:** 
- Add `quarkus-undertow` extension for HTTP session support
- Or redesign to use `@RequestScoped` or stateless pattern

### Issue 4: Messaging channels not found
**Symptom:** "No method found consuming channel 'orders'"  
**Solution:** Verify `@Incoming("orders")` annotation and messaging connector configuration in application.properties

### Issue 5: Database connection fails
**Symptom:** "Unable to acquire JDBC Connection"  
**Solution:** 
- Check datasource configuration in application.properties
- Verify JDBC driver extension (quarkus-jdbc-h2, etc.) in pom.xml
- Ensure database is running

### Issue 6: Static resources not served
**Symptom:** 404 for /index.html  
**Solution:** Move files to `src/main/resources/META-INF/resources/`

### Issue 7: Flyway migration fails
**Symptom:** "Flyway failed to initialize"  
**Solution:** 
- Verify migration scripts in `src/main/resources/db/migration/`
- Check `quarkus.flyway.migrate-at-start=true` in properties
- Ensure `quarkus-flyway` extension in pom.xml

---

## Summary

**Migration Complete When:**
- [x] pom.xml converted to Quarkus (Phase 1)
- [x] Configuration migrated to application.properties (Phase 2)
- [x] All EJBs converted to CDI beans (Phase 3)
- [x] JMS/MDBs converted to Reactive Messaging (Phase 4)
- [x] Lifecycle hooks using Quarkus events (Phase 5)
- [x] Legacy files removed (Phase 6)
- [x] `mvn clean package` succeeds
- [x] `mvn quarkus:dev` starts application
- [x] REST endpoints tested and working
- [x] Database connectivity verified
- [x] Message processing functional

**Total Files Modified:** 30+  
**Total Files Deleted:** 6+  
**Total Files Created:** 2+

**Result:** Standalone Quarkus 3 application, no application server required, cloud-native ready.
