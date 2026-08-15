# Implementation Plan: Java EE 7 to Quarkus 3 Migration

**Generated**: 2026-08-15T20:19:00Z  
**Project**: coolstore-monolith  
**Migration**: Java EE 7 (JBoss EAP/WildFly) → Quarkus 3

---

## Overview

This implementation plan provides step-by-step instructions for migrating the Coolstore monolith from Java EE 7 to Quarkus 3. The plan follows the javaee-to-quarkus skill phases and must be executed in order. After each phase, run `mvn clean compile` to validate changes before proceeding.

**Total Steps**: 47  
**Estimated Time**: 3-5 days  
**Prerequisite**: Java 17+ installed

---

## Phase 1: Build Configuration (9 steps)

**Goal**: Transform Maven build from WAR to JAR packaging with Quarkus dependencies.

### Step 1.1: Update packaging type
**Type**: SIMPLE  
**File**: `pom.xml`  
**Action**: Change packaging from WAR to JAR

**Before**:
```xml
<packaging>war</packaging>
```

**After**:
```xml
<packaging>jar</packaging>
```

---

### Step 1.2: Update Java version
**Type**: SIMPLE  
**File**: `pom.xml`  
**Action**: Update source/target from 1.8 to 17

**Before**:
```xml
<source>1.8</source>
<target>1.8</target>
```

**After**:
```xml
<source>17</source>
<target>17</target>
```

---

### Step 1.3: Add Quarkus properties
**Type**: SIMPLE  
**File**: `pom.xml`  
**Action**: Add Quarkus version property in `<properties>` section

**Add**:
```xml
<quarkus.platform.version>3.6.0</quarkus.platform.version>
<compiler-plugin.version>3.11.0</compiler-plugin.version>
```

---

### Step 1.4: Add Quarkus BOM
**Type**: SIMPLE  
**File**: `pom.xml`  
**Action**: Add Quarkus BOM in `<dependencyManagement>` section (create if not exists)

**Add**:
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

---

### Step 1.5: Remove Java EE dependencies
**Type**: SIMPLE  
**File**: `pom.xml`  
**Action**: Delete the following dependencies:

```xml
<!-- DELETE these -->
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
```

---

### Step 1.6: Add Quarkus core extensions
**Type**: SIMPLE  
**File**: `pom.xml`  
**Action**: Add Quarkus core extensions in `<dependencies>` section

**Add**:
```xml
<!-- Quarkus Core -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-arc</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-resteasy-reactive-jackson</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-hibernate-orm</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-jdbc-postgresql</artifactId>
</dependency>
```

---

### Step 1.7: Add Quarkus messaging and security extensions
**Type**: SIMPLE  
**File**: `pom.xml`  
**Action**: Add messaging and security extensions

**Add**:
```xml
<!-- Messaging -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-reactive-messaging</artifactId>
</dependency>

<!-- Security -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-oidc</artifactId>
</dependency>

<!-- Health and Observability -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-health</artifactId>
</dependency>
```

---

### Step 1.8: Update Flyway dependency
**Type**: SIMPLE  
**File**: `pom.xml`  
**Action**: Replace standalone Flyway with Quarkus extension

**Remove**:
```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
    <version>4.1.2</version>
</dependency>
```

**Add**:
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-flyway</artifactId>
</dependency>
```

---

### Step 1.9: Update build plugins
**Type**: SIMPLE  
**File**: `pom.xml`  
**Action**: Remove maven-war-plugin, add quarkus-maven-plugin, update compiler plugin

**Remove**:
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-war-plugin</artifactId>
    <version>3.2.0</version>
</plugin>
```

**Update compiler plugin**:
```xml
<plugin>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>${compiler-plugin.version}</version>
    <configuration>
        <encoding>${project.encoding}</encoding>
        <source>17</source>
        <target>17</target>
        <parameters>true</parameters>
    </configuration>
</plugin>
```

**Add Quarkus plugin**:
```xml
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

**Build Gate**: Run `mvn clean compile`. Must succeed before proceeding.

---

## Phase 2: Application Configuration (6 steps)

**Goal**: Replace XML configuration with application.properties.

### Step 2.1: Create application.properties
**Type**: CREATE  
**File**: `src/main/resources/application.properties`  
**Action**: Create new file with datasource configuration

**Content**:
```properties
# Datasource configuration
quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=coolstore
quarkus.datasource.password=coolstore
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/coolstore

# Hibernate configuration
quarkus.hibernate-orm.database.generation=none
quarkus.hibernate-orm.log.sql=false
quarkus.hibernate-orm.log.format-sql=true
quarkus.hibernate-orm.sql-load-script=no-file

# Flyway configuration
quarkus.flyway.migrate-at-start=true
quarkus.flyway.baseline-on-migrate=true
quarkus.flyway.locations=classpath:db/migration

# Application configuration
quarkus.http.port=8080
quarkus.http.test-port=8081

# Dev mode configuration
%dev.quarkus.log.level=INFO
%dev.quarkus.hibernate-orm.log.sql=true
```

**Notes**:
- Adjust database credentials to match your environment
- Datasource JNDI name is no longer needed (direct injection)
- Flyway will auto-run migrations on startup

---

### Step 2.2: Add OIDC configuration
**Type**: MODIFY  
**File**: `src/main/resources/application.properties`  
**Action**: Append Keycloak/OIDC configuration

**Append**:
```properties
# OIDC/Keycloak configuration
quarkus.oidc.auth-server-url=http://localhost:8081/realms/eap
quarkus.oidc.client-id=eap-app
quarkus.oidc.credentials.secret=secret
quarkus.oidc.application-type=web-app
quarkus.oidc.token.audience=eap-app

