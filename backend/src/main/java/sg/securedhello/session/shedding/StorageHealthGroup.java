package sg.securedhello.session.shedding;

import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.health.actuate.endpoint.AdditionalHealthEndpointPath;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroup;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroupsPostProcessor;
import org.springframework.boot.health.actuate.endpoint.HttpCodeStatusMapper;
import org.springframework.boot.health.actuate.endpoint.StatusAggregator;

/**
 * Keeps {@code h2Data} out of {@code /actuator/health}, so a shed episode never turns the endpoint a load balancer
 * polls into 503 and ejects a node whose signed-in users are unaffected (ADR-041). The indicator is reported only by
 * the {@value #GROUP} group, {@code /actuator/health/storage}, set in {@code application.yml}. Boot's primary group
 * holds every indicator and no property excludes one from it, so this post-processor narrows its membership. It also
 * leaves the group out of the names the root body lists, which keeps that body detail-free (T-OBS-009).
 */
final class StorageHealthGroup implements HealthEndpointGroupsPostProcessor {

    /** The health group that reports {@link #INDICATOR}. */
    static final String GROUP = "storage";

    /** The shed episode's indicator, from the {@code h2DataHealthIndicator} bean. */
    static final String INDICATOR = "h2Data";

    @Override
    public HealthEndpointGroups postProcessHealthEndpointGroups(HealthEndpointGroups groups) {
        HealthEndpointGroup primary = new PrimaryWithoutStorage(groups.getPrimary());
        return new HealthEndpointGroups() {
            @Override
            public HealthEndpointGroup getPrimary() {
                return primary;
            }

            /** Unlisted, so the root body stays {@code {"status":...}} alone; {@link #get} still resolves it. */
            @Override
            public Set<String> getNames() {
                return groups.getNames().stream().filter(name -> !GROUP.equals(name))
                        .collect(Collectors.toUnmodifiableSet());
            }

            @Override
            public @Nullable HealthEndpointGroup get(String name) {
                return groups.get(name);
            }
        };
    }

    /** The primary group, less {@link #INDICATOR}. */
    private record PrimaryWithoutStorage(HealthEndpointGroup delegate) implements HealthEndpointGroup {

        @Override
        public boolean isMember(String name) {
            return !INDICATOR.equals(name) && delegate.isMember(name);
        }

        @Override
        public boolean showComponents(SecurityContext securityContext) {
            return delegate.showComponents(securityContext);
        }

        @Override
        public boolean showDetails(SecurityContext securityContext) {
            return delegate.showDetails(securityContext);
        }

        @Override
        public StatusAggregator getStatusAggregator() {
            return delegate.getStatusAggregator();
        }

        @Override
        public HttpCodeStatusMapper getHttpCodeStatusMapper() {
            return delegate.getHttpCodeStatusMapper();
        }

        @Override
        public @Nullable AdditionalHealthEndpointPath getAdditionalPath() {
            return delegate.getAdditionalPath();
        }
    }
}
