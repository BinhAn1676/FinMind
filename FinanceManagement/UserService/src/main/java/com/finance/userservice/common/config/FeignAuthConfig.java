package com.finance.userservice.common.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

@Configuration
public class FeignAuthConfig {

	@Bean
	public RequestInterceptor oauth2FeignRequestInterceptor() {
		return new RequestInterceptor() {
			@Override
			public void apply(RequestTemplate template) {
				Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
				if (authentication != null && authentication.getPrincipal() instanceof Jwt) {
					Jwt jwt = (Jwt) authentication.getPrincipal();
					String token = jwt.getTokenValue();
					if (StringUtils.hasText(token)) {
						template.header("Authorization", "Bearer " + token);
					}
				}
			}
		};
	}
}


