package com.enterprise.rag.user;

import java.util.Set;

import com.enterprise.rag.security.Role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public final class UserDtos {

    private UserDtos() {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(String token, long expiresInSeconds, UserResponse user) {
    }

    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 100) String username,
            @NotBlank @Size(min = 8, max = 100) String password,
            @Size(max = 200) String fullName,
            @NotEmpty Set<Role> roles) {
    }

    public record UserResponse(Long id, String username, String fullName, Set<Role> roles) {

        public static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getUsername(), user.getFullName(), user.getRoles());
        }
    }
}
