package com.example.helloauth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Admin admin = new Admin();
    private final Lockout lockout = new Lockout();
    private final Throttle throttle = new Throttle();
    private final Reset reset = new Reset();
    private Cors cors = new Cors();

    public Admin getAdmin() { return admin; }
    public Lockout getLockout() { return lockout; }
    public Throttle getThrottle() { return throttle; }
    public Reset getReset() { return reset; }
    public Cors getCors() { return cors; }
    public void setCors(Cors cors) { this.cors = cors; }

    public static class Admin {
        /** Seed admin credentials, sourced from config/env (never committed plaintext). IM8 as-8. */
        private String username;
        private String email;
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class Lockout {
        /** Consecutive failures that trigger a lockout. */
        private int maxAttempts = 5;
        /** Cooldown duration in minutes. */
        private long cooldownMinutes = 15;

        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public long getCooldownMinutes() { return cooldownMinutes; }
        public void setCooldownMinutes(long cooldownMinutes) { this.cooldownMinutes = cooldownMinutes; }
    }

    public static class Throttle {
        /** Max failed attempts per IP within the window before throttling. */
        private int maxPerIp = 15;
        /** Sliding window in minutes for per-IP failure counting. */
        private long windowMinutes = 15;

        public int getMaxPerIp() { return maxPerIp; }
        public void setMaxPerIp(int maxPerIp) { this.maxPerIp = maxPerIp; }
        public long getWindowMinutes() { return windowMinutes; }
        public void setWindowMinutes(long windowMinutes) { this.windowMinutes = windowMinutes; }
    }

    public static class Reset {
        /** Reset token validity in minutes (15-30 per spec). */
        private long tokenTtlMinutes = 30;

        public long getTokenTtlMinutes() { return tokenTtlMinutes; }
        public void setTokenTtlMinutes(long tokenTtlMinutes) { this.tokenTtlMinutes = tokenTtlMinutes; }
    }

    public static class Cors {
        /** Explicit allow-list of frontend origins. IM8 CORS requirement. */
        private java.util.List<String> allowedOrigins = java.util.List.of("http://localhost:3000");

        public java.util.List<String> getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(java.util.List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }
    }
}
