package com.example.insurancesystem.domain.authenticate;

import com.example.insurancesystem.domain.user.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoginUser implements UserDetails {

    private User user;

    private List<String> permissions;

    private String sessionId;

    private Long enterpriseId;

    @JsonIgnore
    private List<GrantedAuthority> authorities;

    public LoginUser(User user, List<String> permissions) {
        this.user = user;
        this.enterpriseId = user.getEnterpriseId();
        this.permissions = Objects.requireNonNullElseGet(permissions, ArrayList::new);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {

        if (authorities == null) {
            authorities = permissions.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
        }
        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        boolean accountEnabled = Integer.valueOf(1).equals(user.getStatus());
        boolean memberEnabled = user.getEnterpriseId() == null
                || Integer.valueOf(1).equals(user.getMemberStatus());
        return accountEnabled && memberEnabled;
    }
}
