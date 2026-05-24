package io.velo.was.aiplatform.tenant;

public record AiTenantKeyRotationResult(AiTenantIssuedKey newKey,
                                        String rotatedKeyId,
                                        long graceExpiresAtEpochMillis,
                                        boolean oldKeyStillActive) {
}
