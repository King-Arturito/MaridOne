package org.example.maridone.auth.twofactor;

import org.example.maridone.core.user.UserAccount;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.exception.notfound.AccountNotFoundException;
import org.example.maridone.exception.badrequest.UnauthorizedAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TwoFactorService {

    private final UserAccountRepository userAccountRepository;
    private final TotpService totpService;

    public TwoFactorService(UserAccountRepository userAccountRepository, TotpService totpService) {
        this.userAccountRepository = userAccountRepository;
        this.totpService = totpService;
    }

    @Transactional
    public TwoFactorSetupResponse generateSetupDetails(String username) {
        UserAccount userAccount = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));

        //generate a cryptographic secret
        String secret = totpService.generateSecret();

        //temp save, but 2fa still disabled
        userAccount.setTwoFactorSecret(secret);
        userAccount.setTwoFactorEnabled(false);
        userAccountRepository.save(userAccount);

        //Base64 QR Code image URI for the frontend
        String qrCodeImageUri = totpService.getQrCodeImageUri(secret, username);

        return new TwoFactorSetupResponse(qrCodeImageUri, secret);
    }


    @Transactional
    public void confirmAndEnable2fa(String username, String code) {
        UserAccount userAccount = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));

        String savedSecret = userAccount.getTwoFactorSecret();
        if (savedSecret == null) {
            throw new IllegalStateException("2FA setup was not initiated. Please generate a QR code first.");
        }

        //verify the code with the unconfirmed secret
        boolean isCodeValid = totpService.verifyCode(savedSecret, code);

        if (!isCodeValid) {
            throw new UnauthorizedAccessException("Invalid 2FA code. Setup failed.");
        }

        //enable 2fa now
        userAccount.setTwoFactorEnabled(true);
        userAccountRepository.save(userAccount);
    }


    //disable 2fa if desired
    @Transactional
    public Void disable2fa(String username) {
        UserAccount userAccount = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));

        userAccount.setTwoFactorEnabled(false);
        userAccount.setTwoFactorSecret(null);
        userAccountRepository.save(userAccount);
        return null;
    }
}