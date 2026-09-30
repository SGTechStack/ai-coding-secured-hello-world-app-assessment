package sg.example.helloauth.support;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

import org.springframework.core.task.TaskDecorator;

/**
 * Stands in for the application's task executor: work handed to it waits until the test runs
 * it, so a test can see what a request did before its background work, and then after it. Like
 * Boot's executor, it applies the application's {@link TaskDecorator} as the work is handed over.
 */
public final class BackgroundTasks implements Executor {

    private final TaskDecorator decorator;
    private final Queue<Runnable> pending = new ConcurrentLinkedQueue<>();

    BackgroundTasks(TaskDecorator decorator) {
        this.decorator = decorator;
    }

    @Override
    public void execute(Runnable task) {
        pending.add(decorator.decorate(task));
    }

    /** Runs every waiting task, including any that those tasks hand over in turn. */
    public void runAll() {
        for (Runnable task = pending.poll(); task != null; task = pending.poll()) {
            task.run();
        }
    }

    public boolean isIdle() {
        return pending.isEmpty();
    }

    void clear() {
        pending.clear();
    }
}
