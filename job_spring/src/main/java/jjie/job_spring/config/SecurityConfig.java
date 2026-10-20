package jjie.job_spring.config;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import jjie.job_spring.auth.common.AppUserDetails;
import jjie.job_spring.auth.common.CurrentUserResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationProvider provider,
                                           ObjectMapper json) throws Exception {
        http.authenticationProvider(provider);
        // 注册不要求登录，但 POST 仍须携带有效的 CSRF Token。
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .anyRequest().authenticated());
        // 使用内置表单认证过滤器，自动保存 SecurityContext、处理会话固定攻击与 CSRF 更新。
        http.formLogin(form -> form
                .loginPage("/api/auth/login")
                .loginProcessingUrl("/api/auth/login")
                .successHandler((request, response, authentication) ->
                        writeJson(response, 200,
                                CurrentUserResponse.from((AppUserDetails) authentication.getPrincipal()), json))
                .failureHandler((request, response, exception) -> {
                    if (exception instanceof AuthenticationServiceException) {
                        writeJson(response, 503,
                                new SecurityError("SERVICE_UNAVAILABLE", "登录服务暂不可用，请稍后重试"), json);
                    } else {
                        writeJson(response, 401,
                                new SecurityError("INVALID_CREDENTIALS", "用户名或密码错误"), json);
                    }
                }));
        http.logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204))
                .permitAll());
        http.requestCache(cache -> cache.disable());
        http.exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, exception) ->
                        writeJson(response, 401,
                                new SecurityError("UNAUTHENTICATED", "请先登录"), json))
                .accessDeniedHandler((request, response, exception) ->
                        writeJson(response, 403,
                                new SecurityError("FORBIDDEN", "请求校验未通过，请刷新后重试"), json)));
        // 保留默认 CSRF 保护，不通过全站放行来绕过 Security。
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(12);
        return new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                return bcrypt.encode(rawPassword);
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                // 登录也执行字节上限，避免超过 BCrypt 输入范围的内容被截断后比对。
                if (rawPassword == null || rawPassword.toString().getBytes(StandardCharsets.UTF_8).length > 72) {
                    return false;
                }
                return bcrypt.matches(rawPassword, encodedPassword);
            }

            @Override
            public boolean upgradeEncoding(String encodedPassword) {
                return bcrypt.upgradeEncoding(encodedPassword);
            }
        };
    }

    @Bean
    AuthenticationProvider authenticationProvider(UserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return provider;
    }

    private static void writeJson(HttpServletResponse response, int status, Object body,
                                  ObjectMapper json) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        json.writeValue(response.getOutputStream(), body);
    }

    public record SecurityError(String code, String message) {
    }
}
