# Logging conflicts: hashed client IP, lockout logged at WARN

The logging standard (§3.3) forbids logging client IPs, but its own authentication recipes put `source.ip` on every auth event. We log `source.ip_hash`, an HMAC-SHA256 of the IP with a server-side secret. Throttling and password-spraying events from the same source can be linked during an investigation, but no raw IP is stored.

The logging recipe logs account lockout at ERROR. The user standard says WARN, and the logging standard itself reserves ERROR for unrecoverable failures. A lockout is a condition the system handled, so it is logged at WARN with `event.action=ATTEMPTS_EXCEEDED` and `error_code 423`.
