package com.resort.platform.auth;

import com.resort.platform.auth.dto.ChangePasswordRequest;
import com.resort.platform.auth.dto.LoginRequest;
import com.resort.platform.auth.dto.MeResponse;
import com.resort.platform.users.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfLogoutHandler csrfLogoutHandler;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    public AuthController(
            AuthService authService,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository) {
        this.authService = authService;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.securityContextRepository = securityContextRepository;
        this.csrfLogoutHandler = new CsrfLogoutHandler(csrfTokenRepository);
    }

    @PostMapping("/login")
    public MeResponse login(
            @Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        User user = authService.authenticate(body.email(), body.password(), request.getRemoteAddr());
        Authentication authentication = authenticationFor(user);
        // Novo id de sessão (session fixation) e novo token CSRF.
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
        saveAuthentication(authentication, request, response);
        renderCsrfToken(request);
        return MeResponse.of(user);
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return MeResponse.of(authService.currentUser(principal.id()));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ChangePasswordRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        User user = authService.changePassword(
                principal.id(), body.currentPassword(), body.newPassword(), request.getSession().getId());
        request.changeSessionId();
        // A sessão passa a ter as authorities completas, sem PASSWORD_CHANGE_REQUIRED (D-055).
        saveAuthentication(authenticationFor(user), request, response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal AuthenticatedUser principal,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {
        authService.recordLogout(principal.id());
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        csrfLogoutHandler.logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }

    private static Authentication authenticationFor(User user) {
        AuthenticatedUser principal =
                new AuthenticatedUser(user.getId(), user.getRole(), user.isMustChangePassword());
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.authorities());
    }

    private void saveAuthentication(
            Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    /** Emite já nesta resposta o token CSRF gerado no login. */
    private static void renderCsrfToken(HttpServletRequest request) {
        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken csrfToken) {
            csrfToken.getToken();
        }
    }
}
