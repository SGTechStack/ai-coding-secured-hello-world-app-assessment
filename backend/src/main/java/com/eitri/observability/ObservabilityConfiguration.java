package com.eitri.observability;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ObservabilityConfiguration {

    @Bean
    Ticker monotonicTicker() {
        return System::nanoTime;
    }

    @Bean
    HostMetadataResolver hostMetadataResolver() {
        return () -> {
            try {
                InetAddress localHost = InetAddress.getLocalHost();
                return new HostMetadata(localHost.getHostName(), localHost.getHostAddress());
            } catch (UnknownHostException exception) {
                return new HostMetadata("unavailable", "unavailable");
            }
        };
    }

    @Bean
    FilterRegistrationBean<RequestObservabilityFilter> requestObservabilityFilter(
            Ticker ticker, SecurityFilterProperties securityFilterProperties) {
        FilterRegistrationBean<RequestObservabilityFilter> registration =
                new FilterRegistrationBean<>(new RequestObservabilityFilter(ticker));
        registration.setName("requestObservabilityFilter");
        registration.setOrder(securityFilterProperties.getOrder() - 1);
        return registration;
    }
}
