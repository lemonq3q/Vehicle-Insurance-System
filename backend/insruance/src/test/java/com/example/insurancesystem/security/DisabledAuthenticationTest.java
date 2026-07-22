package com.example.insurancesystem.security;

import com.example.insurancesystem.domain.authenticate.LoginUser;
import com.example.insurancesystem.domain.user.User;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;

class DisabledAuthenticationTest {

  @Test
  void providerReportsDisabledInsteadOfInternalAuthenticationError() {
    BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    User user = new User();
    user.setUsername("disabled-user");
    user.setPassword(passwordEncoder.encode("password"));
    user.setStatus(1);
    user.setEnterpriseId(1L);
    user.setMemberStatus(0);
    UserDetailsService userDetailsService = username -> new LoginUser(user, List.of());

    DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
    provider.setPasswordEncoder(passwordEncoder);
    provider.setUserDetailsService(userDetailsService);

    assertThrows(
        DisabledException.class,
        () ->
            provider.authenticate(
                new UsernamePasswordAuthenticationToken("disabled-user", "password")));
  }
}
