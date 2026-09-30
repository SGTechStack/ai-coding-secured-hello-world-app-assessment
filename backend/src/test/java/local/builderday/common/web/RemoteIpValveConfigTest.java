package local.builderday.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.apache.catalina.valves.RemoteIpValve;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.context.WebServerApplicationContext;

/**
 * Client IP comes from Tomcat's RemoteIpValve with its default private-range internal proxies, which cover the ALB.
 * The cloud security group admits only the ALB, so no other caller can reach the application to forge the header.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class RemoteIpValveConfigTest {
  @Autowired WebServerApplicationContext context;

  @Test
  void should_installRemoteIpValveTrustingPrivateRangeProxies_when_usingTomcatDefaults() {
    var tomcat = ((TomcatWebServer) context.getWebServer()).getTomcat();
    var valve = Arrays.stream(tomcat.getEngine().getPipeline().getValves())
        .filter(RemoteIpValve.class::isInstance).map(RemoteIpValve.class::cast).findFirst();

    assertThat(valve).hasValueSatisfying(remoteIp -> {
      assertThat(remoteIp.getInternalProxies()).contains("10.0.0.0/8"); // VPC addresses, including the ALB
      assertThat(remoteIp.getTrustedProxies()).isNull();
      assertThat(remoteIp.getRemoteIpHeader()).isEqualToIgnoringCase("X-Forwarded-For");
    });
  }
}
