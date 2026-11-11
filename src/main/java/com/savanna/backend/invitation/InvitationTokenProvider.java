package com.savanna.backend.invitation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class InvitationTokenProvider {

    private final byte[] secretKey;

    public InvitationTokenProvider(
            @Value("${invitation.secret}") String secret
    ) {
        this.secretKey = Base64.getDecoder().decode(secret);
    }

    // 초대 링크마다 고유한 식별값 생성
    public String createSeed() {
        return UUID.randomUUID().toString();
    }

    // 식별값과 서버 비밀키로 재생성 가능한 토큰 생성
    public String createToken(String seed) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(
                    new SecretKeySpec(
                            secretKey,
                            "HmacSHA256"
                    )
            );

            byte[] signature = mac.doFinal(
                    seed.getBytes(StandardCharsets.UTF_8)
            );

            return seed + "." +
                    Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(signature);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "초대 토큰 생성에 실패했습니다.", e
            );
        }
    }

    // 기존 방식과 동일한 SHA-256 해시 생성
    public String hashToken(String token) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hashed = digest.digest(
                    token.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hashed);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "초대 토큰 해시에 실패했습니다.", e
            );
        }
    }
}
