package sg.securedhello.session;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Runs the reconciliation sweep once at startup, before traffic is served (ADR-039). Singletons are initialised,
 * Flyway included, before the web server starts, so no request can reach a session the sweep is about to end. A
 * failed sweep stops startup.
 */
@Component
class ReconciliationAtStartup implements SmartInitializingSingleton {

    private final SessionTerminationService sessions;

    ReconciliationAtStartup(SessionTerminationService sessions) {
        this.sessions = sessions;
    }

    @Override
    public void afterSingletonsInstantiated() {
        sessions.reconcile();
    }
}
