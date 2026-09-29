package hello.desk.web;

import hello.desk.web.Payloads.GreetingBody;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hello")
public class HelloController {

    @GetMapping
    public GreetingBody hello(Authentication authentication) {
        return new GreetingBody("Hello, " + authentication.getName());
    }
}
