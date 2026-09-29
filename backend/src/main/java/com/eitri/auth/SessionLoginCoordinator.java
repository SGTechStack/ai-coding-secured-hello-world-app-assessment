package com.eitri.auth;

import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** Serializes session replacement for a bounded set of account stripes in this single app instance. */
@Component
final class SessionLoginCoordinator {

    private static final int STRIPE_COUNT = 64;

    private final ReentrantLock[] stripes = new ReentrantLock[STRIPE_COUNT];

    SessionLoginCoordinator() {
        for (int index = 0; index < stripes.length; index++) {
            stripes[index] = new ReentrantLock();
        }
    }

    <T> T coordinate(UUID accountId, Supplier<T> action) {
        ReentrantLock lock = stripes[Math.floorMod(accountId.hashCode(), stripes.length)];
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
