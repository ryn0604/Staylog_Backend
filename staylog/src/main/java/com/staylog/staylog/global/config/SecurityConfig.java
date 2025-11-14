package com.staylog.staylog.global.config;

import com.staylog.staylog.global.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 설정
 * - JWT 기반 인증/인가 설정
 * - CORS 설정
 * - CSRF 비활성화 (stateless API)
 * - 세션 관리: STATELESS
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;


    /**
     * Security Filter Chain 설정
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF 비활성화 (JWT 사용으로 stateless)
                .csrf(AbstractHttpConfigurer::disable)

                // CORS 설정 활성화
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 세션 관리: STATELESS (JWT 사용)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 요청 인가 설정
                .authorizeHttpRequests(auth -> auth
                        // 인증 없이 접근 가능한 엔드포인트 (회원가입, 로그인 등)
                        .requestMatchers(
                                "/v1/**",
                                "/v1/auth/**",           // 인증 관련 (회원가입, 로그인)
                                "/v1/test/**",           // 테스트 엔드포인트
                                "/error",                // 에러 페이지
                                "/actuator/**",          // Spring Actuator
                                "/swagger-ui/**",        // Swagger UI
                                "/v3/api-docs/**",        // Swagger API Docs
                                "/**"
                        ).permitAll()
                        
                        /**
                         * https 관련 문제 수정
                         * @author 고윤제
                         */
                		.requestMatchers(HttpMethod.OPTIONS,"/**").permitAll()	// 프리플라이트 통과

                        /**
                         * SSE 구독 경로는 인증 필요
                         * @author 이준혁
                         */
                        .requestMatchers("/api/v1/notification/subscribe").authenticated()	// 프리플라이트 통과
                		
                        // 나머지 요청은 인증 필요
                        .anyRequest().authenticated()
                )

                // JWT 인증 필터를 UsernamePasswordAuthenticationFilter 이전에 추가
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS 설정
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 허용할 Origin (개발 환경)
        configuration.setAllowedOrigins(Arrays.asList(
                "http://localhost:3000",      // React 개발 서버
                "http://localhost:8080",      // 로컬 테스트
                "http://127.0.0.1:3000",
                "http://127.0.0.1:8080",
                "http://localhost:5173"
        ));

        // 허용할 HTTP 메서드
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));

        // 허용할 헤더
        configuration.setAllowedHeaders(Arrays.asList("*"));

        // 인증 정보 포함 허용 (쿠키, Authorization 헤더 등)
        configuration.setAllowCredentials(true);

        // preflight 요청 캐싱 시간 (초)
        configuration.setMaxAge(3600L);

        // 노출할 헤더 (클라이언트가 접근할 수 있는 응답 헤더)
        configuration.setExposedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type"
        ));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    /**
     * 비밀번호 암호화를 위한 PasswordEncoder 빈 등록
     * BCrypt 알고리즘 사용
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }


}