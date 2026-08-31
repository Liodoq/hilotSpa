package com.hilotspa.backend.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity          // turns on @PreAuthorize, used in 0.6c
public class SecurityConfig {

    @Value("${hilotspa.cors.allowed-origin}")
    private String allowedOrigin;

    /** BCrypt by default, with the algorithm recorded in the hash as {bcrypt}... */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** Reads our own "roles" claim and turns CUSTOMER into ROLE_CUSTOMER. */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // --- Public ---
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // The public site: the spa's own details and its live menu, and
                // nothing else. Read-only by construction - PublicController has
                // no write mapping and returns a DTO built only from Massage, so
                // there is no client, no protocol rule and no availability it
                // could expose even by mistake. GET only, so a POST to this
                // prefix falls through to authenticated() below.
                .requestMatchers(HttpMethod.GET, "/api/v1/public/**").permitAll()

                // Self-service, ABOVE the ADMIN rule - Spring takes the FIRST
                // match, so below it these would never run (the B43 lesson).
                .requestMatchers("/api/v1/users/me", "/api/v1/users/me/**").authenticated()
                .requestMatchers("/api/v1/users/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST,   "/api/v1/branches/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,    "/api/v1/branches/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/branches/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST,   "/api/v1/massages/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,    "/api/v1/massages/**").hasRole("ADMIN")

                // Operational data. Reads are branch-scoped inside the service,
                // so a customer must never reach them at all.
                .requestMatchers(HttpMethod.GET, "/api/v1/protocols/**").hasAnyRole("STAFF", "ADMIN")
                .requestMatchers("/api/v1/protocols/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/appointments/schedule").hasAnyRole("STAFF", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/appointments/walk-in").hasAnyRole("STAFF", "ADMIN")
                .requestMatchers("/api/v1/therapists/**").hasAnyRole("STAFF", "ADMIN")
                .requestMatchers("/api/v1/rooms/**").hasAnyRole("STAFF", "ADMIN")
                .requestMatchers("/api/v1/audit-log/**").hasAnyRole("STAFF", "ADMIN")

                .requestMatchers("/api/v1/patient-intake/**").hasAnyRole("STAFF", "ADMIN")
                // B43: a CUSTOMER must be able to save their OWN profile, or
                // profileGuard bounces them out of the wizard before step 1.
                // This MUST stay ABOVE the STAFF/ADMIN line - Spring takes the
                // FIRST matching rule, so below it this never runs.
                .requestMatchers("/api/v1/demographics/me").authenticated()
                .requestMatchers("/api/v1/demographics/**").hasAnyRole("STAFF", "ADMIN")

                .requestMatchers(HttpMethod.GET, "/api/v1/branches/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/massages/**").authenticated()

                .anyRequest().authenticated())
            .oauth2ResourceServer(o -> o.jwt(
                jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(List.of(allowedOrigin));
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        c.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", c);
        return src;
    }
}