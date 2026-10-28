package com.enterprise.rag.user;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.enterprise.rag.common.ApiException;
import com.enterprise.rag.security.AuthUser;
import com.enterprise.rag.security.JwtService;
import com.enterprise.rag.user.UserDtos.LoginRequest;
import com.enterprise.rag.user.UserDtos.LoginResponse;
import com.enterprise.rag.user.UserDtos.UserResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        User user = users.findByUsername(request.username())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("Invalid username or password"));
        String token = jwtService.generateToken(user.toAuthUser());
        return new LoginResponse(token, jwtService.expiration().toSeconds(), UserResponse.from(user));
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthUser principal) {
        return users.findByUsername(principal.username())
                .map(UserResponse::from)
                .orElseThrow(() -> ApiException.unauthorized("User no longer exists"));
    }
}
