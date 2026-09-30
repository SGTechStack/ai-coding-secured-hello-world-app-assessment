package sg.example.helloauth;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HelloAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(HelloAuthApplication.class, args);
    }

    /** The one source of time, so time-dependent rules can be tested with a controllable clock. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
