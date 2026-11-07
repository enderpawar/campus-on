package com.campuson.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** No app registration: use the student's e-class credentials. Saving a password is opt-in. */
public record LoginRequest(@NotBlank @Size(max = 64) String studentId,
                           @NotBlank @Size(max = 1024) String password,
                           boolean rememberCredentials) {
    public LoginRequest { studentId = StudentIds.normalize(studentId); }
}
