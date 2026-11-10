package com.ecommerce.FerreViky.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class CodeHasher {
    private final SecretKeySpec key;

    public CodeHasher(@Value("${app.crypto.codes-hmac-key}") String b64Key) {
        this.key = new SecretKeySpec(Base64.getDecoder().decode(b64Key), "HmacSHA256");
    }

    public String hash(String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(code.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}