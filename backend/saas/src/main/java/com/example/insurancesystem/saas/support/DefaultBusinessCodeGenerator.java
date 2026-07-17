package com.example.insurancesystem.saas.support;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
public class DefaultBusinessCodeGenerator implements BusinessCodeGenerator {
  private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
  private final SecureRandom random = new SecureRandom();

  public String enterpriseCode() {
    return "ENT-" + timestamp() + "-" + random(5);
  }

  public String inviteCode() {
    return "XMEB-" + random(8);
  }

  public String rechargeNo() {
    return "RC" + date() + random(5);
  }

  public String subscriptionOrderNo() {
    return "SO" + date() + random(5);
  }

  public String transactionNo() {
    return "TX" + date() + random(5);
  }

  private String timestamp() {
    return LocalDateTime.now().format(TIME);
  }

  private String date() {
    return LocalDateTime.now().format(DATE);
  }

  private String random(int length) {
    StringBuilder value = new StringBuilder(length);
    for (int i = 0; i < length; i++) value.append(ALPHABET[random.nextInt(ALPHABET.length)]);
    return value.toString();
  }
}
