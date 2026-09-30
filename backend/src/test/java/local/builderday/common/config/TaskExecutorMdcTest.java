package local.builderday.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import local.builderday.support.AuditLogCapture;
import local.builderday.support.TestClocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** The shared task decorator, applied to Boot's auto-configured executor: tasks log with their submitter's context. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestClocks.class)
class TaskExecutorMdcTest {
  private static final String LOGGER = TaskExecutorMdcTest.class.getName();

  @Autowired ThreadPoolTaskExecutor applicationTaskExecutor;

  @AfterEach
  void clear() { MDC.clear(); }

  @Test
  void should_logWithTheSubmittersTraceAndUser_when_aTaskRunsOnTheExecutor() throws Exception {
    try (var log = new AuditLogCapture(LOGGER)) {
      MDC.put("trace.id", "request-trace");
      MDC.put("user.id", "request-user");
      run(() -> LoggerFactory.getLogger(LOGGER).info("in task"));
      MDC.clear();
      // Every worker, whichever ran the first task, starts a task submitted with an empty MDC with an empty MDC.
      for (int i = 0; i < applicationTaskExecutor.getMaxPoolSize() * 2; i++) {
        run(() -> LoggerFactory.getLogger(LOGGER).info("clean task"));
      }

      var events = log.events();
      assertThat(events.getFirst().getMDCPropertyMap())
          .containsAllEntriesOf(Map.of("trace.id", "request-trace", "user.id", "request-user"));
      assertThat(events.subList(1, events.size())).allSatisfy(event ->
          assertThat(event.getMDCPropertyMap()).doesNotContainKeys("trace.id", "user.id"));
    }
  }

  @Test
  void should_leaveLaterTasksClean_when_aTaskThrows() throws Exception {
    MDC.put("user.id", "request-user");
    var failed = applicationTaskExecutor.submit(() -> { throw new IllegalStateException("boom"); });
    assertThatThrownBy(() -> failed.get(10, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
    MDC.clear();

    try (var log = new AuditLogCapture(LOGGER)) {
      for (int i = 0; i < applicationTaskExecutor.getMaxPoolSize() * 2; i++) {
        run(() -> LoggerFactory.getLogger(LOGGER).info("after failure"));
      }
      assertThat(log.events()).allSatisfy(event -> assertThat(event.getMDCPropertyMap()).doesNotContainKey("user.id"));
    }
  }

  private void run(Runnable task) throws Exception {
    applicationTaskExecutor.submit(task).get(10, TimeUnit.SECONDS);
  }
}
