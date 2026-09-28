package sg.securedhello.security.source;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * The test JVM's name-resolution provider (installed through {@code META-INF/services}). It delegates to the built-in
 * resolver, except while {@link #armed} on the current thread: then every lookup is counted and fails.
 */
public final class DnsTrap extends InetAddressResolverProvider {

    private static final ThreadLocal<Boolean> ARMED = ThreadLocal.withInitial(() -> false);
    private static final AtomicInteger TRIPPED = new AtomicInteger();

    /** Runs {@code action} with the trap armed and returns how many lookups it attempted. */
    static int lookupsDuring(Runnable action) {
        int before = TRIPPED.get();
        ARMED.set(true);
        try {
            action.run();
        } finally {
            ARMED.set(false);
        }
        return TRIPPED.get() - before;
    }

    @Override
    public InetAddressResolver get(Configuration configuration) {
        InetAddressResolver builtin = configuration.builtinResolver();
        return new InetAddressResolver() {
            @Override
            public Stream<InetAddress> lookupByName(String host, LookupPolicy policy) throws UnknownHostException {
                trip(host);
                return builtin.lookupByName(host, policy);
            }

            @Override
            public String lookupByAddress(byte[] address) throws UnknownHostException {
                trip("reverse lookup");
                return builtin.lookupByAddress(address);
            }
        };
    }

    private static void trip(String what) throws UnknownHostException {
        if (ARMED.get()) {
            TRIPPED.incrementAndGet();
            throw new UnknownHostException("DNS trap: " + what);
        }
    }

    @Override
    public String name() {
        return "dns-trap";
    }
}
