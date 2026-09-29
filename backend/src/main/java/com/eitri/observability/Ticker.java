package com.eitri.observability;

/** Monotonic time source used for elapsed-time measurements. */
@FunctionalInterface
public interface Ticker {

    long read();
}
