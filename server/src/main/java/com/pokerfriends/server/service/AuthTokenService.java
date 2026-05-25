package com.pokerfriends.server.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class AuthTokenService {
  private static final String HMAC_ALGO = "HmacSHA256";
  private final byte[] secret;

  public AuthTokenService(@Value("${app.auth.secret:poker-friends-secret}") String secret) {
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
  }

  public String issueToken(String tableId, String playerId) {
    String payload = tableId + ":" + playerId;
    String signature = sign(payload);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
        + "."
        + signature;
  }

  public boolean validate(String token, String tableId, String playerId) {
    if (token == null || !token.contains(".")) {
      return false;
    }
    String[] parts = token.split("\\.", 2);
    String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
    String expectedPayload = tableId + ":" + playerId;
    return payload.equals(expectedPayload) && sign(payload).equals(parts[1]);
  }

  private String sign(String payload) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGO);
      mac.init(new SecretKeySpec(secret, HMAC_ALGO));
      byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (Exception ex) {
      throw new IllegalStateException("Cannot sign token", ex);
    }
  }
}
