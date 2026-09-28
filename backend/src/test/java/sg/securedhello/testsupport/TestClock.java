package sg.securedhello.testsupport;

/**
 * Holds the one suite-wide {@link MutableClock} (ADR-066). Every named context replaces the {@code clock} bean with
 * this instance, so time only ever moves forward across the whole sequential run.
 */
public final class TestClock {

    private static final MutableClock SHARED = MutableClock.startingNow();

    private TestClock() {
    }

    /** The shared clock. Also the {@code @TestBean} factory for the {@code clock} bean. */
    public static MutableClock shared() {
        return SHARED;
    }
}
