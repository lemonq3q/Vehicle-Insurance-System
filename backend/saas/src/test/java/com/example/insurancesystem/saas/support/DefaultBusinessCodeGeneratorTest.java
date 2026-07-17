package com.example.insurancesystem.saas.support;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DefaultBusinessCodeGeneratorTest {
  private final DefaultBusinessCodeGenerator generator = new DefaultBusinessCodeGenerator();

  @Test
  void generatesReadableBusinessPrefixesAndDifferentValues() {
    String first = generator.inviteCode();
    String second = generator.inviteCode();
    assertTrue(first.matches("XMEB-[23456789A-HJ-NP-Z]{8}"));
    assertNotEquals(first, second);
    assertTrue(generator.enterpriseCode().startsWith("ENT-"));
    assertTrue(generator.rechargeNo().matches("RC\\d{8}[23456789A-HJ-NP-Z]{5}"));
    assertTrue(generator.subscriptionOrderNo().matches("SO\\d{8}[23456789A-HJ-NP-Z]{5}"));
    assertTrue(generator.transactionNo().matches("TX\\d{8}[23456789A-HJ-NP-Z]{5}"));
  }
}
