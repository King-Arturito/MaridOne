package org.example.maridone.auth.token;

import jakarta.persistence.*;
import org.example.maridone.core.user.UserAccount;

import java.time.Instant;

@Entity
@Table(name = "refresh_token")
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refresh_id")
    private Long refreshId;

    @Column(name = "refresh_token", nullable = false, unique = true)
    private String refreshToken;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "username", nullable = false)
    private UserAccount userAccount;

    @Column(name = "expiry_date", nullable = false)
    private Instant expiryDate;

    public Long getRefreshId() {
        return refreshId;
    }

    public void setRefreshId(Long refreshId) {
        this.refreshId = refreshId;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public void setUserAccount(UserAccount userAccount) {
        this.userAccount = userAccount;
    }

    public Instant getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(Instant expiryDate) {
        this.expiryDate = expiryDate;
    }
}
