package com.example.insurancesystem.saas.integration.payment;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class MockPaymentGateway implements PaymentGateway {
  public String createPayment(String orderNo, BigDecimal amount, String channel) {
    return "MOCK-" + channel + "-" + orderNo;
  }
}
