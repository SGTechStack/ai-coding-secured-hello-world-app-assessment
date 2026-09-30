package local.builderday.architecture.fixture.core;

import local.builderday.architecture.fixture.usecase.UseCaseSide;

/** Fixture: a domain core that depends on one of its own use cases, closing a cycle with {@link UseCaseSide}. */
@SuppressWarnings("unused")
public class CoreSide {
  private UseCaseSide useCase;
}
