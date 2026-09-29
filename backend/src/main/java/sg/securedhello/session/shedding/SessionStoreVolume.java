package sg.securedhello.session.shedding;

import java.io.IOException;

/** The volume holding the H2 database file, which sessions, accounts and audit data share (ADR-041). */
@FunctionalInterface
public interface SessionStoreVolume {

    /** The bytes a new write can use now. */
    long usableBytes() throws IOException;
}
