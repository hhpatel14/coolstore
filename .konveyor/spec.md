# Migration Specification: Java EE 7 to Quarkus 3

## Executive Summary

This specification outlines the migration of the Coolstore Monolith application from a Java EE 7 WebLogic/JBoss WAR deployment to a standalone Quarkus 3 JAR application. The migration will replace traditional Java EE patterns (EJBs, MDBs, JNDI) with modern Quarkus equivalents while preserving all business functionality.

**Source:** Java EE 7 WAR application (WebLogic/WildFly)  
**Target:** Quarkus 3 standalone JAR application  
**Language:** Java  
**Build Tool:** Maven  
**Estimated Complexity:** Medium-High

## Current Architecture

### Technology Stack
- **Runtime:** Java EE 7 (WebLogic/JBoss/WildFly application server)
- **Packaging:** WAR (Web Application Archive)
- **Dependency Injection:** CDI 1.1 + EJB 3.2
- **Persistence:** JPA 2.1 with Hibernate
- **REST:** JAX-RS 2.0
- **Messaging:** JMS 2.0 with Message-Driven Beans
- **Configuration:** persistence.xml, web.xml, beans.xml

### Application Structure
- **30 Java source files** across 5 packages:
  - `model/` (8 JPA entities)
  - `service/` (10 services: 4 EJBs, 2 MDBs, 4 plain CDI beans)
  - `rest/` (4 JAX-RS endpoints)
  - `persistence/` (1 resource producer)
  - `utils/` (4 utility classes)
- **2 WebLogic stub files** for lifecycle integration
- **3 configuration files** (persistence.xml, web.xml, beans.xml)

### Key Java EE Patterns Identified

1. **EJB Session Beans**
   - `@Stateless`: CatalogService, ShippingService, OrderService
   - `@Stateful`: ShoppingCartService
   - `@Remote`: ShippingService (with remote interface)

2. **Message-Driven Beans (MDB)**
   - `OrderServiceMDB`: @MessageDriven with activation config
   - `InventoryNotificationMDB`: Manual JMS MessageListener implementation

3. **JNDI Lookups**
   - ShippingService remote EJB lookup in ShoppingCartService
   - JMS ConnectionFactory and Topic lookups in InventoryNotificationMDB
   - Hardcoded WebLogic/WildFly JNDI properties

4. **Application Server Lifecycle**
   - `StartupListener`: Extends WebLogic ApplicationLifecycleListener
   - Custom stub classes in `weblogic.application` package

5. **Persistence**
   - JPA entities with standard annotations (@Entity, @Table, @OneToOne)
   - EntityManager injection via CDI
   - JNDI datasource: `java:jboss/datasources/CoolstoreDS`

6. **Session Management**
   - `CartEndpoint`: @SessionScoped REST resource (non-standard pattern)

## Target Architecture

### Technology Stack
- **Runtime:** Quarkus 3 standalone
- **Packaging:** JAR (executable uber-jar or fast-jar)
- **Dependency Injection:** CDI 4.0 (Arc)
- **Persistence:** Hibernate ORM with Panache (optional)
- **REST:** RESTEasy Reactive (JAX-RS compatible)
- **Messaging:** SmallRye Reactive Messaging (Kafka or AMQP)
- **Configuration:** application.properties

### Key Transformations

1. **Build Configuration**
   - WAR → JAR packaging
   - Add Quarkus BOM and Maven plugin
   - Replace Java EE dependencies with Quarkus extensions
   - Target Java 17+ (Quarkus 3 minimum)

2. **EJB → CDI Beans**
   - Remove @Stateless, @Stateful annotations
   - Add @ApplicationScoped where needed
   - Remove @Remote interfaces
   - Replace JNDI lookups with direct CDI @Inject

3. **MDB → SmallRye Reactive Messaging**
   - Replace @MessageDriven with @Incoming channels
   - Convert manual MessageListener to reactive @Incoming methods
   - Configure messaging backend (Kafka/AMQP) in application.properties

