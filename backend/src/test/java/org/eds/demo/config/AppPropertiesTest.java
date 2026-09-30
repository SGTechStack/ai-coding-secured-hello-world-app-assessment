package org.eds.demo.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AppPropertiesTest {

  @Test
  void defaults_provideStandardCspPolicyAndSpaLocation() {
    var properties = new AppProperties(null, null);

    assertThat(properties.spa().staticLocation()).isEqualTo("file:./static/");
    assertThat(properties.security().csp().policyDirectives())
        .isEqualTo(AppProperties.DEFAULT_CSP_POLICY)
        .contains("worker-src 'self' blob:;");
  }

  @Test
  void customCspPolicy_overridesDefaultDirectives() {
    var customCsp = "default-src 'self'; script-src 'self' https://trusted.cdn.com;";
    var properties =
        new AppProperties(
            new AppProperties.Spa("file:./custom/"),
            new AppProperties.Security(new AppProperties.Security.Csp(customCsp)));

    assertThat(properties.spa().staticLocation()).isEqualTo("file:./custom/");
    assertThat(properties.security().csp().policyDirectives()).isEqualTo(customCsp);
  }
}
