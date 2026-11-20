package com.example.insurancesystem.requestlog;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 验证日志观察器不改变响应、不把业务内部已处理异常自动标记为错误。 */
class RequestLogFilterTest {
    /** 响应原样透传，并将业务返回码、特殊文本和耗时放入独立快照。 */
    @Test void preservesResponseAndCapturesBusinessText() throws Exception {
        RequestLogTransport transport=mock(RequestLogTransport.class);
        RequestLogFilter filter=new RequestLogFilter(transport,new ObjectMapper(),"test");
        MockHttpServletRequest request=new MockHttpServletRequest("POST","/save");
        request.setContentType("application/json");request.setContent("{\"id\":1}".getBytes());
        MockHttpServletResponse response=new MockHttpServletResponse();
        filter.doFilter(request,response,(req,res)->{
            req.getInputStream().readAllBytes();
            RequestLogContext.appendBusinessInfo("自定义信息");
            try {throw new IllegalArgumentException("已处理");}catch(IllegalArgumentException ignored) { }
            res.setContentType("application/json");res.getWriter().write("{\"code\":400,\"msg\":\"拒绝\"}");
        });
        ArgumentCaptor<Map<String,Object>> captor=ArgumentCaptor.forClass(Map.class);verify(transport).submit(captor.capture());
        assertEquals(response.getContentAsString(),captor.getValue().get("responseBody"));
        assertEquals("400",captor.getValue().get("businessCode"));assertEquals(false,captor.getValue().get("hasException"));
        assertTrue(captor.getValue().get("businessInfo").toString().contains("自定义信息"));
    }
    /** 未处理异常仍传播，日志只观察，下一请求上下文不携带上次异常。 */
    @Test void propagatesExceptionAndClearsContext() {
        RequestLogTransport transport=mock(RequestLogTransport.class);
        RequestLogFilter filter=new RequestLogFilter(transport,new ObjectMapper(),"test");
        assertThrows(IllegalStateException.class,()->filter.doFilter(new MockHttpServletRequest(),new MockHttpServletResponse(),(req,res)->{throw new IllegalStateException("失败");}));
        ArgumentCaptor<Map<String,Object>> captor=ArgumentCaptor.forClass(Map.class);verify(transport).submit(captor.capture());assertEquals(true,captor.getValue().get("hasException"));
        assertTrue(RequestLogContext.begin().snapshot().isEmpty());RequestLogContext.clear();
    }
    /** 大响应全部返回客户端，仅限制日志副本，不能截断业务数据。 */
    @Test void boundsCaptureWithoutTruncatingResponse() throws Exception {
        RequestLogTransport transport=mock(RequestLogTransport.class);
        RequestLogFilter filter=new RequestLogFilter(transport,new ObjectMapper(),"test");
        MockHttpServletResponse response=new MockHttpServletResponse();byte[] bytes=new byte[100000];Arrays.fill(bytes,(byte)'a');
        filter.doFilter(new MockHttpServletRequest(),response,(req,res)->{res.setContentType("text/plain");res.getOutputStream().write(bytes);});
        assertEquals(100000,response.getContentAsByteArray().length);
        ArgumentCaptor<Map<String,Object>> captor=ArgumentCaptor.forClass(Map.class);verify(transport).submit(captor.capture());assertEquals(true,captor.getValue().get("responseTruncated"));assertEquals(65536,captor.getValue().get("responseBody").toString().length());
    }
}
