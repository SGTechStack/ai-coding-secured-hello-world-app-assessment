package org.eds.demo.config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.EncodedResourceResolver;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the frontend SPA static assets from a configurable location.
 *
 * <p>Defaults to a {@code static/} directory beside the running JAR. Override {@code
 * app.spa.static-location} in a profile-specific properties file to point at a different path, for
 * example the local {@code demo-frontend/dist} output.
 */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class WebConfiguration implements WebMvcConfigurer {

  private final AppProperties.Spa spa;
  private final Environment environment;

  public WebConfiguration(AppProperties appProperties, Environment environment) {
    this.spa = appProperties.spa();
    this.environment = environment;
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    var locations = new ArrayList<>(List.of(spa.staticLocation(), "classpath:/static/"));
    // static-local/ contains dev-only assets (e.g. mock login page) that must not be served in
    // other profiles, so it is registered only when the local profile is active rather than via
    // spring.web.resources.static-locations which applies to all profiles.
    if (List.of(environment.getActiveProfiles()).contains("local")) {
      locations.add("classpath:/static-local/");
    }
    // Vite content-hashes asset filenames — safe to cache forever.
    // Matches anything in the /app/assets/ folder.
    // Captures your hashed .js, .css, images, and fonts.
    registry
        .addResourceHandler("/app/assets/**")
        .addResourceLocations(locations.toArray(String[]::new))
        .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
        .resourceChain(true)
        .addResolver(new EncodedResourceResolver()) // Automatically serves your .br / .gz files
        .addResolver(new PathResourceResolver());

    // Entry points (index.html, welcome/index.html) have no content hash — always revalidate.
    registry
        .addResourceHandler("/**")
        .addResourceLocations(locations.toArray(String[]::new))
        .setCacheControl(CacheControl.noCache().mustRevalidate())
        .resourceChain(true)
        .addResolver(new EncodedResourceResolver()) // Automatically serves your .br / .gz files
        .addResolver(new PathResourceResolver());
  }
}
