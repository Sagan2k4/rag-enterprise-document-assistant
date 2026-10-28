package com.enterprise.rag.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * The authenticated principal, rebuilt from the JWT on every request.
 */
public record AuthUser(Long id, String username, Set<Role> roles) {

    public AuthUser {
        roles = roles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(roles);
    }

    public boolean isAdmin() {
        return roles.contains(Role.ADMIN);
    }

    /** True when the user may read a document restricted to {@code allowedRoles}. */
    public boolean canAccess(Collection<String> allowedRoles) {
        return isAdmin() || allowedRoles.stream().anyMatch(r -> roles.stream().anyMatch(own -> own.name().equals(r)));
    }

    public boolean canAccess(String[] allowedRoles) {
        return canAccess(Arrays.asList(allowedRoles));
    }

    /** Sorted role names — stable input for cache keys and SQL array literals. */
    public String[] roleNames() {
        return roles.stream().map(Role::name).sorted().toArray(String[]::new);
    }
}
