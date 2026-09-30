package org.eds.demo.user.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import org.eds.demo.common.TypedId;

public record UserId(UUID value) implements TypedId, Serializable {
  public UserId {
    Objects.requireNonNull(value);
  }
}
