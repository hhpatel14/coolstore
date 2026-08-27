/**
 * Legacy WebLogic compatibility stub - no longer used in Quarkus
 */
package weblogic.application;

@Deprecated
public abstract class ApplicationLifecycleListener {

    public void postStart(ApplicationLifecycleEvent evt) {
        // No-op - replaced by Quarkus lifecycle events
    }

    public void postStop(ApplicationLifecycleEvent evt) {
        // No-op - replaced by Quarkus lifecycle events
    }

    public void preStart(ApplicationLifecycleEvent evt) {
        // No-op - replaced by Quarkus lifecycle events
    }

    public void preStop(ApplicationLifecycleEvent evt) {
        // No-op - replaced by Quarkus lifecycle events
    }
}
