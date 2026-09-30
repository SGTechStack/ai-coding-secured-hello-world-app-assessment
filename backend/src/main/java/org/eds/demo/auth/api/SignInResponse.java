package org.eds.demo.auth.api;

import lombok.Builder;

// spotless:off
@Builder
public record SignInResponse(
    String username
) {}
// spotless:on
