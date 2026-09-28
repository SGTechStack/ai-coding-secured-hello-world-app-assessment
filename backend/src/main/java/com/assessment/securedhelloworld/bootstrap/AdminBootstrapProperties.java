package com.assessment.securedhelloworld.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Bootstrap admin credential configuration ({@code app.admin.*}),
 * consumed by {@link AdminBootstrapRunner} to seed the initial admin
 * account.
 */
@Component
@ConfigurationProperties(prefix = "app.admin")
public class AdminBootstrapProperties {

    private String username;
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
