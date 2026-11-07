package com.campuson.api.auth;

import com.campuson.api.global.security.AuthMember;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final com.campuson.api.global.security.ClientAddressResolver addresses;
    public AuthController(AuthService authService, com.campuson.api.global.security.ClientAddressResolver addresses) { this.authService = authService; this.addresses = addresses; }
    /** 명시한 신뢰 프록시에서 온 전달 헤더만 사용한다. */
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, addresses.resolve(http));
    }
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) { return authService.refresh(request); }
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthMember me) {
        authService.logout(me.memberId());
        return ResponseEntity.noContent().build();
    }
}
