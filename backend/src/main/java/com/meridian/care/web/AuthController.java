package com.meridian.care.web;

import com.meridian.care.security.AppUserPrincipal;
import com.meridian.care.web.dto.MeResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Removed: local login no longer used — Auth0 handles authentication via /oauth2/authorization/auth0
// import com.meridian.care.web.dto.LoginRequest;
// import jakarta.servlet.http.HttpServletResponse;
// import jakarta.validation.Valid;
// import org.springframework.security.authentication.AuthenticationManager;
// import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
// import org.springframework.security.core.Authentication;
// import org.springframework.security.core.AuthenticationException;
// import org.springframework.security.core.context.SecurityContext;
// import org.springframework.security.web.context.SecurityContextRepository;
// import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    // Removed: AuthenticationManager — local credential check replaced by Auth0 OIDC flow
    // private final AuthenticationManager authenticationManager;
    // private final SecurityContextRepository securityContextRepository;

    private final SecurityContextHolderStrategy holderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    // Removed: /api/auth/login — login is now initiated via GET /oauth2/authorization/auth0
    // @PostMapping("/login")
    // public MeResponse login(@Valid @RequestBody LoginRequest request, ...) { ... }

    // Returns the current authenticated user and their care home
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AppUserPrincipal principal) {
        return MeResponse.from(principal);
    }

    // Invalidates the server-side session; Auth0 logout is handled by SecurityConfig
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        holderStrategy.clearContext();
    }

    // Removed: only handled bad credentials from local login — no longer applicable
    // @ExceptionHandler(AuthenticationException.class)
    // @ResponseStatus(HttpStatus.UNAUTHORIZED)
    // public Map<String, String> handleBadCredentials(AuthenticationException ex) { ... }
}
