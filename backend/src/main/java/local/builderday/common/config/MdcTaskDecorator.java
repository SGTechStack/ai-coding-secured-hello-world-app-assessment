package local.builderday.common.config;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;

/**
 * The shared task decorator (backend steering, Logging): work handed to the auto-configured task executor logs with
 * the MDC of the thread that submitted it, e.g. the request's {@code trace.id}. The worker's previous context is
 * restored afterwards, even when the task throws, so a pooled thread never carries one task's context into the next.
 * Boot applies a lone {@link TaskDecorator} bean to its auto-configured executor.
 */
@Component
public class MdcTaskDecorator implements TaskDecorator {
  @Override
  public Runnable decorate(Runnable task) {
    Map<String, String> submitted = MDC.getCopyOfContextMap();
    return () -> {
      Map<String, String> previous = MDC.getCopyOfContextMap();
      apply(submitted);
      try {
        task.run();
      } finally {
        apply(previous);
      }
    };
  }

  private static void apply(Map<String, String> context) {
    if (context == null) MDC.clear();
    else MDC.setContextMap(context);
  }
}
