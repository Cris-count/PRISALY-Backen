package com.prisaly.m1.application.service;

import com.prisaly.m1.application.port.AuthenticatedStudent;
import com.prisaly.m1.domain.rule.IdentityDomainException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Minimal DD-02 validation path for {@code /validar}.
 * Challenges live in memory until OTP persistence / delivery is authorized.
 * Failed validation denies access (same posture as {@link DenyingStudentAccessService}).
 */
@Service
public class StudentAccessValidationService {

    public static final String CODE_STUDENT_ACCESS_DENIED = "STUDENT_ACCESS_DENIED";
    public static final String ESTADO_VALIDADO = "VALIDADO";

    private final ConcurrentHashMap<String, PendingChallenge> challenges = new ConcurrentHashMap<>();

    /**
     * Registers a one-time challenge for validation evidence / tests.
     * Not an OTP delivery API; solicitation and SMTP remain out of scope here.
     */
    public void registerChallenge(
            String challengeId,
            String codigo,
            UUID userId,
            UUID studentId) {
        Objects.requireNonNull(challengeId, "challengeId");
        Objects.requireNonNull(codigo, "codigo");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(studentId, "studentId");
        if (challengeId.isBlank()) {
            throw new IllegalArgumentException("challengeId must not be blank");
        }
        if (!codigo.matches("\\d{8}")) {
            throw new IllegalArgumentException("codigo must be exactly 8 digits");
        }
        challenges.put(challengeId, new PendingChallenge(codigo, userId, studentId, false));
    }

    public ValidatedStudentAccess validate(String challengeId, String codigo) {
        Objects.requireNonNull(challengeId, "challengeId");
        Objects.requireNonNull(codigo, "codigo");

        PendingChallenge pending = challenges.get(challengeId);
        if (pending == null || pending.consumed || !pending.codigo.equals(codigo)) {
            throw new IdentityDomainException(
                    CODE_STUDENT_ACCESS_DENIED,
                    "Student access denied: challenge missing, code invalid, or already consumed");
        }

        PendingChallenge consumed = new PendingChallenge(
                pending.codigo, pending.userId, pending.studentId, true);
        boolean replaced = challenges.replace(challengeId, pending, consumed);
        if (!replaced) {
            throw new IdentityDomainException(
                    CODE_STUDENT_ACCESS_DENIED,
                    "Student access denied: challenge missing, code invalid, or already consumed");
        }

        return new ValidatedStudentAccess(
                new AuthenticatedStudent(pending.userId, pending.studentId),
                challengeId,
                ESTADO_VALIDADO);
    }

    public record ValidatedStudentAccess(
            AuthenticatedStudent student,
            String challengeId,
            String estado
    ) {
    }

    private record PendingChallenge(
            String codigo,
            UUID userId,
            UUID studentId,
            boolean consumed
    ) {
    }
}
