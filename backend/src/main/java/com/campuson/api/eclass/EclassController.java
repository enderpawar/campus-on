package com.campuson.api.eclass;

import com.campuson.api.global.security.AuthMember;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/eclass")
public class EclassController {
    private final EclassSyncService sync;
    private final EclassAccountService accounts;
    public EclassController(EclassSyncService sync, EclassAccountService accounts) { this.sync = sync; this.accounts = accounts; }
    @GetMapping("/connection")
    public EclassConnectionResponse connection(@AuthenticationPrincipal AuthMember me) { return accounts.status(me.memberId()); }
    @PostMapping("/sync")
    public EclassConnectionResponse synchronize(@AuthenticationPrincipal AuthMember me) { return sync.sync(me.memberId()); }
    @DeleteMapping("/connection")
    public ResponseEntity<Void> disconnect(@AuthenticationPrincipal AuthMember me) {
        accounts.disconnect(me.memberId()); return ResponseEntity.noContent().build();
    }
}
