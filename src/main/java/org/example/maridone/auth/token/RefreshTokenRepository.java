package org.example.maridone.auth.token;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @Modifying
    void deleteByRefreshToken(String refreshToken);

    Optional<RefreshToken> findByRefreshToken(String refreshToken);
}
