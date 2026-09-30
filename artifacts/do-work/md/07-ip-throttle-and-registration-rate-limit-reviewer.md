## Findings

| Finding | Required action | Status |
| --- | --- | --- |
| KB unavailable. The acceptance criterion says the IP Throttle uses the direct client address and ignores forwarded headers. server.forward-headers-strategy is not set. When it is unset, Spring Boot 4.1.1 switches to NATIVE on any detected cloud platform: CloudPlatform.isUsingForwardHeaders() returns true for Kubernetes, Cloud Foundry, Heroku, Azure App Service, Nomad and SAP. Kubernetes is detected from KUBERNETES_SERVICE_HOST/PORT. Tomcat's RemoteIpValve then rewrites getRemoteAddr() from X-Forwarded-For when the request arrives from an internal-range address. That changes the IP Throttle key, the registration limiter key and source.ip_hash, depending on where the app runs. forwardedHeadersAreIgnored uses MockMvc, which never runs Tomcat valves, so it cannot catch this.<br><br><details><summary><strong>Verify in</strong></summary><br><code>application.properties:50-55 (app.ip-throttle.*</code><br><code>server.forward-headers-strategy unset)</code></details> | Set server.forward-headers-strategy=none explicitly in application.properties, next to the existing reverse-proxy comment. Operators who add a trusted proxy then change it on purpose, as the spec's deployment note describes. Add a bootstrap assertion in StartupConfigurationTest that the prod profile resolves server.forward-headers-strategy to none, even when KUBERNETES_SERVICE_HOST/PORT or spring.main.cloud-platform=kubernetes is set. | Resolved |


## Full do-work log

Open locally: `artifacts/do-work/07-ip-throttle-and-registration-rate-limit.html`

Includes reviewer findings and human decisions.
