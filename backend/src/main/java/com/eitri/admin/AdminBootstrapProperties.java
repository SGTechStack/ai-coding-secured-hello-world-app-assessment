package com.eitri.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credentials for the initial admin account, used only while no {@code ADMIN} exists. They come from
 * the environment ({@code ADMIN_USERNAME}, {@code ADMIN_EMAIL}, {@code ADMIN_PASSWORD}); there is no
 * built-in default.
 */
@ConfigurationProperties("app.admin")
record AdminBootstrapProperties(String username, String email, String password) {

    /** Never prints the password. */
    @Override
    public String toString() {
        return "AdminBootstrapProperties[username=" + username + ", email=" + email + ", password=***]";
    }
}
