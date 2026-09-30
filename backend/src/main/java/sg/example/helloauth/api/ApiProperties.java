package sg.example.helloauth.api;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * @param basePath the prefix of every API path, {@code /api} by default
 * @param maxRequestBodySize the largest request body accepted; larger ones are a validation error
 * @param trustedProxies the reverse proxies, as IP addresses or CIDR ranges, whose
 *        {@code X-Forwarded-For} names the client. None by default: the client is the peer.
 */
@ConfigurationProperties("app.api")
public record ApiProperties(
        @DefaultValue("/api") String basePath,
        @DefaultValue("16KB") DataSize maxRequestBodySize,
        @DefaultValue List<String> trustedProxies) {

    public String path(String suffix) {
        return basePath + suffix;
    }
}
