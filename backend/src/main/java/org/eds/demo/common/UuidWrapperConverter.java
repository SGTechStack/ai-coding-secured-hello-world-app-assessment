package org.eds.demo.common;

import jakarta.persistence.AttributeConverter;
import java.util.UUID;
import java.util.function.Function;

public abstract class UuidWrapperConverter<T extends TypedId>
    implements AttributeConverter<T, UUID> {

  private final Function<UUID, T> wrap;

  protected UuidWrapperConverter(Function<UUID, T> wrap) {
    this.wrap = wrap;
  }

  @Override
  public UUID convertToDatabaseColumn(T attribute) {
    return attribute == null ? null : attribute.value();
  }

  @Override
  public T convertToEntityAttribute(UUID dbData) {
    return dbData == null ? null : wrap.apply(dbData);
  }
}
