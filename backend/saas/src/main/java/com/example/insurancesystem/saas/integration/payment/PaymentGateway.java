package com.example.insurancesystem.saas.integration.payment;

import java.math.BigDecimal;

public interface PaymentGateway {
  String createPayment(String orderNo, BigDecimal amount, String channel);
}
