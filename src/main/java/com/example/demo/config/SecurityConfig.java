package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.authorizeHttpRequests(authorize -> authorize
					.requestMatchers("/login", "/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
					.requestMatchers("/api/**").authenticated()
					.anyRequest().authenticated())
			.formLogin(form -> form
					.loginPage("/login")
					.defaultSuccessUrl("/todo", true)
					.failureUrl("/login?error")
					.permitAll())
			.logout(logout -> logout
					.logoutSuccessUrl("/login?logout")
					.invalidateHttpSession(true)
					.clearAuthentication(true)
					.deleteCookies("JSESSIONID"))
			.csrf(Customizer.withDefaults())
			.exceptionHandling(exceptions -> exceptions
					.defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
							PathPatternRequestMatcher.pathPattern("/api/**")));
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
