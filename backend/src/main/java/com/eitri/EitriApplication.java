package com.eitri;

import java.time.ZoneId;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EitriApplication {

    static {
        // Structured logging reads the JVM default zone during Boot's logging initialization.
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Asia/Singapore")));
    }

    public static void main(String[] args) {
        SpringApplication.run(EitriApplication.class, args);
    }
}
