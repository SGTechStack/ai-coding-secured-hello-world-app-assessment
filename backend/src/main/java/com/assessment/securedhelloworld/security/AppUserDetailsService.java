package com.assessment.securedhelloworld.security;

import com.assessment.securedhelloworld.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(AppUserDetails::new)
                // Spring Security's DaoAuthenticationProvider (hideUserNotFoundExceptions=true,
                // the default) converts this into the same BadCredentialsException as a wrong
                // password, so an unknown username never produces a distinguishable error
                // (enumeration resistance, PRD Story 2 / IM8 as-7).
                .orElseThrow(() -> new UsernameNotFoundException("No such user: " + username));
    }
}
