package org.styli.services.order.jwt.security.jwtsecurity.model;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class JwtUserDetails implements UserDetails {

  /**
   *
   */
  private static final long serialVersionUID = 1L;
  private String usrName;
  private String token;
  private String password;
  private Collection<? extends GrantedAuthority> authorities;

  public JwtUserDetails(String userName, String password, String token, List<GrantedAuthority> grantedAuthorities) {

    this.usrName = userName;
    this.password = password;
    this.token = token;
    this.authorities = grantedAuthorities;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return authorities;
  }

  @Override
  public String getPassword() {
    return password;
  }

  @Override
  public String getUsername() {
    return usrName;
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
    return true;
  }

 
  public String getUsrName() {
	return usrName;
}

public String getToken() {
    return token;
  }

  public void setUserName(String userName) {
    this.usrName = userName;
  }

  public void setPassword(String password) {
    this.password = password;
  }

}
