package org.example.maridone.core.user;

import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.enums.Position;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.security.core.userdetails.User;
//Required for Spring Security
@Service
public class UserLoginService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;

    public UserLoginService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    @ExecutionTime
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAccount user = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User account of username: " + username + " not found."));

        Position role = userAccountRepository.findPositionByUsername(username).orElse(Position.UNKNOWN);
        return User
                .withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .roles(role.name())
                .build();
    }
}
