package com.egastro.security;

import com.egastro.enums.Role;

public record AuthenticatedUser(Long id, String email, Role role) {
}
