package sg.securedhello.testsupport;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import tools.jackson.databind.json.JsonMapper;

import sg.securedhello.SecuredHelloApplication;

/**
 * {@code runner}: starts the application jar's main class in a JVM of its own, as an operator would start the recovery
 * runner (level R). Each process gets a database file and an audit directory the test owns, the {@link TestSecrets}
 * through {@code SPRING_APPLICATION_JSON}, and none of the parent's {@code APP_*} variables, so a developer's shell
 * never decides a result. Its streams go to files, never to the test's own output.
 */
public final class RunnerProcess {

    /** Longer than any start the suite makes; a process still running then has hung, and fails the test. */
    private static final Duration TIMEOUT = Duration.ofMinutes(2);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Path database;
    private final Path auditDirectory;
    private final Path work;
    private final Map<String, String> environment = new LinkedHashMap<>();
    private final Map<String, Object> properties = TestSecrets.properties();

    private RunnerProcess(Path database, Path auditDirectory, Path work) {
        this.database = database;
        this.auditDirectory = auditDirectory;
        this.work = work;
    }

    /**
     * A process on the H2 database at {@code database} (the path without {@code .mv.db}), writing its audit file under
     * {@code auditDirectory}, with its output files in {@code work}.
     */
    public static RunnerProcess on(Path database, Path auditDirectory, Path work) {
        return new RunnerProcess(database, auditDirectory, work);
    }

    /** The JDBC URL of an H2 file database at {@code database}, as production spells it. */
    public static String url(Path database) {
        return "jdbc:h2:file:" + database.toString().replace('\\', '/') + ";LOCK_TIMEOUT=1000";
    }

    /** Adds an environment variable to the process. */
    public RunnerProcess env(String name, String value) {
        environment.put(name, value);
        return this;
    }

    /** Sets an application property for the process, replacing the harness's value. */
    public RunnerProcess property(String name, Object value) {
        properties.put(name, value);
        return this;
    }

    /** One finished process: its exit status and everything it wrote. */
    public record Result(int exitCode, String stdout, String stderr) {
    }

    /** Runs the main class with {@code args}, writes {@code stdin} and closes it, and waits for the exit. */
    public Result run(String stdin, String... args) {
        Started started = start(stdin, args);
        try {
            if (!started.process().waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                started.process().destroyForcibly();
                throw new AssertionError("the process did not exit within " + TIMEOUT);
            }
            return new Result(started.process().exitValue(), started.stdout(), started.stderr());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    /**
     * Runs the main class with {@code args} until its stdout satisfies {@code ready}, then stops it; for a start that
     * would otherwise keep running, such as the application itself.
     */
    public Result runUntil(Predicate<String> ready, String stdin, String... args) {
        Started started = start(stdin, args);
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        try {
            while (!ready.test(started.stdout())) {
                if (!started.process().isAlive() || System.nanoTime() > deadline) {
                    throw new AssertionError("the process exited or timed out before it was ready:\n"
                            + started.stdout() + started.stderr());
                }
                started.process().waitFor(200, TimeUnit.MILLISECONDS);
            }
            started.process().destroy();
            started.process().waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            awaitDatabaseReleased();
            return new Result(started.process().exitValue(), started.stdout(), started.stderr());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        } finally {
            started.process().destroyForcibly();
        }
    }

    /**
     * Waits until the stopped process's lock on the database file is gone: Windows releases a killed process's file
     * lock a moment after the process has exited.
     */
    private void awaitDatabaseReleased() throws InterruptedException {
        Path file = Path.of(database + ".mv.db");
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE);
                    FileLock lock = channel.tryLock()) {
                if (lock != null) {
                    return;
                }
            } catch (IOException | OverlappingFileLockException stillHeld) {
                // Held by the exiting process; try again shortly.
            }
            new CountDownLatch(1).await(200, TimeUnit.MILLISECONDS);
        }
        throw new AssertionError("the database file stayed locked after the process exited");
    }

    private record Started(Process process, Path out, Path err) {

        String stdout() {
            return read(out);
        }

        String stderr() {
            return read(err);
        }

        private static String read(Path file) {
            try {
                return Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private Started start(String stdin, String... args) {
        try {
            Files.createDirectories(work);
            Path out = Files.createTempFile(work, "stdout-", ".log");
            Path err = Files.createTempFile(work, "stderr-", ".log");
            List<String> command = new ArrayList<>(List.of(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                    "-cp", System.getProperty("java.class.path"),
                    SecuredHelloApplication.class.getName()));
            command.addAll(List.of(args));
            ProcessBuilder builder = new ProcessBuilder(command).directory(work.toFile())
                    .redirectOutput(out.toFile()).redirectError(err.toFile());
            Map<String, String> env = builder.environment();
            env.keySet().removeIf(name -> name.startsWith("APP_") || name.startsWith("SPRING_"));
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("spring.datasource.url", url(database));
            json.put("app.db.data-dir", database.toAbsolutePath().getParent().toString());
            json.put("app.audit.directory", auditDirectory.toString());
            json.putAll(properties);
            env.put("SPRING_APPLICATION_JSON", JSON.writeValueAsString(json));
            env.putAll(environment);
            Process process = builder.start();
            try (OutputStream in = process.getOutputStream()) {
                in.write(stdin.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // The process may exit before reading its stdin; that is its answer, not a harness failure.
            }
            return new Started(process, out, err);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