# Optional: disable OIDC for dev/test if not needed
%dev.quarkus.oidc.enabled=true
%test.quarkus.oidc.enabled=false
```

**Notes**:
- Update `auth-server-url` to match your Keycloak server
- Get `credentials.secret` from keycloak.json if needed
- Set to `service` application-type if frontend is served separately

---

### Step 2.3: Add messaging configuration
**Type**: MODIFY  
**File**: `src/main/resources/application.properties`  
**Action**: Configure in-memory messaging channels

**Append**:
```properties
# Reactive Messaging - In-Memory Connector
mp.messaging.outgoing.orders.connector=smallrye-in-memory
mp.messaging.incoming.orders.connector=smallrye-in-memory

# Channel broadcast (multiple consumers)
mp.messaging.incoming.orders.broadcast=true
```

**Notes**:
- `orders` channel replaces `topic/orders` JMS topic
- In-memory connector is for single-instance deployments
- To use Kafka: change connector to `smallrye-kafka`, add broker config

---

### Step 2.4: Delete persistence.xml
**Type**: DELETE  
**File**: `src/main/resources/META-INF/persistence.xml`  
**Action**: Delete file (configuration moved to application.properties)

---

### Step 2.5: Delete web.xml (if exists)
**Type**: DELETE  
**File**: `src/main/webapp/WEB-INF/web.xml`  
**Action**: Delete file if present (not needed in Quarkus)

**Check**: Run `find src/main/webapp -name "web.xml"` first

---

### Step 2.6: Delete beans.xml (if exists)
**Type**: DELETE  
**File**: `src/main/webapp/WEB-INF/beans.xml`  
**Action**: Delete file if present (CDI enabled by default in Quarkus)

**Check**: Run `find src/main/webapp -name "beans.xml"` first

**Build Gate**: Run `mvn clean compile`. Must succeed before proceeding.

---

## Phase 3: EJB to CDI Conversion (12 steps)

**Goal**: Replace EJB annotations with CDI, remove JNDI lookups.

### Step 3.1: Convert ProductService
**Type**: SIMPLE  
**File**: `src/main/java/com/redhat/coolstore/service/ProductService.java`  
**Action**: Replace @Stateless with @ApplicationScoped

**Before**:
```java
import javax.ejb.Stateless;

