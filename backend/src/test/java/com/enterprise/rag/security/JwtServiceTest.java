package com.enterprise.rag.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.JwtException;

class JwtServiceTest {

    private static final String SECRET = "test-secret-that-is-at-least-32-bytes-long!";

    @Test
    void roundTripsUserAndRoles() {
        JwtService jwt = new JwtService(SECRET, Duration.ofMinutes(5));
        AuthUser user = new AuthUser(7L, "alice", Set.of(Role.HR, Role.EMPLOYEE));

        AuthUser parsed = jwt.parse(jwt.generateToken(user));

        assertThat(parsed).isEqualTo(user);
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        String token = new JwtService(SECRET, Duration.ofMinutes(5))
                .generateToken(new AuthUser(1L, "bob", Set.of(Role.ADMIN)));
        JwtService other = new JwtService("another-secret-that-is-also-32-bytes-long", Duration.ofMinutes(5));

        assertThatThrownBy(() -> other.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() {
        JwtService jwt = new JwtService(SECRET, Duration.ofSeconds(-10));
        String token = jwt.generateToken(new AuthUser(1L, "bob", Set.of(Role.EMPLOYEE)));

        assertThatThrownBy(() -> jwt.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new JwtService("too-short", Duration.ofMinutes(5)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void accessRulesFollowRoleOverlapWithAdminBypass() {
        AuthUser employee = new AuthUser(1L, "dave", Set.of(Role.EMPLOYEE));
        AuthUser admin = new AuthUser(2L, "admin", Set.of(Role.ADMIN));

        assertThat(employee.canAccess(List.of("EMPLOYEE", "FINANCE"))).isTrue();
        assertThat(employee.canAccess(List.of("HR"))).isFalse();
        assertThat(admin.canAccess(List.of("HR"))).isTrue();
        assertThat(employee.roleNames()).containsExactly("EMPLOYEE");
    }
}
