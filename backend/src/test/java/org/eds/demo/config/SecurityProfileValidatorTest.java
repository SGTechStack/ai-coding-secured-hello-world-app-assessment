package org.eds.demo.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.env.Environment;

public class SecurityProfileValidatorTest {

  @Test
  void throwsWhenProdLikeAndUnsafe() {
    ApplicationContext ctx = mock(ApplicationContext.class);
    Environment env = mock(Environment.class);
    when(ctx.getEnvironment()).thenReturn(env);
    when(env.getActiveProfiles()).thenReturn(new String[] {"prod", "unsafe-foo"});

    ContextRefreshedEvent event = new ContextRefreshedEvent(ctx);
    SecurityProfileValidator validator = new SecurityProfileValidator();

    assertThrows(RuntimeException.class, () -> validator.validate(event));
  }

  @Test
  void doesNotThrowWhenProdLikeWithoutUnsafe() {
    ApplicationContext ctx = mock(ApplicationContext.class);
    Environment env = mock(Environment.class);
    when(ctx.getEnvironment()).thenReturn(env);
    when(env.getActiveProfiles()).thenReturn(new String[] {"prod", "some-other"});

    ContextRefreshedEvent event = new ContextRefreshedEvent(ctx);
    SecurityProfileValidator validator = new SecurityProfileValidator();

    assertDoesNotThrow(() -> validator.validate(event));
  }

  @Test
  void doesNotThrowWhenUnsafeButNotProdLike() {
    ApplicationContext ctx = mock(ApplicationContext.class);
    Environment env = mock(Environment.class);
    when(ctx.getEnvironment()).thenReturn(env);
    when(env.getActiveProfiles()).thenReturn(new String[] {"dev", "unsafe-foo"});

    ContextRefreshedEvent event = new ContextRefreshedEvent(ctx);
    SecurityProfileValidator validator = new SecurityProfileValidator();

    assertDoesNotThrow(() -> validator.validate(event));
  }
}
