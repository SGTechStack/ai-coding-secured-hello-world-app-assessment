package org.eds.demo.user.domain;

import jakarta.persistence.Converter;
import org.eds.demo.common.UuidWrapperConverter;

@Converter
public class UserIdConverter extends UuidWrapperConverter<UserId> {
  public UserIdConverter() {
    super(UserId::new);
  }
}
