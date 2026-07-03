package com.nodeadmin.modules.auth.service;

import com.nodeadmin.common.error.ConflictError;
import com.nodeadmin.common.error.UnauthorizedError;
import com.nodeadmin.common.model.SessionUser;
import com.nodeadmin.config.AppProperties;
import com.nodeadmin.modules.access.user.entity.UserEntity;
import com.nodeadmin.modules.access.user.repository.UserRepository;
import com.nodeadmin.modules.auth.dto.RegisterRequest;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Concrete implementation of {@link IAuthService}.
 *
 * <p>All credential operations mirror NodeAdmin's AuthController logic:
 * <ul>
 *   <li>BCrypt password verification using rounds from {@code app.bcrypt.rounds}</li>
 *   <li>Blocked-user guard (throws 401, not 403, to avoid leaking account existence)</li>
 *   <li>OTP: 6-digit numeric, bcrypt-hashed before storage, expiry from OTP_EXPIRY_MINUTES env</li>
 *   <li>JWT blacklisting on API logout via {@link IJwtService}</li>
 * </ul>
 */
@Service
@Transactional
public class AuthService implements IAuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository        userRepository;
    private final IJwtService           jwtService;
    private final JavaMailSender        mailSender;
    private final BCryptPasswordEncoder bcrypt;
    private final AppProperties         appProperties;

    public AuthService(UserRepository userRepository,
                       IJwtService jwtService,
                       AppProperties appProperties,
                       JavaMailSender mailSender) {
        this.userRepository = userRepository;
        this.jwtService     = jwtService;
        this.appProperties  = appProperties;
        this.mailSender     = mailSender;
        this.bcrypt         = new BCryptPasswordEncoder(appProperties.getBcrypt().getRounds());
    }

    // -------------------------------------------------------------------------
    // IAuthService — login
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public SessionUser loginWeb(String email, String password, boolean rememberMe) {
        UserEntity user = findAndVerify(email, password);
        return toSessionUser(user);
    }

    @Override
    @Transactional(readOnly = true)
    public String loginApi(String email, String password) {
        UserEntity user = findAndVerify(email, password);
        return jwtService.generateToken(user.getId(), user.getEmail(), "api");
    }

    @Override
    public void logoutApi(String token) {
        long remainingMs = 0L;
        if (jwtService instanceof JwtService js) {
            remainingMs = js.remainingMs(token);
        }
        jwtService.blacklistToken(token, remainingMs > 0 ? remainingMs : 3_600_000L);
    }

    // -------------------------------------------------------------------------
    // IAuthService — registration
    // -------------------------------------------------------------------------

    @Override
    public UserEntity register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictError("Email is already in use");
        }
        if (userRepository.existsByCode(request.getCode())) {
            throw new ConflictError("Code is already in use");
        }

        UserEntity user = new UserEntity();
        user.setName(request.getName());
        user.setCode(request.getCode());
        user.setEmail(request.getEmail());
        user.setPassword(bcrypt.encode(request.getPassword()));

        return userRepository.save(user);
    }

    // -------------------------------------------------------------------------
    // IAuthService — OTP password reset
    // -------------------------------------------------------------------------

    @Override
    public void requestOtp(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedError("Invalid email"));

        String otp    = generateOtp();
        String hashed = bcrypt.encode(otp);

        long expiryMs = (long) appProperties.getOtp().getExpiryMinutes() * 60 * 1_000L;
        user.setPasswordOtp(hashed);
        user.setPasswordOtpExpires(System.currentTimeMillis() + expiryMs);
        userRepository.save(user);

        sendOtpEmail(email, otp);
    }

    @Override
    public void processOtp(String email, String otp, String newPassword) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedError("Invalid or expired OTP"));

        boolean notExpired = user.getPasswordOtpExpires() != null
                && user.getPasswordOtpExpires() > System.currentTimeMillis();
        boolean otpMatches = user.getPasswordOtp() != null
                && bcrypt.matches(otp, user.getPasswordOtp());

        if (!notExpired || !otpMatches) {
            throw new UnauthorizedError("Invalid or expired OTP");
        }

        user.setPassword(bcrypt.encode(newPassword));
        user.setPasswordOtp(null);
        user.setPasswordOtpExpires(null);
        userRepository.save(user);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private UserEntity findAndVerify(String email, String password) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedError("Invalid email or password"));

        if (!bcrypt.matches(password, user.getPassword())) {
            throw new UnauthorizedError("Invalid email or password");
        }

        if (Boolean.TRUE.equals(user.getBlocked())) {
            throw new UnauthorizedError("Account is blocked");
        }

        return user;
    }

    private SessionUser toSessionUser(UserEntity user) {
        List<String> roleNames = user.getRoles().stream()
                .map(r -> r.getName())
                .collect(Collectors.toList());

        return new SessionUser(
                user.getId(),
                user.getCode(),
                user.getName(),
                user.getEmail(),
                user.getPicture(),
                user.getTimezone(),
                user.getStatus(),
                roleNames
        );
    }

    private void sendOtpEmail(String to, String plainOtp) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            helper.setTo(to);
            helper.setSubject("Your Password Reset OTP");
            helper.setText(
                "<html><body style=\"font-family:Arial,sans-serif;\">"
                + "<h2>Password Reset OTP</h2>"
                + "<p>Your one-time password is:</p>"
                + "<p style=\"font-size:2rem;font-weight:bold;letter-spacing:.3rem;\">"
                + plainOtp + "</p>"
                + "<p>This code expires in <strong>" + appProperties.getOtp().getExpiryMinutes() + " minutes</strong>.</p>"
                + "<p>If you did not request a password reset, you can safely ignore this email.</p>"
                + "</body></html>",
                true
            );
            mailSender.send(msg);
        } catch (Exception e) {
            log.warn("OTP email could not be delivered to {}: {}", to, e.getMessage());
        }
    }

    /** Generates a cryptographically random 6-digit numeric OTP. */
    private String generateOtp() {
        SecureRandom rng = new SecureRandom();
        return String.format("%06d", rng.nextInt(1_000_000));
    }
}
