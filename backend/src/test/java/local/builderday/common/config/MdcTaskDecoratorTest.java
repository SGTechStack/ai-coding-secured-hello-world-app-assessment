package local.builderday.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcTaskDecoratorTest {
  private final MdcTaskDecorator decorator = new MdcTaskDecorator();

  @AfterEach
  void clear() { MDC.clear(); }

  @Test
  void should_runTheTaskWithTheSubmittersContext_andRestoreTheWorkersAfterwards() {
    MDC.put("trace.id", "request-trace");
    var seen = new AtomicReference<String>();
    Runnable decorated = decorator.decorate(() -> seen.set(MDC.get("trace.id")));

    MDC.clear();
    MDC.put("trace.id", "worker-before");
    decorated.run();

    assertThat(seen.get()).isEqualTo("request-trace");
    assertThat(MDC.get("trace.id")).isEqualTo("worker-before");
  }

  @Test
  void should_runWithAnEmptyContext_when_submittedWithoutOne_evenOnAWorkerThatHasOne() {
    var seen = new AtomicReference<Object>("unset");
    Runnable decorated = decorator.decorate(() -> seen.set(MDC.getCopyOfContextMap()));

    MDC.put("user.id", "worker-before");
    decorated.run();

    assertThat(seen.get()).satisfiesAnyOf(map -> assertThat(map).isNull(),
        map -> assertThat((java.util.Map<?, ?>) map).isEmpty());
    assertThat(MDC.get("user.id")).isEqualTo("worker-before");
  }

  @Test
  void should_restoreAnEmptyWorkerContext_evenWhenTheTaskThrows() {
    MDC.put("trace.id", "request-trace");
    Runnable decorated = decorator.decorate(() -> { throw new IllegalStateException("boom"); });

    MDC.clear();
    assertThatThrownBy(decorated::run).isInstanceOf(IllegalStateException.class);

    assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
  }
}
