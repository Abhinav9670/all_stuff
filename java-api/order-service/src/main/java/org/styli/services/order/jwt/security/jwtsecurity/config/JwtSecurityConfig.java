package org.styli.services.order.jwt.security.jwtsecurity.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.styli.services.order.jwt.security.jwtsecurity.security.JwtAuthenticationEntryPoint;
import org.styli.services.order.jwt.security.jwtsecurity.security.JwtAuthenticationProvider;
import org.styli.services.order.jwt.security.jwtsecurity.security.JwtAuthenticationTokenFilter;
import org.styli.services.order.jwt.security.jwtsecurity.security.JwtSuccessHandler;

@EnableGlobalMethodSecurity(prePostEnabled = true)
@EnableWebSecurity
@Configuration
public class JwtSecurityConfig extends WebSecurityConfigurerAdapter {

  @Autowired
  private JwtAuthenticationProvider authenticationProvider;
  @Autowired
  private JwtAuthenticationEntryPoint entryPoint;

  @Value("${order.jwt.flag}")
  String jwtFlag;

  @Bean
  public AuthenticationManager authenticationManager() {
    return new ProviderManager(Collections.singletonList(authenticationProvider));
  }

  @Bean
  public JwtAuthenticationTokenFilter authenticationTokenFilter() {
    JwtAuthenticationTokenFilter filter = new JwtAuthenticationTokenFilter(jwtFlag);
    filter.setAuthenticationManager(authenticationManager());
    filter.setAuthenticationSuccessHandler(new JwtSuccessHandler());
    return filter;
  }

  // @Override
  // public void configure(WebSecurity web) throws Exception {
  // web.ignoring().antMatchers("/rest/customer/registration/**");
  // }

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    http.cors();

    http.csrf().disable().authorizeRequests().antMatchers(HttpMethod.OPTIONS, "/**").permitAll()
        .antMatchers("**/rest/order/auth/**").authenticated().and().exceptionHandling().authenticationEntryPoint(entryPoint)
        .and().sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
  }

  @Value("${cors.urls}")
  private String corsUrls;

  @Value("${cors.allowed.headers}")
  private String corsAllowedHeader;

  @Value("${cors.methods}")
  private String corsMethods;

  @Value("${env}")
  private String env;

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    String allowedOrigins;
    if (env.contains("dev") || env.contains("qa") || env.contains("qa01") || env.contains("uat")) {
      allowedOrigins = corsUrls + ",http://local.stylifashion.com:3000,https://local.stylifashion.com:3000";
    } else {
      allowedOrigins = corsUrls;
    }
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
    configuration.setAllowedMethods(Arrays.asList(corsMethods.split(",")));
    configuration.setAllowedHeaders(Arrays.asList(corsAllowedHeader.split(",")));
    configuration.setExposedHeaders(Arrays.asList("x-auth-token"));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
