package sg.example.helloauth.security;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** @param allowedOrigins the exact frontend origins allowed to make credentialed calls; never a wildcard */
@ConfigurationProperties("app.cors")
public record CorsProperties(@DefaultValue List<String> allowedOrigins) {

    public CorsProperties {
        if (allowedOrigins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalArgumentException("app.cors.allowed-origins must list exact origins, not a wildcard");
        }
        allowedOrigins = List.copyOf(allowedOrigins);
    }
}