@Stateless
public class ProductService {
```

**After**:
```java
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProductService {
```

---

### Step 3.2: Convert CatalogService
**Type**: SIMPLE  
**File**: `src/main/java/com/redhat/coolstore/service/CatalogService.java`  
**Action**: Replace @Stateless with @ApplicationScoped

**Before**:
```java
import javax.ejb.Stateless;

@Stateless
public class CatalogService {
```

**After**:
```java
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CatalogService {
```

---

### Step 3.3: Convert OrderService
**Type**: SIMPLE  
**File**: `src/main/java/com/redhat/coolstore/service/OrderService.java`  
**Action**: Replace @Stateless with @ApplicationScoped

**Before**:
```java
import javax.ejb.Stateless;

@Stateless
public class OrderService {
```

**After**:
```java
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OrderService {
```

---

### Step 3.4: Convert ShoppingCartOrderProcessor
**Type**: SIMPLE  
**File**: `src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java`  
**Action**: Replace @Stateless with @ApplicationScoped (will modify further in Phase 4)

**Before**:
```java
import javax.ejb.Stateless;

@Stateless
public class ShoppingCartOrderProcessor {
```

**After**:
```java
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ShoppingCartOrderProcessor {
```

---

### Step 3.5: Convert ShoppingCartService
**Type**: COMPLEX  
**File**: `src/main/java/com/redhat/coolstore/service/ShoppingCartService.java`  
**Action**: Replace @Stateful with @SessionScoped

**Before**:
```java
import javax.ejb.Stateful;

@Stateful
public class ShoppingCartService {
```

**After**:
```java
import jakarta.enterprise.context.SessionScoped;
import java.io.Serializable;

@SessionScoped
public class ShoppingCartService implements Serializable {
    private static final long serialVersionUID = 1L;
```

**Notes**:
- @SessionScoped beans must implement Serializable
- Verify that HTTP session is available (quarkus-undertow handles this)
- Test carefully: session scope behavior differs from @Stateful EJB

---

### Step 3.6: Remove ShippingService @Remote annotation
**Type**: SIMPLE  
**File**: `src/main/java/com/redhat/coolstore/service/ShippingService.java`  
**Action**: Replace @Stateless @Remote with @ApplicationScoped

**Before**:
```java
import javax.ejb.Remote;
import javax.ejb.Stateless;

@Stateless
@Remote(ShippingServiceRemote.class)
public class ShippingService implements ShippingServiceRemote {
```

**After**:
```java
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ShippingService implements ShippingServiceRemote {
```

**Note**: Keep implementing ShippingServiceRemote for now (will remove in step 3.8)

---

### Step 3.7: Replace JNDI lookup with injection in ShoppingCartService
**Type**: COMPLEX  
**File**: `src/main/java/com/redhat/coolstore/service/ShoppingCartService.java`  
**Action**: Remove lookupShippingServiceRemote() method, inject ShippingService directly

**Before**:
```java
import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

// ... in class body ...

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

// ... usage ...
sc.setShippingTotal(lookupShippingServiceRemote().calculateShipping(sc));
sc.setShippingTotal(sc.getShippingTotal()
        + lookupShippingServiceRemote().calculateShippingInsurance(sc));
```

**After**:
```java
import jakarta.inject.Inject;

// ... in class body (add field) ...

@Inject
ShippingService shippingService;

// ... DELETE lookupShippingServiceRemote() method entirely ...

// ... update usage ...
sc.setShippingTotal(shippingService.calculateShipping(sc));
sc.setShippingTotal(sc.getShippingTotal()
        + shippingService.calculateShippingInsurance(sc));
```

**Remove imports**:
```java
// DELETE these imports
import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
```

---

### Step 3.8: Delete ShippingServiceRemote interface
**Type**: DELETE  
**File**: `src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java`  
**Action**: Delete entire file

---

### Step 3.9: Update ShippingService to remove interface
**Type**: SIMPLE  
**File**: `src/main/java/com/redhat/coolstore/service/ShippingService.java`  
**Action**: Remove `implements ShippingServiceRemote`

**Before**:
```java
@ApplicationScoped
public class ShippingService implements ShippingServiceRemote {
```

**After**:
```java
@ApplicationScoped
public class ShippingService {
```

---

### Step 3.10: Update all javax imports to jakarta (CDI, Inject)
**Type**: SIMPLE  
**Files**: All Java files in `src/main/java/com/redhat/coolstore/`  
**Action**: Replace javax.inject and javax.enterprise imports with jakarta

**Find/Replace**:
```
javax.inject.Inject → jakarta.inject.Inject
javax.enterprise.context → jakarta.enterprise.context
```

**Affected files**:
- All service classes
- REST endpoints
- Producers.java
- Resources.java (will delete later)

**Tool**: Use IDE find/replace or:
```bash
find src/main/java -name "*.java" -exec sed -i 's/javax\.inject\./jakarta.inject./g' {} +
find src/main/java -name "*.java" -exec sed -i 's/javax\.enterprise\./jakarta.enterprise./g' {} +
```

---

### Step 3.11: Update JAX-RS and JPA imports
**Type**: SIMPLE  
**Files**: REST endpoints and entity classes  
**Action**: Replace javax.ws.rs and javax.persistence imports

**Find/Replace**:
```
javax.ws.rs → jakarta.ws.rs
javax.persistence → jakarta.persistence
```

**Tool**:
```bash
find src/main/java -name "*.java" -exec sed -i 's/javax\.ws\.rs\./jakarta.ws.rs./g' {} +
find src/main/java -name "*.java" -exec sed -i 's/javax\.persistence\./jakarta.persistence./g' {} +
```

---

### Step 3.12: Update annotation imports
**Type**: SIMPLE  
**Files**: All Java files  
**Action**: Replace javax.annotation imports

**Find/Replace**:
```
javax.annotation → jakarta.annotation
```

**Tool**:
```bash
find src/main/java -name "*.java" -exec sed -i 's/javax\.annotation\./jakarta.annotation./g' {} +
```

**Build Gate**: Run `mvn clean compile`. Must succeed before proceeding.

---

## Phase 4: Messaging Conversion (8 steps)

**Goal**: Replace JMS MDBs and producers with SmallRye Reactive Messaging.

### Step 4.1: Convert OrderServiceMDB to reactive method
**Type**: COMPLEX  
**File**: `src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java`  
**Action**: Replace @MessageDriven with @Incoming, convert onMessage to reactive method

**Before**:
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

**After**:
```java
package com.redhat.coolstore.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

import java.util.logging.Logger;

@ApplicationScoped
public class OrderServiceMDB {

	@Inject
	Logger log;

	@Inject
	OrderService orderService;

	@Inject
	CatalogService catalogService;

	@Incoming("orders")
	public void processOrder(String orderStr) {
		log.info("Received order: " + orderStr);
		try {
			Order order = Transformers.jsonToOrder(orderStr);
			log.info("Order object is " + order);
			orderService.save(order);
			order.getItemList().forEach(orderItem -> {
				catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity());
			});
		} catch (Exception e) {
			log.severe("Error processing order: " + e.getMessage());
			throw new RuntimeException(e);
		}
	}
}
```

**Changes**:
- @MessageDriven → @ApplicationScoped
- Removed MessageListener interface
- onMessage(Message) → processOrder(String)
- @Incoming("orders") annotation
- Simplified: no JMS API, direct String parameter
- Better logging

---

### Step 4.2: Handle InventoryNotificationMDB
**Type**: DECISION  
**File**: `src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java`  
**Action**: Determine if this class is used, then either fix or delete

**Current state**: Contains broken WebLogic JNDI code, no @MessageDriven annotation

**Option A - DELETE (recommended)**:
If this class is not used in production (appears to be legacy/dead code):
```bash
# Delete the file
rm src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
```

**Option B - FIX** (if actually needed):
Convert to reactive messaging like OrderServiceMDB:

```java
package com.redhat.coolstore.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

import java.util.logging.Logger;

@ApplicationScoped
public class InventoryNotificationMDB {

    private static final int LOW_THRESHOLD = 50;

    @Inject
    Logger log;

    @Inject
    private CatalogService catalogService;

    @Incoming("orders")
    public void checkInventory(String orderStr) {
        try {
            log.info("Checking inventory for order");
            Order order = Transformers.jsonToOrder(orderStr);
            order.getItemList().forEach(orderItem -> {
                int oldQuantity = catalogService.getCatalogItemById(orderItem.getProductId())
                    .getInventory().getQuantity();
                int newQuantity = oldQuantity - orderItem.getQuantity();
                if (newQuantity < LOW_THRESHOLD) {
                    log.warning("Inventory for item " + orderItem.getProductId() + 
                        " is below threshold (" + LOW_THRESHOLD + "), contact supplier!");
                }
            });
        } catch (Exception e) {
            log.severe("Error checking inventory: " + e.getMessage());
        }
    }
}
```

**Decision**: Choose Option A (DELETE) unless user confirms it's needed.

---

### Step 4.3: Convert ShoppingCartOrderProcessor to use Emitter
**Type**: COMPLEX  
**File**: `src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java`  
**Action**: Replace JMS producer with Reactive Messaging Emitter

**Before**:
```java
package com.redhat.coolstore.service;

import java.util.logging.Logger;
import javax.ejb.Stateless;
import javax.annotation.Resource;
import javax.inject.Inject;
import javax.jms.JMSContext;
import javax.jms.Topic;

import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.utils.Transformers;

@Stateless
public class ShoppingCartOrderProcessor  {

    @Inject
    Logger log;

    @Inject
    private transient JMSContext context;

    @Resource(lookup = "java:/topic/orders")
    private Topic ordersTopic;

    public void  process(ShoppingCart cart) {
        log.info("Sending order from processor: ");
        context.createProducer().send(ordersTopic, Transformers.shoppingCartToJson(cart));
    }
}
```

**After**:
```java
package com.redhat.coolstore.service;

import java.util.logging.Logger;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.utils.Transformers;

@ApplicationScoped
public class ShoppingCartOrderProcessor {

    @Inject
    Logger log;

    @Inject
    @Channel("orders")
    Emitter<String> ordersEmitter;

    public void process(ShoppingCart cart) {
        log.info("Sending order from processor");
        String orderJson = Transformers.shoppingCartToJson(cart);
        ordersEmitter.send(orderJson);
    }
}
```

**Changes**:
- @Stateless → @ApplicationScoped (already done in step 3.4)
- Removed JMSContext and Topic fields
- Added @Channel Emitter<String>
- send() instead of createProducer().send()
- No JMS imports needed

---

### Step 4.4: Update Transformers if needed
**Type**: VERIFY  
**File**: `src/main/java/com/redhat/coolstore/utils/Transformers.java`  
**Action**: Verify JSON transformation methods work, update imports if needed

**Check**: Ensure methods `shoppingCartToJson()` and `jsonToOrder()` exist and work correctly.

**If using javax.json**: Update to jakarta.json:
```
javax.json → jakarta.json
```

**If no changes needed**: Skip this step.

---

### Step 4.5: Remove JMS dependencies check
**Type**: VERIFY  
**Files**: All service classes  
**Action**: Verify no javax.jms imports remain

**Command**:
```bash
grep -r "javax.jms" src/main/java/
```

**Expected**: No results (all JMS code removed)

**If found**: Remove remaining JMS imports and code

---

### Step 4.6: Test messaging configuration
**Type**: VERIFY  
**File**: `src/main/resources/application.properties`  
**Action**: Verify messaging channel configuration is present

**Verify these lines exist**:
```properties
mp.messaging.outgoing.orders.connector=smallrye-in-memory
mp.messaging.incoming.orders.connector=smallrye-in-memory
mp.messaging.incoming.orders.broadcast=true
```

---

### Step 4.7: Optional - Add Kafka connector config (for production)
**Type**: DOCUMENT  
**File**: `src/main/resources/application.properties`  
**Action**: Add commented Kafka configuration for future use

**Append** (commented out):
```properties
# --- Kafka Configuration (uncomment for production multi-replica deployment) ---
# mp.messaging.outgoing.orders.connector=smallrye-kafka
# mp.messaging.outgoing.orders.topic=orders
# mp.messaging.outgoing.orders.value.serializer=org.apache.kafka.common.serialization.StringSerializer
# mp.messaging.incoming.orders.connector=smallrye-kafka
# mp.messaging.incoming.orders.topic=orders
# mp.messaging.incoming.orders.value.deserializer=org.apache.kafka.common.serialization.StringDeserializer
# kafka.bootstrap.servers=localhost:9092
```

**Also add to pom.xml** (commented):
```xml
<!-- Uncomment for Kafka support
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-reactive-messaging-kafka</artifactId>
</dependency>
-->
```

---

### Step 4.8: Update entity Transaction imports
**Type**: SIMPLE  
**Files**: Service classes using @Transactional  
**Action**: Update transaction imports if used

**Find/Replace**:
```
javax.transaction → jakarta.transaction
```

**Tool**:
```bash
find src/main/java -name "*.java" -exec sed -i 's/javax\.transaction\./jakarta.transaction./g' {} +
```

**Build Gate**: Run `mvn clean compile`. Must succeed before proceeding.

---

## Phase 5: Lifecycle Conversion (4 steps)

**Goal**: Replace Java EE lifecycle hooks with Quarkus events.

### Step 5.1: Delete StartupListener
**Type**: DELETE  
**File**: `src/main/java/com/redhat/coolstore/utils/StartupListener.java`  
**Action**: Delete entire file (WebLogic lifecycle listener, non-functional)

---

### Step 5.2: Simplify DataBaseMigrationStartup
**Type**: COMPLEX  
**File**: `src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java`  
**Action**: Remove manual Flyway execution (Quarkus Flyway extension handles this)

**Before**:
```java
package com.redhat.coolstore.utils;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.inject.Inject;
import javax.sql.DataSource;
import java.util.logging.Level;
import java.util.logging.Logger;

@Singleton
@Startup
@TransactionManagement(TransactionManagementType.BEAN)
public class DataBaseMigrationStartup {

    @Inject
    Logger logger;

    @Resource(mappedName = "java:jboss/datasources/CoolstoreDS")
    DataSource dataSource;

    @PostConstruct
    private void startup() {
        try {
            logger.info("Initializing/migrating the database using FlyWay");
            Flyway flyway = new Flyway();
            flyway.setDataSource(dataSource);
            flyway.baseline();
            flyway.migrate();
        } catch (FlywayException e) {
            if(logger !=null)
                logger.log(Level.SEVERE,"FAILED TO INITIALIZE THE DATABASE: " + e.getMessage(),e);
            else
                System.out.println("FAILED TO INITIALIZE THE DATABASE: " + e.getMessage() + " and injection of logger doesn't work");
        }
    }
}
```

**After (Option A - DELETE entire file - recommended)**:
Quarkus Flyway extension automatically runs migrations on startup, so this class is no longer needed.

```bash
rm src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
```

**After (Option B - Convert to startup event observer - if custom logic needed)**:
```java
package com.redhat.coolstore.utils;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import io.quarkus.runtime.StartupEvent;

import java.util.logging.Logger;

@ApplicationScoped
public class DataBaseMigrationStartup {

    @Inject
    Logger logger;

    void onStartup(@Observes StartupEvent event) {
        logger.info("Application started - Flyway migrations handled automatically by Quarkus");
        // Add any custom startup logic here if needed
    }
}
```

**Recommendation**: Use Option A (DELETE). Flyway is configured in application.properties and runs automatically.

---

### Step 5.3: Optional - Add application startup logging
**Type**: CREATE  
**File**: `src/main/java/com/redhat/coolstore/utils/ApplicationLifecycle.java`  
**Action**: Create startup/shutdown event observers (replaces StartupListener functionality)

**Create new file**:
```java
package com.redhat.coolstore.utils;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

import java.util.logging.Logger;
import jakarta.inject.Inject;

@ApplicationScoped
public class ApplicationLifecycle {

    @Inject
    Logger log;

    void onStart(@Observes StartupEvent event) {
        log.info("Coolstore application starting...");
    }

    void onStop(@Observes ShutdownEvent event) {
        log.info("Coolstore application stopping...");
    }
}
```

**Note**: This is optional. Only create if startup/shutdown logging is desired.

---

### Step 5.4: Verify lifecycle imports
**Type**: VERIFY  
**Files**: Any files using lifecycle annotations  
**Action**: Ensure no javax.ejb imports remain

**Check**:
```bash
grep -r "javax.ejb" src/main/java/
```

**Expected**: No results

**If found**: Replace with appropriate annotations:
- @Singleton @Startup → @ApplicationScoped with @Observes StartupEvent
- @PostConstruct → @Observes StartupEvent or keep if still valid
- @PreDestroy → @Observes ShutdownEvent or keep if still valid

**Build Gate**: Run `mvn clean compile`. Must succeed before proceeding.

---

## Phase 6: Cleanup and Frontend (8 steps)

**Goal**: Remove legacy code, migrate frontend to static resources.

### Step 6.1: Delete weblogic package
**Type**: DELETE  
**Files**: `src/main/java/weblogic/**/*`  
**Action**: Delete entire weblogic stub package

**Command**:
```bash
rm -rf src/main/java/weblogic/
```

**This removes**:
- ApplicationLifecycleEvent.java
- ApplicationLifecycleListener.java
- NonCatalogLogger.java

---

### Step 6.2: Delete EntityManager producer
**Type**: DELETE  
**File**: `src/main/java/com/redhat/coolstore/persistence/Resources.java`  
**Action**: Delete entire file (EntityManager is auto-injected in Quarkus)

**Command**:
```bash
rm src/main/java/com/redhat/coolstore/persistence/Resources.java
```

**Note**: Any @PersistenceContext EntityManager fields will work via auto-injection.

---

### Step 6.3: Update EntityManager injection in entities/services
**Type**: VERIFY  
**Files**: Services using EntityManager  
**Action**: Verify @PersistenceContext is replaced with @Inject (or keep @PersistenceContext)

**Quarkus supports both**:
```java
@Inject
EntityManager em;
```
**or**
```java
@PersistenceContext
EntityManager em;
```

**Action**: No changes needed if already using @Inject. If using @PersistenceContext, optionally change to @Inject.

---

### Step 6.4: Move frontend files to META-INF/resources
**Type**: COMPLEX  
**Files**: `src/main/webapp/**/*`  
**Action**: Move static resources to Quarkus static resource directory

**Commands**:
```bash
# Create target directory
mkdir -p src/main/resources/META-INF/resources

# Move all webapp content
cp -r src/main/webapp/* src/main/resources/META-INF/resources/

# Delete old webapp directory (after verification)
# rm -rf src/main/webapp
```

**Note**: Keep webapp directory for now, delete after testing (Step 6.8)

---

### Step 6.5: Convert index.jsp to index.html
**Type**: COMPLEX  
**File**: `src/main/resources/META-INF/resources/index.html` (new)  
**Action**: Convert JSP to static HTML, remove session initialization

**Original index.jsp** (src/main/webapp/index.jsp):
```jsp
<% request.getSession(true); %>
<!DOCTYPE html>
<html>
<head>
    <!-- AngularJS app code -->
</head>
<body>
    <!-- ... -->
</body>
</html>
```

**New index.html** (create in META-INF/resources):
```html
<!DOCTYPE html>
<html>
<head>
    <!-- Keep all existing head content from index.jsp -->
    <!-- Remove JSP scriptlet: <% request.getSession(true); %> -->
</head>
<body>
    <!-- Keep all existing body content from index.jsp -->
</body>
</html>
```

**Action**:
1. Copy index.jsp to index.html in new location
2. Remove JSP scriptlet: `<% request.getSession(true); %>`
3. Verify all AngularJS references still work
4. Update paths if needed (usually relative paths work as-is)

---

### Step 6.6: Delete JSP files
**Type**: DELETE  
**Files**: `src/main/webapp/*.jsp`  
**Action**: Delete index.jsp and health.jsp after conversion

**Commands**:
```bash
# Only delete after index.html is created and tested
rm src/main/resources/META-INF/resources/index.jsp
rm src/main/resources/META-INF/resources/health.jsp  # if exists
```

---

### Step 6.7: Add health endpoint (replaces health.jsp)
**Type**: VERIFY  
**Action**: Verify SmallRye Health is configured (already added in Step 1.7)

**Test**:
After build, access:
- `http://localhost:8080/q/health` - Overall health
- `http://localhost:8080/q/health/live` - Liveness
- `http://localhost:8080/q/health/ready` - Readiness

**Optional - Create custom health check**:
```java
package com.redhat.coolstore.health;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

@Liveness
@ApplicationScoped
public class DatabaseHealthCheck implements HealthCheck {

    @Override
    public HealthCheckResponse call() {
        // Add database connectivity check if needed
        return HealthCheckResponse.up("Database connection");
    }
}
```

---

### Step 6.8: Delete webapp directory
**Type**: DELETE  
**Files**: `src/main/webapp/**/*`  
**Action**: Delete entire webapp directory after frontend migration verified

**Command**:
```bash
# Only run after testing frontend in META-INF/resources
rm -rf src/main/webapp
```

**Verify first**:
- Frontend loads at http://localhost:8080/
- All assets (CSS, JS, images) load correctly
- AngularJS app functions properly

**Build Gate**: Run `mvn clean package`. Must succeed before proceeding.

---

## Phase 7: Final Verification (10 steps)

**Goal**: Build, test, and verify the migrated application.

### Step 7.1: Full clean build
**Type**: VERIFY  
**Action**: Run full Maven build

**Command**:
```bash
mvn clean package
```

**Expected**: Build SUCCESS, creates `target/quarkus-app/`

**If fails**: Review error messages, fix compilation issues, repeat.

---

### Step 7.2: Verify package structure
**Type**: VERIFY  
**Action**: Confirm JAR packaging (not WAR)

**Command**:
```bash
ls -lh target/quarkus-app/
```

**Expected output**:
- `quarkus-run.jar` (main JAR)
- `lib/` directory
- `app/` directory
- `quarkus/` directory

---

### Step 7.3: Run database setup
**Type**: SETUP  
**Action**: Start PostgreSQL database (if not running)

**Docker command** (example):
```bash
docker run --name coolstore-db \
  -e POSTGRES_DB=coolstore \
  -e POSTGRES_USER=coolstore \
  -e POSTGRES_PASSWORD=coolstore \
  -p 5432:5432 \
  -d postgres:14
```

**Verify**:
```bash
psql -h localhost -U coolstore -d coolstore -c "SELECT 1;"
```

---

### Step 7.4: Run Keycloak setup (if needed)
**Type**: SETUP  
**Action**: Start Keycloak server and import realm

**Docker command** (example):
```bash
docker run --name keycloak \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  -p 8081:8080 \
  -d quay.io/keycloak/keycloak:latest start-dev
```

**Import realm**:
1. Access http://localhost:8081/
2. Login with admin/admin
3. Import `realm-export.json`
4. Verify client `eap-app` exists

**Update application.properties** if Keycloak URL differs.

---

### Step 7.5: Start application in dev mode
**Type**: TEST  
**Action**: Run Quarkus dev mode

**Command**:
```bash
mvn quarkus:dev
```

**Expected**:
- Application starts in <3 seconds
- Dev UI available at http://localhost:8080/q/dev/
- Logs show "Installed features: [...]"

**Verify startup logs**:
- Flyway migrations run successfully
- Hibernate ORM initializes
- No errors or warnings

---

### Step 7.6: Test health endpoints
**Type**: TEST  
**Action**: Verify health endpoints respond

**Commands**:
```bash
curl http://localhost:8080/q/health
curl http://localhost:8080/q/health/live
curl http://localhost:8080/q/health/ready
```

**Expected**: HTTP 200, JSON response with "UP" status

---

### Step 7.7: Test REST API endpoints
**Type**: TEST  
**Action**: Verify REST endpoints work

**Test Product endpoint**:
```bash
curl http://localhost:8080/services/products
```

**Expected**: JSON array of products (if data loaded)

**Test Cart endpoint**:
```bash
curl http://localhost:8080/services/cart
```

**Expected**: JSON cart object or empty cart

**If 401 Unauthorized**: OIDC is enforcing auth. Either:
- Configure endpoints as public in application.properties
- Obtain token from Keycloak and include in request

---

### Step 7.8: Test frontend
**Type**: TEST  
**Action**: Load frontend in browser

**Navigate to**: http://localhost:8080/

**Verify**:
- AngularJS app loads without errors
- UI displays correctly (PatternFly styles)
- Can browse products (if REST API accessible)
- No console errors (check browser dev tools)

**If authentication issues**:
- Verify Keycloak is running
- Check quarkus.oidc.* configuration
- Review browser network tab for auth redirects

---

### Step 7.9: Test order workflow (E2E)
**Type**: TEST  
**Action**: Test complete order processing workflow

**Steps**:
1. **Add products to cart**: Use frontend or REST API
2. **Checkout**: Click checkout button
3. **Verify message sent**: Check logs for "Sending order from processor"
4. **Verify order received**: Check logs for "Received order:"
5. **Verify order saved**: Check database:
   ```sql
   SELECT * FROM orders ORDER BY id DESC LIMIT 1;
   ```
6. **Verify inventory updated**: Check catalog_items table

**Expected**:
- Order appears in database
- Inventory quantity decreases
- No exceptions in logs

**If messaging fails**:
- Verify mp.messaging.* configuration in application.properties
- Check that both OrderServiceMDB and ShoppingCartOrderProcessor use channel "orders"
- Ensure broadcast=true for multiple consumers

---

### Step 7.10: Review and fix any warnings
**Type**: VERIFY  
**Action**: Review application logs for warnings

**Check for**:
- Deprecated API warnings
- Configuration warnings
- Missing dependencies

**Common warnings and fixes**:

1. **"CDI: Unsatisfied dependency"**: Missing @Inject or bean not discovered
2. **"Hibernate: No JTA platform available"**: Normal in Quarkus (uses Narayana)
3. **"OIDC: Unable to connect to auth server"**: Keycloak not running or wrong URL

**Action**: Fix any critical warnings, document known benign warnings.

---

## Post-Migration Tasks

### Documentation Updates
1. Update README.md:
   - Change deployment instructions (Quarkus instead of JBoss/WildFly)
   - Update build command: `mvn clean package`
   - Update run command: `java -jar target/quarkus-app/quarkus-run.jar`
   - Add dev mode instructions: `mvn quarkus:dev`
   - Update configuration details (application.properties)

2. Update database setup documentation:
   - Remove JNDI datasource setup
   - Add datasource configuration in application.properties
   - Note: Flyway runs automatically on startup

3. Update deployment documentation:
   - Container image: Quarkus produces optimized container images
   - Kubernetes: Add Quarkus Kubernetes extension if deploying to K8s
   - Native image: Optional future optimization

### Configuration Externaliation
For production deployments, externalize configuration:

**Environment variables** (recommended):
```bash
export QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://prod-db:5432/coolstore
export QUARKUS_DATASOURCE_USERNAME=prod_user
export QUARKUS_DATASOURCE_PASSWORD=prod_password
export QUARKUS_OIDC_AUTH_SERVER_URL=https://keycloak.prod.example.com/realms/eap
```

**Or application-prod.properties**:
Create `src/main/resources/application-prod.properties` with production values.
Activate with: `java -Dquarkus.profile=prod -jar quarkus-run.jar`

### Performance Optimization (Optional)
1. **Native Image Compilation**:
   ```bash
   mvn package -Pnative
   ```
   Creates native executable for ultra-fast startup (<100ms) and low memory.

2. **Container Image**:
   Add to pom.xml:
   ```xml
   <dependency>
       <groupId>io.quarkus</groupId>
       <artifactId>quarkus-container-image-docker</artifactId>
   </dependency>
   ```
   Build: `mvn package -Dquarkus.container-image.build=true`

3. **Kubernetes Deployment**:
   Add to pom.xml:
   ```xml
   <dependency>
       <groupId>io.quarkus</groupId>
       <artifactId>quarkus-kubernetes</artifactId>
   </dependency>
   ```
   Generates Kubernetes manifests automatically.

### Switch to Kafka (if needed)
For production multi-replica deployment:

1. **Add Kafka extension**:
   ```xml
   <dependency>
       <groupId>io.quarkus</groupId>
       <artifactId>quarkus-smallrye-reactive-messaging-kafka</artifactId>
   </dependency>
   ```

2. **Update application.properties**:
   ```properties
   # Remove in-memory connector config
   # Add Kafka config (see Step 4.7 for details)
   mp.messaging.outgoing.orders.connector=smallrye-kafka
   mp.messaging.incoming.orders.connector=smallrye-kafka
   kafka.bootstrap.servers=kafka:9092
   ```

### Testing Enhancements
1. Add Quarkus tests:
   ```xml
   <dependency>
       <groupId>io.quarkus</groupId>
       <artifactId>quarkus-junit5</artifactId>
       <scope>test</scope>
   </dependency>
   <dependency>
       <groupId>io.rest-assured</groupId>
       <artifactId>rest-assured</artifactId>
       <scope>test</scope>
   </dependency>
   ```

2. Create REST endpoint tests:
   ```java
   @QuarkusTest
   public class ProductEndpointTest {
       @Test
       public void testProductsEndpoint() {
           given()
               .when().get("/services/products")
               .then()
               .statusCode(200);
       }
   }
   ```

---

## Rollback Procedure

If migration fails or issues discovered in production:

1. **Stop Quarkus application**
2. **Checkout pre-migration Git commit**:
   ```bash
   git checkout <pre-migration-commit-hash>
   ```
3. **Rebuild WAR**:
   ```bash
   mvn clean package
   ```
4. **Deploy WAR to JBoss/WildFly**:
   ```bash
   cp target/ROOT.war $JBOSS_HOME/standalone/deployments/
   ```
5. **Restart application server**

**Database**: No changes needed (same schema, same Flyway migrations)  
**Keycloak**: No changes needed (same realm)

---

## Known Issues and Solutions

### Issue 1: Session Scope Not Working
**Symptom**: ShoppingCartService state not maintained between requests  
**Cause**: HTTP session not available or not propagated  
**Solution**: Add quarkus-undertow extension:
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-undertow</artifactId>
</dependency>
```

### Issue 2: Messaging Not Working
**Symptom**: OrderServiceMDB not receiving messages  
**Cause**: Channel configuration mismatch  
**Solution**: Verify channel names match exactly:
- Emitter: `@Channel("orders")`
- Consumer: `@Incoming("orders")`
- Config: `mp.messaging.outgoing.orders.*` and `mp.messaging.incoming.orders.*`

### Issue 3: OIDC Authentication Failing
**Symptom**: 401 Unauthorized on all endpoints  
**Cause**: Keycloak not accessible or wrong configuration  
**Solutions**:
- Verify Keycloak is running and accessible
- Check `quarkus.oidc.auth-server-url` is correct
- Temporarily disable: `quarkus.oidc.enabled=false` for testing
- Check logs for OIDC errors

### Issue 4: Database Connection Failed
**Symptom**: Cannot connect to PostgreSQL  
**Solutions**:
- Verify PostgreSQL is running
- Check connection URL, username, password
- Test with `psql` command
- Review quarkus.datasource.* properties

### Issue 5: Frontend Not Loading
**Symptom**: 404 on http://localhost:8080/  
**Cause**: Static resources not in correct location  
**Solution**: Verify files in `src/main/resources/META-INF/resources/`

---

## Success Criteria Checklist

- [ ] Application builds successfully: `mvn clean package`
- [ ] Application starts: `java -jar target/quarkus-app/quarkus-run.jar`
- [ ] Dev mode works: `mvn quarkus:dev`
- [ ] Startup time <3 seconds
- [ ] Memory usage <100MB (vs 500MB+ with JBoss/WildFly)
- [ ] Health endpoints respond: `/q/health`, `/q/health/live`, `/q/health/ready`
- [ ] REST APIs work: `/services/products`, `/services/cart`, `/services/order`
- [ ] Frontend loads and functions
- [ ] Keycloak authentication works
- [ ] Database connectivity works
- [ ] Flyway migrations run on startup
- [ ] Order workflow complete: checkout → message → order saved → inventory updated
- [ ] No WebLogic legacy code remains
- [ ] No javax.* EE imports (all jakarta.*)
- [ ] No compilation warnings
- [ ] All tests pass (if tests added)

---

## Appendix A: File Change Summary

| File | Action | Phase |
|------|--------|-------|
| pom.xml | Modify | 1 |
| src/main/resources/application.properties | Create | 2 |
| src/main/resources/META-INF/persistence.xml | Delete | 2 |
| src/main/webapp/WEB-INF/web.xml | Delete | 2 |
| src/main/webapp/WEB-INF/beans.xml | Delete | 2 |
| src/main/java/com/redhat/coolstore/service/ProductService.java | Modify | 3 |
| src/main/java/com/redhat/coolstore/service/CatalogService.java | Modify | 3 |
| src/main/java/com/redhat/coolstore/service/OrderService.java | Modify | 3 |
| src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java | Modify | 3,4 |
| src/main/java/com/redhat/coolstore/service/ShoppingCartService.java | Modify | 3 |
| src/main/java/com/redhat/coolstore/service/ShippingService.java | Modify | 3 |
| src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java | Delete | 3 |
| src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java | Modify | 4 |
| src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java | Delete/Modify | 4 |
| src/main/java/com/redhat/coolstore/utils/StartupListener.java | Delete | 5 |
| src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java | Delete/Modify | 5 |
| src/main/java/com/redhat/coolstore/utils/ApplicationLifecycle.java | Create (optional) | 5 |
| src/main/java/weblogic/** | Delete | 6 |
| src/main/java/com/redhat/coolstore/persistence/Resources.java | Delete | 6 |
| src/main/webapp/** | Move+Delete | 6 |
| src/main/resources/META-INF/resources/** | Create | 6 |
| All *.java | Modify (imports) | 3 |

---

## Appendix B: Command Reference

### Build Commands
```bash
# Clean build
mvn clean package

# Dev mode (hot reload)
mvn quarkus:dev

# Run tests
mvn test

# Native build
mvn package -Pnative

# Build container image
mvn package -Dquarkus.container-image.build=true
```

### Run Commands
```bash
# Run JVM mode
java -jar target/quarkus-app/quarkus-run.jar

# Run with profile
java -Dquarkus.profile=prod -jar target/quarkus-app/quarkus-run.jar

# Run native
./target/coolstore-monolith-1.0.0-SNAPSHOT-runner
```

### Development Commands
```bash
# Find imports to replace
grep -r "javax\." src/main/java/ | grep -v jakarta

# Replace imports (Linux/Mac)
find src/main/java -name "*.java" -exec sed -i 's/javax\.inject\./jakarta.inject./g' {} +

# Check for EJB references
grep -r "javax.ejb" src/main/java/

# Check for JMS references
grep -r "javax.jms" src/main/java/
```

### Database Commands
```bash
# Connect to PostgreSQL
psql -h localhost -U coolstore -d coolstore

# Check tables
\dt

# View orders
SELECT * FROM orders;

# View inventory
SELECT * FROM catalog_items;
```

### Docker Commands
```bash
# Start PostgreSQL
docker run --name coolstore-db -e POSTGRES_DB=coolstore -e POSTGRES_USER=coolstore -e POSTGRES_PASSWORD=coolstore -p 5432:5432 -d postgres:14

# Start Keycloak
docker run --name keycloak -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin -p 8081:8080 -d quay.io/keycloak/keycloak:latest start-dev

# Build Quarkus container
docker build -f src/main/docker/Dockerfile.jvm -t coolstore-quarkus .

# Run container
docker run -p 8080:8080 coolstore-quarkus
```

---

*End of Implementation Plan*
