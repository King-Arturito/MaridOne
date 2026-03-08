package org.example.maridone.core.user;

import java.util.Optional;

import org.example.maridone.enums.Position;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class UserLoginServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @InjectMocks
    private UserLoginService userLoginService;

    @Test
    void loadUserByUsername_ShouldReturnUserDetails_WhenFound() {
        String username = "frieren";
        UserAccount account = new UserAccount();
        account.setUsername(username);
        account.setPasswordHash("hashedPass");

        when(userAccountRepository.findByUsername(username)).thenReturn(Optional.of(account));
        when(userAccountRepository.findPositionByUsername(username)).thenReturn(Optional.of(Position.MANAGER));

        UserDetails result = userLoginService.loadUserByUsername(username);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(username, result.getUsername());
        Assertions.assertTrue(result.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER")));
    }

    @Test
    void loadUserByUsername_ShouldThrow_WhenNotFound() {
        String username = "unknown";
        when(userAccountRepository.findByUsername(username)).thenReturn(Optional.empty());

        Assertions.assertThrows(UsernameNotFoundException.class, () -> {
            userLoginService.loadUserByUsername(username);
        });
    }
}
