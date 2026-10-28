package com.enterprise.rag.user;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.enterprise.rag.common.ApiException;
import com.enterprise.rag.user.UserDtos.CreateUserRequest;
import com.enterprise.rag.user.UserDtos.UserResponse;

import jakarta.validation.Valid;

/** User administration — restricted to ADMIN in {@code SecurityConfig}. */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AdminUserController(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public List<UserResponse> list() {
        return users.findAll().stream().map(UserResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        if (users.existsByUsername(request.username())) {
            throw ApiException.conflict("Username already taken");
        }
        User user = new User(request.username(), passwordEncoder.encode(request.password()),
                request.fullName(), request.roles());
        return UserResponse.from(users.save(user));
    }
}
