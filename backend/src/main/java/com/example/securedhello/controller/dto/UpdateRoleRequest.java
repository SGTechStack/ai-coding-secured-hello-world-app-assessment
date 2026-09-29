package com.example.securedhello.controller.dto;

import jakarta.validation.constraints.NotNull;

import com.example.securedhello.entity.Role;

/** Request body to change a target account's role. */
public record UpdateRoleRequest(@NotNull Role role) {
}
