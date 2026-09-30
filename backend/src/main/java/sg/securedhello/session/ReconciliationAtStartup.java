package sg.securedhello.session;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

/**
 * Runs the reconciliation sweep once at startup, before traffic is served (ADR-039). Singletons are initialised,
 * Flyway included, before the web server starts, so no request can reach a session the sweep is about to end. A
 * failed sweep stops startup.
 *
 * <p>Web only: the offline recovery runner serves no traffic and changes only what its digest previewed (ADR-072;
 * ADR-074), so it neither sweeps nor lets a failed sweep block a recovery. The application sweeps when it restarts
 * after the outage, before its port opens.
 */
@Component
@ConditionalOnWebApplication
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
