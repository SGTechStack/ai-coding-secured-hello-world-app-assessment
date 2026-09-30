package local.builderday.account.adminbootstrap.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Triggers Admin bootstrap at startup. An {@code ApplicationRunner} runs after the context has refreshed, so Liquibase
 * has already migrated the schema; any {@link AdminBootstrapException} it throws stops startup (ADR 0009 §4). The
 * runner only delegates, so tests exercise {@link AdminBootstrap#bootstrap()} directly.
 */
@Component
class AdminBootstrapRunner implements ApplicationRunner {
  private final AdminBootstrap adminBootstrap;

  AdminBootstrapRunner(AdminBootstrap adminBootstrap) {
    this.adminBootstrap = adminBootstrap;
  }

  @Override
  public void run(ApplicationArguments args) {
    adminBootstrap.bootstrap();
  }
}
