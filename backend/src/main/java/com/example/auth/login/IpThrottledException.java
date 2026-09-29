package com.example.auth.login;

/** Thrown when a source IP has exceeded the failed-login rate limit. */
public class IpThrottledException extends RuntimeException {

    public IpThrottledException(String ip) {
        super("Too many failed login attempts from IP: " + ip);
    }
}
