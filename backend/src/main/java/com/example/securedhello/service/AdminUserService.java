package com.example.securedhello.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.securedhello.entity.User;
import com.example.securedhello.repository.UserRepository;

/**
 * Admin account queries. Returns the account roster as entities; the
 * controller maps them to a non-sensitive view. Password hashes are never
 * exposed beyond the persistence layer.
 */
@Service
public class AdminUserService {

    private final UserRepository userRepository;

    public AdminUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<User> listUsers() {
        return userRepository.findAll();
    }
}
