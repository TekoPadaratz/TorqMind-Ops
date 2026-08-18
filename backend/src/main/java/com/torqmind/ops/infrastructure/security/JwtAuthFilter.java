package com.torqmind.ops.infrastructure.security;

import com.torqmind.ops.domain.user.User;
import com.torqmind.ops.infrastructure.persistence.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            try {
                JwtService.ParsedToken parsed = jwtService.parseToken(token);
                User user = userRepository.findById(parsed.principal().userId()).orElse(null);
                if (user == null || !user.isActive() || parsed.passwordEpoch() != user.getPasswordEpoch()) {
                    SecurityContextHolder.clearContext();
                } else {
                    AppUserPrincipal principal = new AppUserPrincipal(
                            user.getId(),
                            user.getUsername(),
                            user.getRole(),
                            user.getCompanyId(),
                            user.getBranchId()
                    );
                    var authority = new SimpleGrantedAuthority("ROLE_" + principal.role());
                    var authentication = new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(authority));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception ex) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
