package com.sgtechstack.helloauth.hello;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HelloController {

	@GetMapping(path = "/api/hello", produces = MediaType.TEXT_PLAIN_VALUE)
	String hello(Authentication authentication) {
		return "Hello, " + authentication.getName();
	}

}
