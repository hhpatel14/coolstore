# Migration Spec

## Goal
Migrate a Java EE 7 application running on JBoss EAP 7.4 to Quarkus 3.x

## Source → Target
- **Source**: Java EE 7 / JBoss EAP 7.4
- **Target**: Quarkus 3.x

## Scope
- Files affected: ~20 Java source files
- Estimated complexity: Medium
- Hardest areas: 
  1. Message-Driven Beans (JMS topic-based messaging)
  2. Session-scoped REST endpoints with CDI
  3. Container-managed persistence and JNDI datasources

## Key Decisions Applied

Since no questionnaire.json exists, the following decisions are inferred from code analysis and migration best practices:

### Decision 1: Target Framework Version
- **Chosen**: Quarkus 3.x with Jakarta EE 10 APIs
- **Reasoning**: Latest stable Quarkus version provides best performance and full Jakarta EE compatibility

### Decision 2: Messaging Strategy
- **Chosen**: Replace JMS/ActiveMQ with SmallRye Reactive Messaging (in-memory) for single-instance deployments, with path to external broker if needed
- **Reasoning**: The application uses JMS topics (`topic/orders`) for internal messaging. Quarkus doesn't use container-managed JMS. SmallRye Reactive Messaging provides similar functionality with better cloud-native support

### Decision 3: Database Configuration
- **Chosen**: Replace JNDI datasource with Quarkus datasource configuration in application.properties
- **Reasoning**: Quarkus doesn't use JNDI. The application currently uses `java:jboss/datasources/CoolstoreDS` which must be converted to Quarkus datasource properties

### Decision 4: Authentication Strategy
- **Chosen**: Keep Keycloak authentication using Quarkus OIDC extension
- **Reasoning**: Application already uses Keycloak. Quarkus has excellent Keycloak/OIDC support through quarkus-oidc extension

### Decision 5: View Layer Strategy
- **Chosen**: Keep AngularJS frontend as-is, served as static content
- **Reasoning**: The frontend is a separate AngularJS application. No changes needed to the JavaScript - only ensure Quarkus serves static content correctly

### Decision 6: Persistence Strategy
- **Chosen**: Migrate from container-managed EntityManager to Quarkus Hibernate ORM with Panache
- **Reasoning**: Replace `@PersistenceContext` with `@Inject EntityManager` and update persistence.xml to Quarkus format. Optionally adopt Panache for simplified repository pattern

### Decision 7: Deployment Model
- **Chosen**: Single JAR deployment (Quarkus default) replacing WAR
- **Reasoning**: Quarkus uses JAR packaging by default. The application will no longer need an external application server

## Approach

The migration follows these phases:

### Phase 1: Build Configuration
1. Replace Maven WAR plugin with Quarkus Maven plugin
2. Update dependencies from Java EE 7 to Jakarta EE 10 APIs
3. Add Quarkus BOM and core extensions
4. Remove JBoss-specific dependencies

### Phase 2: Persistence Layer
1. Update persistence.xml for Quarkus format
2. Replace container-managed EntityManager producers with Quarkus CDI
3. Update entity classes if needed (minimal changes expected)
4. Configure datasource in application.properties (replace JNDI)

### Phase 3: Business Logic
1. Update service classes for Jakarta namespace changes (javax → jakarta)
2. Migrate Message-Driven Beans to SmallRye Reactive Messaging
3. Update CDI patterns (most will work as-is)
4. Update transaction management if needed

### Phase 4: REST Layer
1. Update JAX-RS Application class (remove extension, add annotation)
2. Update REST endpoints for Jakarta namespace
3. Handle session-scoped endpoints (Quarkus supports @SessionScoped)
4. Update CORS configuration if needed

### Phase 5: Configuration & Resources
1. Create application.properties with all configuration
2. Configure datasource, OIDC, messaging
3. Remove deployment descriptors (web.xml, beans.xml - optional in Quarkus)
4. Configure static resource serving for AngularJS frontend

### Phase 6: Database Migration
1. Update Flyway configuration for Quarkus
2. Ensure migration scripts run correctly
3. Test schema generation

## Domain Skill
**none** - No domain-specific skill detected. Using general Java EE to Quarkus migration knowledge.

## Notes
- The application uses Flyway for database migrations, which Quarkus supports natively
- Session clustering (standalone-full-ha.xml) will need to be addressed through Quarkus Infinispan if clustering is required
- The realm-export.json for Keycloak should work as-is
- WebLogic classes (`weblogic.application.*`) in the codebase appear to be unused/legacy and should be removed
