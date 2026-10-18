package jjie.job_spring.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // 注册不要求登录，但 POST 仍须携带有效的 CSRF Token。
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                .anyRequest().authenticated());
        http.exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, exception) ->
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED))
                .accessDeniedHandler((request, response, exception) ->
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN)));
        // 保留默认 CSRF 保护，不通过全站放行来绕过 Security。
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
