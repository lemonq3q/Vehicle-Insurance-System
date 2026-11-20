package com.example.insurancesystem.requestlog;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

/** 最外层请求观察器，原样透传响应，只保留有限文本；不改变鉴权、业务状态或异常传播。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class RequestLogFilter extends OncePerRequestFilter {
    private final RequestLogTransport transport;
    private final ObjectMapper mapper;
    private final String service;
    /** 注入异步传输与JSON解析器，不在请求线程调用Redis。 */
    public RequestLogFilter(RequestLogTransport transport,ObjectMapper mapper,@Value("${spring.application.name:backend}") String service) {
        this.transport=transport; this.mapper=mapper; this.service=service;
    }
    /** 采集入口和结束状态，异常记录后原样抛出，finally清理线程上下文。 */
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        RequestLogContext context=RequestLogContext.begin();
        long started=System.currentTimeMillis(),nano=System.nanoTime();
        String id=UUID.randomUUID().toString();
        RequestLogContext.put("logId",id); RequestLogContext.put("sourceSystem",service);
        RequestLogContext.put("startedAt",started); RequestLogContext.put("method",request.getMethod());
        RequestLogContext.put("uri",request.getRequestURI()); RequestLogContext.put("query",RequestLogContext.limit(request.getQueryString()));
        RequestLogContext.put("clientIp",request.getRemoteAddr()); RequestLogContext.put("hasException",false);
        ContentCachingRequestWrapper input=new ContentCachingRequestWrapper(request,65536);
        Capture output=new Capture(response);
        boolean escaped=false;
        try { chain.doFilter(input,output); }
        catch(IOException|ServletException|RuntimeException|Error e) { escaped=true; RequestLogContext.recordException(e); throw e; }
        finally {
            try {
                output.finishWriter();
                RequestLogContext.put("durationMs",(System.nanoTime()-nano)/1000000);
                RequestLogContext.put("httpStatus",escaped && response.getStatus()<400?500:response.getStatus()); RequestLogContext.put("contentType",response.getContentType());
                RequestLogContext.put("requestBody",text(request.getContentType())?new String(input.getContentAsByteArray(),StandardCharsets.UTF_8):null);
                RequestLogContext.put("requestTruncated",input.getContentAsByteArray().length>=65536);
                String body=output.body(); RequestLogContext.put("responseBody",body);
                RequestLogContext.put("responseTruncated",output.truncated);
                if(body!=null) { try { com.fasterxml.jackson.databind.JsonNode json=mapper.readTree(body);
                    if(json!=null && json.has("code")) RequestLogContext.put("businessCode",json.get("code").asText());
                    if(json!=null) RequestLogContext.put("message",json.path("msg").asText(json.path("message").asText(null)));
                } catch(Exception ignored) { /* 非JSON或截断响应只保存原始文本。 */ } }
                Map<String,Object> snapshot=context.snapshot();
                if(request.isAsyncStarted()) {
                    snapshot.put("async",true);
                    request.getAsyncContext().addListener(new AsyncListener() {
                        /** 异步完成后才保存最终状态与耗时。 */
                        public void onComplete(AsyncEvent event) { snapshot.put("httpStatus",response.getStatus()); snapshot.put("durationMs",(System.nanoTime()-nano)/1000000); snapshot.put("responseBody",output.body()); transport.submit(snapshot); }
                        /** 超时作为请求结果，不接管容器恢复流程。 */
                        public void onTimeout(AsyncEvent event) { snapshot.put("asyncTimeout",true); }
                        /** 后台异常保留堆栈，不依赖已清理的请求线程。 */
                        public void onError(AsyncEvent event) { snapshot.put("hasException",true); StringWriter s=new StringWriter(); if(event.getThrowable()!=null) event.getThrowable().printStackTrace(new PrintWriter(s)); snapshot.put("exceptionInfo",RequestLogContext.limit(s.toString())); }
                        /** 异步重新启动时继续跟踪同一请求。 */
                        public void onStartAsync(AsyncEvent event) { event.getAsyncContext().addListener(this); }
                    });
                } else transport.submit(snapshot);
            } catch(Exception e) { logger.warn("请求日志采集失败",e); }
            finally { RequestLogContext.clear(); }
        }
    }
    /** 只采集文本类型，上传下载二进制不缓存。 */
    private static boolean text(String type) { return type!=null && (type.contains("json") || type.startsWith("text/") || type.contains("x-www-form-urlencoded") || type.contains("xml")); }
    /** 响应透传包装器，仅旁路保留前64KB文本，不整体缓存下载或流。 */
    private static class Capture extends HttpServletResponseWrapper {
        private final ByteArrayOutputStream captured=new ByteArrayOutputStream();
        private ServletOutputStream stream; private PrintWriter writer; private boolean truncated;
        /** 包装原响应而不改变状态和响应头。 */
        Capture(HttpServletResponse response) { super(response); }
        /** 旁路采集有限文本，同时立即写入原始输出流。 */
        @Override public ServletOutputStream getOutputStream() throws IOException {
            if(stream==null) { ServletOutputStream target=super.getOutputStream(); stream=new ServletOutputStream() {
                public boolean isReady() { return target.isReady(); }
                public void setWriteListener(WriteListener listener) { target.setWriteListener(listener); }
                public void write(int b) throws IOException { byte[] bytes={(byte)b}; write(bytes,0,1); }
                public void write(byte[] bytes,int offset,int length) throws IOException { target.write(bytes,offset,length); if(text(getContentType())) { int keep=Math.min(length,65536-captured.size()); captured.write(bytes,offset,keep); if(keep<length) truncated=true; } }
                public void flush() throws IOException { target.flush(); }
                public void close() throws IOException { target.close(); }
            }; } return stream;
        }
        /** 字符输出同样透传，不替换业务响应编码。 */
        @Override public PrintWriter getWriter() throws IOException { if(writer==null) writer=new PrintWriter(new OutputStreamWriter(getOutputStream(),getCharacterEncoding())); return writer; }
        /** 容器刷新时同步刷新字符缓冲。 */
        @Override public void flushBuffer() throws IOException { if(writer!=null) writer.flush(); super.flushBuffer(); }
        /** 完整重置响应时同步清除旧副本。 */
        @Override public void reset() { super.reset(); captured.reset(); truncated=false; }
        /** 重置响应时丢弃已采集的旧内容。 */
        @Override public void resetBuffer() { super.resetBuffer(); captured.reset(); truncated=false; }
        /** 刷新包装器自身字符缓冲，保证原响应与采集内容一致。 */
        void finishWriter() { if(writer!=null) writer.flush(); }
        /** 获取已写文本，不强制刷新或提交业务响应。 */
        String body() { return text(getContentType())?new String(captured.toByteArray(),java.nio.charset.Charset.forName(getCharacterEncoding())):null; }
    }
}
