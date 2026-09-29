package com.example.securedhello.account;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PasswordHistoryRepository extends JpaRepository<PasswordHistoryEntry, UUID> {

}
