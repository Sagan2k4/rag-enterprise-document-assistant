package com.enterprise.rag.user;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.enterprise.rag.config.RagProperties;
import com.enterprise.rag.security.Role;

/**
 * Creates one demo account per role so RBAC can be demonstrated immediately.
 * Disable with {@code SEED_USERS=false}.
 */
@Component
@Order(1)
public class UserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final RagProperties properties;

    public UserSeeder(UserRepository users, PasswordEncoder passwordEncoder, RagProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.seed().users() || users.count() > 0) {
            return;
        }
        create("admin", "admin123", "Ada Admin", Set.of(Role.ADMIN));
        create("alice", "password", "Alice (HR)", Set.of(Role.HR, Role.EMPLOYEE));
        create("bob", "password", "Bob (Engineering)", Set.of(Role.ENGINEERING, Role.EMPLOYEE));
        create("carol", "password", "Carol (Finance)", Set.of(Role.FINANCE, Role.EMPLOYEE));
        create("dave", "password", "Dave (Employee)", Set.of(Role.EMPLOYEE));
        log.info("Seeded demo users: admin, alice, bob, carol, dave");
    }

    private void create(String username, String password, String fullName, Set<Role> roles) {
        users.save(new User(username, passwordEncoder.encode(password), fullName, roles));
    }
}
