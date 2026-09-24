package com.sgtechstack.helloworldauthapp.admin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Structural regression test for the invariant {@link RoleMutationGuard}
 * exists to make explicit: {@code User.setRole(...)} must have exactly one
 * caller in production source — {@link AdminUserManagementService#changeRole}
 * — so role mutation cannot be reached by any path that bypasses the
 * guard's audit record.
 *
 * This scans source text rather than bytecode (no ArchUnit dependency in
 * this project, and adding one for a single invariant would be
 * disproportionate for an app this size). A source scan is a deliberately
 * blunt tool: it would also flag a caller added inside a comment or string
 * literal as a false positive, which is an acceptable trade-off for a
 * regression guard that otherwise requires zero new dependencies.
 */
class RoleMutationInvariantTest {

    private static final Pattern SET_ROLE_CALL = Pattern.compile("\\bsetRole\\(");

    @Test
    void userSetRoleHasExactlyOneCallerInProductionSource() throws IOException {
        Path mainSourceRoot = mainJavaSourceRoot();

        List<Path> callers;
        try (Stream<Path> files = Files.walk(mainSourceRoot)) {
            callers = files
                    .filter(path -> path.toString().endsWith(".java"))
                    // The declaration itself ("public void setRole(Role role)")
                    // is not a call site; exclude the file that declares it.
                    // RoleMutationGuard's own Javadoc mentions "setRole(...)"
                    // in prose while documenting this exact invariant, which
                    // is not a call site either.
                    .filter(path -> !path.endsWith("User.java"))
                    .filter(path -> !path.endsWith("RoleMutationGuard.java"))
                    .filter(this::containsSetRoleCall)
                    .toList();
        }

        assertThat(callers)
                .as("files outside User.java calling setRole(...) — must be exactly "
                        + "AdminUserManagementService.java, routed through RoleMutationGuard")
                .extracting(path -> path.getFileName().toString())
                .containsExactly("AdminUserManagementService.java");
    }

    private boolean containsSetRoleCall(Path path) {
        try {
            return SET_ROLE_CALL.matcher(Files.readString(path)).find();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path mainJavaSourceRoot() {
        Path path = Paths.get("src", "main", "java");
        if (!Files.isDirectory(path)) {
            throw new IllegalStateException(
                    "Expected to find " + path.toAbsolutePath() + " — test must run with the backend module as the "
                            + "working directory");
        }
        return path;
    }
}
