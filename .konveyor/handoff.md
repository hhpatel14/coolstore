# Migration Handoff

## Request
Migrate this Java EE application to Quarkus

## Status: In_progress

## Summary
- 33 of 35 items migrated successfully
- Build passes, 0 tests passing
- 2 item(s) skipped

## What Was Done
1. [x] pom.xml — migrate
2. [x] src/main/resources/application.properties — create
3. [x] src/main/java/com/redhat/coolstore/persistence/Resources.java — migrate
4. [x] src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java — migrate
5. [x] src/main/java/com/redhat/coolstore/model/InventoryEntity.java — migrate
6. [x] src/main/java/com/redhat/coolstore/model/Order.java — migrate
7. [x] src/main/java/com/redhat/coolstore/model/OrderItem.java — migrate
8. [x] src/main/java/com/redhat/coolstore/model/Product.java — migrate
9. [ ] src/main/java/com/redhat/coolstore/model/Promotion.java — migrate (skipped)
10. [x] src/main/java/com/redhat/coolstore/model/ShoppingCart.java — migrate
11. [ ] src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java — migrate (skipped)
12. [x] src/main/java/com/redhat/coolstore/utils/Transformers.java — migrate
13. [x] src/main/java/com/redhat/coolstore/utils/Producers.java — migrate
14. [x] src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java — delete
15. [x] src/main/java/com/redhat/coolstore/utils/StartupListener.java — migrate
16. [x] src/main/java/com/redhat/coolstore/service/CatalogService.java — migrate
17. [x] src/main/java/com/redhat/coolstore/service/ProductService.java — migrate
18. [x] src/main/java/com/redhat/coolstore/service/PromoService.java — migrate
19. [x] src/main/java/com/redhat/coolstore/service/ShippingService.java — migrate
20. [x] src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java — migrate
21. [x] src/main/java/com/redhat/coolstore/service/OrderService.java — migrate
22. [x] src/main/java/com/redhat/coolstore/service/ShoppingCartService.java — migrate
23. [x] src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java — migrate
24. [x] src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java — migrate
25. [x] src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java — migrate
26. [x] src/main/java/com/redhat/coolstore/rest/RestApplication.java — migrate
27. [x] src/main/java/com/redhat/coolstore/rest/CartEndpoint.java — migrate
28. [x] src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java — migrate
29. [x] src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java — migrate
30. [x] src/main/java/weblogic/application/ApplicationLifecycleEvent.java — delete
31. [x] src/main/java/weblogic/application/ApplicationLifecycleListener.java — delete
32. [x] src/main/java/weblogic/i18n/logging/NonCatalogLogger.java — delete
33. [x] src/main/resources/META-INF/persistence.xml — delete
34. [x] src/main/webapp/WEB-INF/beans.xml — delete
35. [x] src/main/webapp/WEB-INF/web.xml — delete

## What Needs Manual Attention
- src/main/java/com/redhat/coolstore/model/Promotion.java — skipped
- src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java — skipped

## Verification
- Build: passing
- Tests: 0 passed, 0 failed

