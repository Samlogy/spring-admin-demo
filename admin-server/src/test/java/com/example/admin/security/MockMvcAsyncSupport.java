package com.example.admin.security;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * The SBA proxy calls {@code request.startAsync()}; MockMvc's mock request then creates an AsyncContext
 * without a response. This test-only filter passes the real (mock) response along.
 */
@TestConfiguration(proxyBeanMethods = false)
class MockMvcAsyncSupport {

    @Bean
    FilterRegistrationBean<Filter> mockMvcAsyncResponseFilter() {
        Filter filter = (request, response, chain) -> chain.doFilter(
                new HttpServletRequestWrapper((HttpServletRequest) request) {
                    @Override
                    public AsyncContext startAsync() {
                        return startAsync(this, response);
                    }
                }, response);
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
