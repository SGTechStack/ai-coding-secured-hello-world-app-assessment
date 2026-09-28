package com.example.helloauth.service;

import com.example.helloauth.domain.Role;
import com.example.helloauth.domain.User;
import com.example.helloauth.repo.UserRepository;
import com.example.helloauth.security.AuditLogger;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuditLogger audit;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       PasswordPolicy passwordPolicy, AuditLogger audit) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.audit = audit;
    }

    /** Registers a new USER account. Never logs or stores the plaintext password (IM8 lm-19). */
    @Transactional
    public User register(String username, String email, String rawPassword) {
        if (!passwordPolicy.isValid(rawPassword)) {
            throw new ServiceExceptions.ValidationException(passwordPolicy.requirementMessage());
        }
        if (userRepository.existsByUsername(username)) {
            throw new ServiceExceptions.ValidationException("Username is already taken.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ServiceExceptions.ValidationException("Email is already registered.");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(Role.USER);
        user.setEnabled(true);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<User> listAll() {
        return userRepository.findAll();
    }

    @Transactional
    public User setEnabled(UUID targetId, boolean enabled, User actor) {
        if (actor.getId().equals(targetId)) {
            throw new ServiceExceptions.SelfActionException("An admin cannot change their own enabled status.");
        }
        User target = requireUser(targetId);
        target.setEnabled(enabled);
        userRepository.save(target);
        audit.adminAction(enabled ? "enable" : "disable", actor.getUsername(), target.getUsername());
        return target;
    }

    @Transactional
    public User changeRole(UUID targetId, Role role, User actor) {
        if (actor.getId().equals(targetId)) {
            throw new ServiceExceptions.SelfActionException("An admin cannot change their own role.");
        }
        User target = requireUser(targetId);
        target.setRole(role);
        userRepository.save(target);
        audit.adminAction("role_change:" + role.name(), actor.getUsername(), target.getUsername());
        return target;
    }

    @Transactional
    public void delete(UUID targetId, User actor) {
        if (actor.getId().equals(targetId)) {
            throw new ServiceExceptions.SelfActionException("An admin cannot delete their own account.");
        }
        User target = requireUser(targetId);
        userRepository.delete(target);
        audit.adminAction("delete", actor.getUsername(), target.getUsername());
    }

    @Transactional(readOnly = true)
    public User requireUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ServiceExceptions.NotFoundException("User not found."));
    }

    @Transactional(readOnly = true)
    public User requireByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ServiceExceptions.NotFoundException("User not found."));
    }

    @Transactional
    public User save(User user) {
        return userRepository.save(user);
    }
}
