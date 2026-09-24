package com.resort.platform.auth;

import com.resort.platform.common.ProblemResponses;
import jakarta.servlet.DispatcherType;
import java.security.Principal;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRepository;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CsrfTokenRepository csrfTokenRepository,
            SecurityContextRepository securityContextRepository,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler,
            @Value("${springdoc.api-docs.enabled:false}") boolean apiDocsEnabled) {
        http.csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> {
                    auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll();
                    if (apiDocsEnabled) {
                        auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                    }
                    auth.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                            // Únicas rotas liberadas para quem precisa trocar a senha (D-055).
                            .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                            .requestMatchers(HttpMethod.POST, "/api/auth/change-password", "/api/auth/logout")
                            .authenticated()
                            .requestMatchers("/api/users/**", "/api/prospectors/**").hasRole("ADMIN")
                            .requestMatchers(HttpMethod.POST, "/api/leads").hasRole("ADMIN")
                            .requestMatchers(HttpMethod.PATCH, "/api/leads/assign").hasRole("ADMIN")
                            .requestMatchers("/api/leads/import", "/api/leads/import/**").hasRole("ADMIN")
                            .requestMatchers("/api/leads", "/api/leads/**").hasAnyRole("ADMIN", "PROSPECTOR")
                            .requestMatchers("/api/visits", "/api/visits/**").hasAnyRole("ADMIN", "PROSPECTOR")
                            .requestMatchers("/api/invitations", "/api/invitations/**").hasAnyRole("ADMIN", "PROSPECTOR")
                            .requestMatchers("/api/**").hasAnyRole("ADMIN", "PROSPECTOR", "GATE", "HOST")
                            .anyRequest().denyAll();
                });
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository(@Value("${server.servlet.session.cookie.secure:false}") boolean secure) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax").secure(secure));
        return repository;
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** Aplicada no login: novo id de sessão (session fixation) e novo token CSRF. */
    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfTokenRepository) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrfTokenRepository)));
    }

    @Bean
    AuthenticationEntryPoint authenticationEntryPoint(ProblemResponses problems) {
        return (request, response, exception) ->
                problems.write(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Autenticação necessária.");
    }

    @Bean
    AccessDeniedHandler accessDeniedHandler(ProblemResponses problems) {
        return (request, response, exception) -> {
            if (exception instanceof CsrfException) {
                problems.write(response, HttpStatus.FORBIDDEN, "CSRF_INVALID", "Token CSRF ausente ou inválido.");
            } else if (mustChangePassword(request.getUserPrincipal())) {
                problems.write(response, HttpStatus.FORBIDDEN, "PASSWORD_CHANGE_REQUIRED",
                        "É necessário trocar a senha antes de continuar.");
            } else {
                problems.write(response, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Acesso negado.");
            }
        };
    }

    private static boolean mustChangePassword(Principal principal) {
        return principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof AuthenticatedUser user
                && user.mustChangePassword();
    }
}
