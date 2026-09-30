package org.eds.demo.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class BusinessExceptionTest {

  @Test
  void badRequestExceptionHas400Status() {
    var ex = new BadRequestException("Invalid input");

    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(ex.getMessage()).isEqualTo("Invalid input");
  }

  @Test
  void notFoundExceptionHas404Status() {
    var ex = new NotFoundException("Resource not found");

    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(ex.getMessage()).isEqualTo("Resource not found");
  }

  @Test
  void notFoundExceptionWithResourceAndIdentifier() {
    var ex = new NotFoundException("User", "abc-123");

    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(ex.getMessage()).isEqualTo("User not found with id 'abc-123'");
  }

  @Test
  void conflictExceptionHas409Status() {
    var ex = new ConflictException("Already exists");

    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(ex.getMessage()).isEqualTo("Already exists");
  }
}
