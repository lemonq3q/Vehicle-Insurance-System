package com.example.insurancesystem.handler;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

  @Test
  void returnsForbiddenForMethodSecurityDenial() {
    GlobalExceptionHandler handler = new GlobalExceptionHandler();

    ResponseResult response =
        handler.handleAccessDeniedException(new AccessDeniedException("不允许访问"), new MockHttpServletRequest("GET", "/test")).getBody();

    assertEquals(403, response.getCode());
    assertEquals("不允许访问", response.getMsg());
  }

  /**
   * 输入绑定错误和文件过大返回真实 4xx；未知内部异常仍返回 500 业务码，防止业务拒绝被当作系统报警。
   */
  @Test
  void requestMistakesAreWarningsAndUnknownFailuresRemainErrors() {
    GlobalExceptionHandler handler = new GlobalExceptionHandler();
    var invalid = handler.handleInvalidRequest(new org.springframework.beans.TypeMismatchException("bad", Long.class));
    assertEquals(400, invalid.getStatusCodeValue());
    assertEquals(400, invalid.getBody().getCode());
    var oversized = handler.handleInvalidRequest(new org.springframework.web.multipart.MaxUploadSizeExceededException(1024));
    assertEquals(413, oversized.getStatusCodeValue());
    assertEquals(413, oversized.getBody().getCode());
    assertEquals(500, handler.handleException(new RuntimeException("internal"), new MockHttpServletRequest()).getBody().getCode());
  }
}
