package com.meridian.care.security;

import com.meridian.care.domain.AppUser;
import com.meridian.care.repo.AppUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    public AppUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AppUser user = users.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No user for email " + email));
        return new AppUserPrincipal(
                user.getId(),
                user.getCareHome().getId(),
                user.getCareHome().getName(),
                user.getCareHome().getSlug(),
                user.getEmail(),
                user.getDisplayName(),
                user.getPasswordHash(),
                user.getRole());
    }
}
