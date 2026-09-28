package com.example.insurancesystem.saas.payment;

import com.example.insurancesystem.handler.GlobalExceptionHandler;
import com.example.insurancesystem.handler.HttpBusinessExceptionHandler;
import com.example.insurancesystem.handler.exception.HttpBusinessException;
import com.example.insurancesystem.handler.AuthenticationEntryPointImpl;
import com.example.insurancesystem.handler.AccessDeniedHandlerImpl;
import com.example.insurancesystem.handler.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证支付回调整个HTTP异常出口，不连接Stripe或数据库。真实非2xx状态是外部平台重试的依据，
 * 包括维护拒绝以外的验签、临时业务故障、参数绑定和安全拒绝，不能仅在JSON内声明失败。
 */
class StripeWebhookHttpStatusTest {
    /**
     * 控制器成功及重复事件为200，缺少签名为400，服务503及未知故障500均保留真实失败状态。
     * 缺少请求体的绑定异常发生于控制器之前，必须由全局异常处理器输出非2xx。
     */
    @Test
    void webhookMvcErrorsNeverBecomeSuccessfulDelivery() throws Exception {
        StripePaymentService service = mock(StripePaymentService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new StripeWebhookController(service), new StripePaymentController(service))
                .setControllerAdvice(new GlobalExceptionHandler(), new StripeExceptionHandler(), new HttpBusinessExceptionHandler()).build();
        String path = "/portal/payment/stripe/webhook";
        mvc.perform(post(path).content("{}").header("Stripe-Signature", "test")).andExpect(status().isOk());
        mvc.perform(post(path).content("{}")).andExpect(status().isBadRequest());
        doThrow(new HttpBusinessException(503, "支付暂不可用")).when(service).handleWebhook("{}", "test");
        mvc.perform(post(path).content("{}").header("Stripe-Signature", "test")).andExpect(status().isServiceUnavailable());
        doThrow(new IllegalStateException("database unavailable")).when(service).handleWebhook("{}", "test");
        mvc.perform(post(path).content("{}").header("Stripe-Signature", "test")).andExpect(status().isInternalServerError());
        mvc.perform(post(path).header("Stripe-Signature", "test")).andExpect(status().isBadRequest());
        doThrow(new BusinessException(409, "原付款未确认")).when(service).handleWebhook("{}", "test");
        mvc.perform(post(path).content("{}").header("Stripe-Signature", "test")).andExpect(status().isConflict());
        when(service.createCheckoutSession(1L)).thenThrow(new IllegalStateException("database unavailable"));
        mvc.perform(post("/portal/payment/stripe/recharge-orders/1/checkout-session")).andExpect(status().isInternalServerError());
        mvc.perform(post("/portal/payment/stripe/recharge-orders/invalid/checkout-session")).andExpect(status().isBadRequest());
    }

    /**
     * 回调前置全局异常保持错误状态，普通接口仍保留历史HTTP200业务外壳，避免隐式迁移所有接口。
     */
    @Test
    void globalAdvicePreservesWebhookFailureAndLegacyBusinessContract() {
        HttpBusinessExceptionHandler httpHandler = new HttpBusinessExceptionHandler();
        assertEquals(503, httpHandler.handle(new HttpBusinessException(503, "维护中")).getStatusCodeValue());
        assertThrows(IllegalArgumentException.class, () -> new HttpBusinessException(200, "非法状态"));
        StripeExceptionHandler stripeHandler = new StripeExceptionHandler();
        assertEquals(403, stripeHandler.handleDenied(new AccessDeniedException("拒绝")).getStatusCodeValue());
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest normal = new MockHttpServletRequest("POST", "/portal/finance/test");
        assertEquals(200, handler.handleBusinessException(new BusinessException(503, "维护中"), normal).getStatusCodeValue());
    }

    /**
     * 回调业务入口主动转换嵌套财务业务失败，配置缺失也必须抛出特殊异常而非依赖URL判断。
     */
    @Test
    void webhookServiceActivelyThrowsHttpBusinessException() {
        StripePaymentProperties properties = new StripePaymentProperties();
        properties.setEnabled(false);
        StripePaymentService service = new StripePaymentService(properties, null, null, null, null);
        HttpBusinessException exception = assertThrows(HttpBusinessException.class, () -> service.handleWebhook("{}", "test"));
        assertEquals(503, exception.getCode());
    }

    /**
     * 安全响应写流工具不应将调用方401/403覆盖成200；被认证层拒绝的外部回调也必须能重试。
     */
    @Test
    void securityWritersPreserveRealErrorStatus() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/portal/payment/stripe/webhook");
        AuthenticationEntryPointImpl entry = new AuthenticationEntryPointImpl();
        ReflectionTestUtils.setField(entry, "objectMapper", new ObjectMapper());
        MockHttpServletResponse unauthorized = new MockHttpServletResponse();
        entry.commence(request, unauthorized, new InsufficientAuthenticationException("认证失败"));
        assertEquals(401, unauthorized.getStatus());
        AccessDeniedHandlerImpl denied = new AccessDeniedHandlerImpl();
        ReflectionTestUtils.setField(denied, "objectMapper", new ObjectMapper());
        MockHttpServletResponse forbidden = new MockHttpServletResponse();
        denied.handle(request, forbidden, new AccessDeniedException("无权限"));
        assertEquals(403, forbidden.getStatus());
    }
}
