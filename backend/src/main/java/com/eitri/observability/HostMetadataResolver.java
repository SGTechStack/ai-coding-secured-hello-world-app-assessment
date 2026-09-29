package com.eitri.observability;

/** Injectable host lookup seam for deterministic startup tests. */
@FunctionalInterface
public interface HostMetadataResolver {

    HostMetadata resolve();
}
