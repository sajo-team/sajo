package com.sajo.user_service.account.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.AesGcmBytesEncryptor;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Converter
@Component
public class AesGcmStringConverter implements AttributeConverter<String, String> {

    private final BytesEncryptor bytesEncryptor;

    public AesGcmStringConverter(
            @Value("${sajo.crypto.account-key}") String password,
            @Value("${sajo.crypto.account-salt}") String salt) {
        this.bytesEncryptor = AesGcmBytesEncryptor.withPassword(password, salt).build();
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        byte[] encrypted = bytesEncryptor.encrypt(attribute.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        byte[] decrypted = bytesEncryptor.decrypt(Base64.getDecoder().decode(dbData));
        return new String(decrypted, StandardCharsets.UTF_8);
    }
}
