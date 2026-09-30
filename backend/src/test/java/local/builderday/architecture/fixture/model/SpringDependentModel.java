package local.builderday.architecture.fixture.model;

import org.springframework.security.core.userdetails.User;

/** Fixture: a model type other than AccountPrincipal depending on Spring Security, which the rule must reject. */
@SuppressWarnings("unused")
public class SpringDependentModel {
  private User user;
}
