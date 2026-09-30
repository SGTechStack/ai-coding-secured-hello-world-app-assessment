package com.assessment.hello.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Cors cors = new Cors();
    private Admin admin = new Admin();
    private Security security = new Security();

    public Cors getCors() {
        return cors;
    }

    public void setCors(Cors cors) {
        this.cors = cors;
    }

    public Admin getAdmin() {
        return admin;
    }

    public void setAdmin(Admin admin) {
        this.admin = admin;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public static class Cors {
        private String allowedOrigin = "http://localhost:3000";

        public String getAllowedOrigin() {
            return allowedOrigin;
        }

        public void setAllowedOrigin(String allowedOrigin) {
            this.allowedOrigin = allowedOrigin;
        }
    }

    public static class Admin {
        private String username;
        private String password;
        private String email;

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

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    public static class Security {
        private Lockout lockout = new Lockout();
        private IpThrottle ipThrottle = new IpThrottle();
        private ResetToken resetToken = new ResetToken();

        public Lockout getLockout() {
            return lockout;
        }

        public void setLockout(Lockout lockout) {
            this.lockout = lockout;
        }

        public IpThrottle getIpThrottle() {
            return ipThrottle;
        }

        public void setIpThrottle(IpThrottle ipThrottle) {
            this.ipThrottle = ipThrottle;
        }

        public ResetToken getResetToken() {
            return resetToken;
        }

        public void setResetToken(ResetToken resetToken) {
            this.resetToken = resetToken;
        }
    }

    public static class Lockout {
        private int maxAttempts = 5;
        private int cooldownMinutes = 15;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public int getCooldownMinutes() {
            return cooldownMinutes;
        }

        public void setCooldownMinutes(int cooldownMinutes) {
            this.cooldownMinutes = cooldownMinutes;
        }
    }

    public static class IpThrottle {
        private int maxAttempts = 20;
        private int windowMinutes = 15;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public int getWindowMinutes() {
            return windowMinutes;
        }

        public void setWindowMinutes(int windowMinutes) {
            this.windowMinutes = windowMinutes;
        }
    }

    public static class ResetToken {
        private int expiryMinutes = 30;

        public int getExpiryMinutes() {
            return expiryMinutes;
        }

        public void setExpiryMinutes(int expiryMinutes) {
            this.expiryMinutes = expiryMinutes;
        }
    }
}
