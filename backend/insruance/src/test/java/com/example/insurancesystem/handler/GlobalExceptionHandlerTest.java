package com.example.insurancesystem.handler;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

  @Test
  void returnsForbiddenForMethodSecurityDenial() {
    GlobalExceptionHandler handler = new GlobalExceptionHandler();

    ResponseResult response =
        handler.handleAccessDeniedException(new AccessDeniedException("不允许访问"));

    assertEquals(403, response.getCode());
    assertEquals("不允许访问", response.getMsg());
  }
}
