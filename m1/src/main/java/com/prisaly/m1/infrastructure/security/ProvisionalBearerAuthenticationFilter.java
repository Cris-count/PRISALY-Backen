package com.prisaly.m1.infrastructure.security;

import com.prisaly.m1.application.port.AuthenticatedUser;
import com.prisaly.m1.application.service.IdentityAccessService;
import com.prisaly.m1.domain.rule.IdentityDomainException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * TD-M1-AUTH-PROVISIONAL filter. Reloads active user + permissions on every request.
 */
@Component
public class ProvisionalBearerAuthenticationFilter extends OncePerRequestFilter {

    private final ProvisionalAccessTokenStore tokenStore;
    private final IdentityAccessService identityAccessService;

    public ProvisionalBearerAuthenticationFilter(
            ProvisionalAccessTokenStore tokenStore,
            IdentityAccessService identityAccessService) {
        this.tokenStore = tokenStore;
        this.identityAccessService = identityAccessService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length()).trim();
            Optional<UUID> userId = tokenStore.resolveActiveUserId(token);
            if (userId.isPresent()) {
                try {
                    AuthenticatedUser user = identityAccessService.requireUser(userId.get());
                    Collection<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + user.roleName().toUpperCase()));
                    for (String permission : user.permissionCodes()) {
                        authorities.add(new SimpleGrantedAuthority("PERM_" + permission));
                    }
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(user.userId(), token, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (IdentityDomainException ex) {
                    tokenStore.revoke(token);
                    SecurityContextHolder.clearContext();
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