4. **Configuration Migration**
   - persistence.xml → application.properties (datasource, Hibernate settings)
   - Remove web.xml and beans.xml (not needed in Quarkus)
   - JNDI datasource → Quarkus datasource configuration

5. **Lifecycle Hooks**
   - Remove WebLogic ApplicationLifecycleListener
   - Replace with Quarkus @Observes StartupEvent/ShutdownEvent

6. **Session State**
   - Review @SessionScoped usage in CartEndpoint
   - Consider stateless alternatives or Quarkus session management

## Migration Phases

The migration will follow the domain skill's phase order:

### Phase 1: Build Configuration
Transform pom.xml from Java EE WAR to Quarkus JAR project.

**Files Modified:** 1
- `pom.xml`

**Key Changes:**
- Packaging: WAR → JAR
- Add Quarkus BOM (io.quarkus.platform:quarkus-bom:3.x)
- Add Quarkus Maven plugin (io.quarkus:quarkus-maven-plugin)
- Replace javaee-web-api/javaee-api with Quarkus extensions:
  - quarkus-resteasy-reactive-jackson
  - quarkus-hibernate-orm
  - quarkus-jdbc-h2 (or PostgreSQL/MySQL as appropriate)
  - quarkus-smallrye-reactive-messaging
  - quarkus-arc (CDI)
- Update compiler source/target to Java 17
- Remove maven-war-plugin

### Phase 2: Application Configuration
Migrate XML configuration to Quarkus application.properties.

**Files Modified:** 1 created, 2 deleted
- Create `src/main/resources/application.properties`
- Delete `src/main/resources/META-INF/persistence.xml`
- Delete `src/main/webapp/WEB-INF/web.xml`
- Keep beans.xml (optional, for CDI discovery)

**Key Changes:**
- Datasource configuration from JNDI to Quarkus format
- Hibernate properties migration
- JMS/messaging configuration (if applicable)

### Phase 3: EJB to CDI Conversion
Replace EJB programming model with CDI beans.

**Files Modified:** 6
- `service/CatalogService.java` (@Stateless → @ApplicationScoped)
- `service/ShippingService.java` (@Stateless @Remote → @ApplicationScoped)
- `service/OrderService.java` (@Stateless → @ApplicationScoped)
- `service/ShoppingCartService.java` (@Stateful → @ApplicationScoped or @RequestScoped)
- `service/ShoppingCartService.java` (remove JNDI lookup of ShippingService)
- Delete `service/ShippingServiceRemote.java` (no longer needed)

**Key Changes:**
- Remove javax.ejb.* imports
- Add javax.enterprise.context.* imports (or jakarta.*)
- Remove @Stateless, @Stateful, @Remote annotations
- Add appropriate CDI scope annotations
- Replace JNDI lookups with @Inject

### Phase 4: Messaging Migration
Convert JMS Message-Driven Beans to SmallRye Reactive Messaging.

**Files Modified:** 2
- `service/OrderServiceMDB.java`
- `service/InventoryNotificationMDB.java`

**Key Changes:**
- Remove @MessageDriven and activation config
- Remove MessageListener interface
- Add @Incoming("orders") annotation
- Convert onMessage(Message) to reactive method signature
- Remove manual JNDI lookups and connection management
- Configure messaging channels in application.properties

### Phase 5: Lifecycle Migration
Replace WebLogic lifecycle hooks with Quarkus startup/shutdown events.

**Files Modified:** 1, Files Deleted: 2
- `utils/StartupListener.java` (rewrite with Quarkus events)
- Delete `weblogic/application/ApplicationLifecycleListener.java`
- Delete `weblogic/application/ApplicationLifecycleEvent.java`

**Key Changes:**
- Remove ApplicationLifecycleListener inheritance
- Add @Observes StartupEvent and ShutdownEvent methods
- Remove weblogic.* package entirely

