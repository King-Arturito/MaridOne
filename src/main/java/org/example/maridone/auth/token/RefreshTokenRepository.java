package org.example.maridone.auth.token;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @Modifying
    void deleteByRefreshToken(String refreshToken);

    @Modifying
    @Query("delete from RefreshToken rt where rt.userAccount.username = :username")
    void deleteByUsername(@Param("username") String username);

    Optional<RefreshToken> findByRefreshToken(String refreshToken);
}
