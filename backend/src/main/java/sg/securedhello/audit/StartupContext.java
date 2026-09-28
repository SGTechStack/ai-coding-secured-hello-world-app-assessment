package sg.securedhello.audit;

import java.util.List;

/**
 * The context of the startup row (row 43): what later correlation and review depend on, recorded once. The host is
 * the server's own; no client address is involved.
 *
 * @param hostName          {@code host.name}
 * @param hostIps           {@code host.ip}
 * @param activeProfiles    the active profiles, or {@code default}
 * @param ipv6PrefixLength  the effective IPv6 source-key prefix length (T-CFG-037)
 * @param keyFingerprints   {@code <property>=<fingerprint>} per application key, never the key
 * @param auditLoggers      {@code <logger>=<effective level>} per audit-relevant logger (ADR-057)
 */
record StartupContext(String hostName, List<String> hostIps, List<String> activeProfiles, int ipv6PrefixLength,
        List<String> keyFingerprints, List<String> auditLoggers) implements AuditContext {

    @Override
    public void writeTo(AuditFields fields) {
        fields.put(AuditKey.HOST_NAME, hostName)
                .put(AuditKey.HOST_IP, hostIps)
                .put(AuditKey.ACTIVE_PROFILES, activeProfiles)
                .put(AuditKey.IPV6_PREFIX_LENGTH, ipv6PrefixLength)
                .put(AuditKey.KEY_FINGERPRINTS, keyFingerprints)
                .put(AuditKey.AUDIT_LOGGERS, auditLoggers);
    }
}
