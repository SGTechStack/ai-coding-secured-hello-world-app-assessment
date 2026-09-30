package com.example.hello.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank @Size(max = 32) String username, @NotBlank @Size(max = 128) String password) {

  @Override
  public String toString() {
    return "LoginRequest{username='" + username + "', password='***'}";
  }
}
