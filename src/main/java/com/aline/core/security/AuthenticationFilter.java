package com.aline.core.security;

import com.aline.core.config.DisableSecurityConfig;
import com.aline.core.dto.request.AuthenticationRequest;
import com.aline.core.exception.ForbiddenException;
import com.aline.core.security.config.JwtConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;

@Component
@Slf4j(topic = "Authentication Filter")
@RequiredArgsConstructor
@ConditionalOnMissingBean(DisableSecurityConfig.class)
public class AuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    private final JwtConfig jwtConfig;
    private final SecretKey jwtSecretKey;
    private final ObjectMapper objectMapper;

    @Override
    @Autowired
    public void setAuthenticationManager(AuthenticationManager authenticationManager) {
        super.setAuthenticationManager(authenticationManager);
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
        log.info("Attempting to authenticate user.");
        try {
            val authRequest = objectMapper.readValue(request.getInputStream(),
                    AuthenticationRequest.class);

            val authentication = new UsernamePasswordAuthenticationToken(authRequest.getUsername(), authRequest.getPassword());

            return getAuthenticationManager().authenticate(authentication);

        } catch (IOException e) {
            e.printStackTrace();
            throw new ForbiddenException("Unable to authenticate user.");
        }
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain, Authentication authResult) throws IOException, ServletException {
        log.info("Successfully authenticated.");
        int expireAfterDays = jwtConfig.getTokenExpirationAfterDays();

        GrantedAuthority authority = new ArrayList<>(authResult.getAuthorities()).get(0);

        String token = Jwts.builder()
                .setSubject(authResult.getName())
                .claim("authority", authority.getAuthority())
                .setIssuedAt(Date.from(LocalDateTime.now().toInstant(ZoneOffset.UTC)))
                .setExpiration(Date.from(LocalDateTime.now().plusDays(expireAfterDays).toInstant(ZoneOffset.UTC)))
                .signWith(jwtSecretKey, SignatureAlgorithm.HS512)
                .compact();

        String tokenStr = jwtConfig.getTokenPrefix() + token;

        response.setHeader(HttpHeaders.AUTHORIZATION, tokenStr);

    }
}
