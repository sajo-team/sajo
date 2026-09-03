package com.sajo.user_service.account.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

@Component
public class HmacSha256Hasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec secretKey;

    public HmacSha256Hasher(@Value("${sajo.crypto.account-hash-key}") String key) {
        this.secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String hash(String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(secretKey);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("해시 생성에 실패했습니다", e);
        }
    }
}
