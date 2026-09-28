package sg.securedhello.session;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.boot.web.server.Cookie;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.HttpSessionIdResolver;

/**
 * Spring Session JDBC wiring (ADR-029; ADR-030): the session cookie, the one-cookie id resolver and the attribute
 * allowlist. The timeout, cleanup cron and cookie attributes are configuration ({@code server.servlet.session.*},
 * {@code spring.session.*}).
 */
@Configuration(proxyBeanMethods = false)
public class SessionConfig implements BeanClassLoaderAware {

    private ClassLoader classLoader = SessionConfig.class.getClassLoader();

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * The session cookie, from {@code server.servlet.session.cookie.*}. It must be explicit: with the resolver below,
     * which is not a {@code CookieHttpSessionIdResolver}, Boot stops creating its own serializer, and every cookie
     * property would silently stop applying (REJ-083; T-SES-031). {@code max-age} is deliberately not mapped: the
     * cookie never persists (REJ-008).
     */
    @Bean
    CookieSerializer cookieSerializer(ServerProperties serverProperties) {
        Cookie cookie = serverProperties.getServlet().getSession().getCookie();
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        PropertyMapper map = PropertyMapper.get();
        map.from(cookie::getName).to(serializer::setCookieName);
        map.from(cookie::getDomain).to(serializer::setDomainName);
        map.from(cookie::getPath).to(serializer::setCookiePath);
        map.from(cookie::getHttpOnly).to(serializer::setUseHttpOnlyCookie);
        map.from(cookie::getSecure).to(serializer::setUseSecureCookie);
        map.from(cookie::getSameSite).to(sameSite -> serializer.setSameSite(sameSite.attributeValue()));
        map.from(cookie::getPartitioned).to(serializer::setPartitioned);
        return serializer;
    }

    /** Only the first session cookie is honoured (R-SES-007). */
    @Bean
    HttpSessionIdResolver httpSessionIdResolver(CookieSerializer cookieSerializer) {
        return new FirstSessionCookieResolver(cookieSerializer);
    }

    /** The repository's attribute conversion, deserialising through the allowlist (R-SES-003). */
    @Bean
    ConversionService springSessionConversionService() {
        GenericConversionService conversionService = new GenericConversionService();
        conversionService.addConverter(Object.class, byte[].class, new SerializingConverter());
        conversionService.addConverter(byte[].class, Object.class,
                new DeserializingConverter(new SessionAttributeAllowlist(classLoader)));
        return conversionService;
    }
}
