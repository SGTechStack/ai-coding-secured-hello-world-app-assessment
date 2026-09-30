package local.builderday.architecture.fixture.usecase;

import local.builderday.architecture.fixture.core.CoreSide;

/** Fixture: a use case that depends on its domain core, which depends back on it. */
@SuppressWarnings("unused")
public class UseCaseSide {
  private CoreSide core;
}
