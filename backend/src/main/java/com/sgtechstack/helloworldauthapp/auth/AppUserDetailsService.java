package com.sgtechstack.helloworldauthapp.auth;

import com.sgtechstack.helloworldauthapp.config.SecurityProperties;
import com.sgtechstack.helloworldauthapp.user.User;
import com.sgtechstack.helloworldauthapp.user.UserRepository;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves each user's fine-grained authorities from {@code
 * app.security.role-mappings} (see {@link SecurityProperties}) at load
 * time, so {@link UserPrincipal} always reflects the current configuration
 * rather than a value cached anywhere else.
 *
 * The mapped authorities are unioned across every role reachable via
 * {@link RoleHierarchy}, not just the user's own literal role. Role
 * hierarchy inheritance (see {@code SecurityConfig#roleHierarchy}) only
 * expands {@code ROLE_*} authorities to other {@code ROLE_*} authorities —
 * it has no notion of the fine-grained authorities {@code role-mappings}
 * associates with a role. Resolving inheritance here, once, at the single
 * place that has both the hierarchy and the mappings, is what lets a senior
 * role (e.g. {@code ADMIN}) reach a junior role's guarded routes (e.g.
 * {@code HELLO_READ}, mapped only to {@code USER}) without every junior
 * authority being repeated in the senior role's own mapping.
 *
 * A role with no configured mapping (shouldn't happen for {@code
 * USER}/{@code ADMIN} today, but guards against a future role added to
 * {@code Role} without a matching YAML entry) contributes no authorities
 * rather than throwing, so a misconfiguration fails closed instead of
 * failing login entirely.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final SecurityProperties securityProperties;
    private final RoleHierarchy roleHierarchy;

    public AppUserDetailsService(
            UserRepository userRepository,
            SecurityProperties securityProperties,
            RoleHierarchy roleHierarchy
    ) {
        this.userRepository = userRepository;
        this.securityProperties = securityProperties;
        this.roleHierarchy = roleHierarchy;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("No account with that username"));

        String ownRole = "ROLE_" + user.getRole().name();
        GrantedAuthority ownRoleAuthority = new SimpleGrantedAuthority(ownRole);

        Set<String> mappedAuthorities = new LinkedHashSet<>();
        for (GrantedAuthority reachable : roleHierarchy.getReachableGrantedAuthorities(List.of(ownRoleAuthority))) {
            String roleName = reachable.getAuthority().replaceFirst("^ROLE_", "");
            mappedAuthorities.addAll(securityProperties.roleMappings().getOrDefault(roleName, List.of()));
        }

        return new UserPrincipal(user, List.copyOf(mappedAuthorities));
    }
}
