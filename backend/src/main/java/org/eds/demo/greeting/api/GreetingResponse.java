package org.eds.demo.greeting.api;

import lombok.Builder;

// spotless:off
@Builder
public record GreetingResponse(
    String message
) {}
// spotless:on