### Phase 6: Cleanup and Validation
Remove legacy files and verify migration completeness.

**Files Deleted:** 3+
- `src/main/webapp/WEB-INF/` directory (web.xml, beans.xml if not needed)
- Any remaining javax.ejb.* imports
- WebLogic/JBoss-specific configuration

**Validation:**
- Run `mvn clean compile` after each phase
- Verify no javax.ejb.*, javax.jms.* (old API) imports remain
- Ensure all JNDI lookups removed
- Check for hardcoded server-specific values

## Risk Assessment

### High Risk Items
1. **@Stateful EJB conversion** (ShoppingCartService)
   - Current: Server-managed conversational state
   - Risk: Session state management differs in Quarkus
   - Mitigation: Review session handling, consider @RequestScoped or external session store

2. **Remote EJB elimination** (ShippingService)
   - Current: Remote interface with JNDI lookup
   - Risk: Breaking service boundaries
   - Mitigation: Direct injection works since this is a monolith; for microservices, consider REST client

3. **JMS to Reactive Messaging**
   - Current: Topic-based pub/sub with multiple consumers
   - Risk: Messaging semantics may differ (Kafka vs JMS)
   - Mitigation: Carefully map JMS Topic to Kafka topics or AMQP exchanges

### Medium Risk Items
1. **@SessionScoped REST endpoint** (CartEndpoint)
   - Risk: CDI session scope behavior may differ
   - Mitigation: Test thoroughly, consider alternatives

2. **Manual JNDI context creation** (InventoryNotificationMDB)
   - Risk: Complex cleanup logic
   - Mitigation: Reactive Messaging handles lifecycle automatically

### Low Risk Items
1. JPA entities (already standard JPA)
2. JAX-RS endpoints (Quarkus is JAX-RS compatible)
3. Standard CDI injection (compatible across platforms)

## Testing Strategy

1. **Phase-by-phase validation:** Run `mvn clean compile` after each phase
2. **Integration testing:** Verify REST endpoints work after Phase 3
3. **Messaging testing:** Verify message consumption after Phase 4
4. **End-to-end testing:** Full application smoke test after Phase 6

## Success Criteria

- [ ] Application compiles with `mvn clean compile`
- [ ] No Java EE server dependencies (javax.ejb, weblogic.*)
- [ ] All REST endpoints functional
- [ ] Message processing working (async order processing)
- [ ] Database persistence operational
- [ ] Application starts with `mvn quarkus:dev`
- [ ] WAR packaging converted to JAR
- [ ] No JNDI lookups remaining

## Open Questions

1. **Messaging Backend:** Should we use Kafka, AMQP (RabbitMQ), or in-memory for reactive messaging?
   - Default recommendation: Start with in-memory for development, Kafka for production

2. **Database:** Current JNDI datasource suggests external DB. Which database (PostgreSQL, MySQL, H2)?
   - Recommendation: Use H2 for dev, match production DB in properties

3. **Session Management:** How critical is session state in CartEndpoint?
   - Recommendation: Evaluate if stateless design is feasible

4. **Deployment Target:** Kubernetes, bare metal, or development only?
   - Affects packaging choice (uber-jar vs fast-jar)

## Estimated Effort

- **Phase 1 (Build Config):** 30 minutes
- **Phase 2 (App Config):** 30 minutes
- **Phase 3 (EJB to CDI):** 1.5 hours
- **Phase 4 (Messaging):** 2 hours
- **Phase 5 (Lifecycle):** 30 minutes
- **Phase 6 (Cleanup):** 30 minutes
- **Testing & Validation:** 1 hour

**Total Estimated Time:** 6-7 hours

## Next Steps

Upon approval of this specification:
1. Generate detailed implementation plan (.konveyor/implementation.md)
2. Execute migration phase-by-phase
3. Test and validate after each phase
4. Document any deviations or issues encountered
