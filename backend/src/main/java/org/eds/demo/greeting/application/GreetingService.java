package org.eds.demo.greeting.application;

import org.springframework.stereotype.Service;

/** Builds the greeting shown to a signed-in Account. */
@Service
public class GreetingService {

  public String greet(String username) {
    return "Hello, " + username;
  }
}
