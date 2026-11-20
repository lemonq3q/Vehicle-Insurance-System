package com.example.insurancesystem.requestlog;

import java.io.*;
import java.util.*;

/** 请求线程诊断上下文，只保存日志，不参与业务状态；请求退出必须清理。 */
public final class RequestLogContext {
    private static final ThreadLocal<RequestLogContext> LOCAL = new ThreadLocal<>();
    private final Map<String,Object> data = new LinkedHashMap<>();
    private final Set<Throwable> errors = Collections.newSetFromMap(new IdentityHashMap<>());
    /** 创建请求上下文，供当前线程追加诊断信息。 */
    public static RequestLogContext begin() { RequestLogContext c=new RequestLogContext(); LOCAL.set(c); return c; }
    /** 业务按需设置任意格式文本；无上下文时无副作用。 */
    public static void setBusinessInfo(String value) { put("businessInfo",limit(value)); }
    /** 业务追加任意诊断文本，不解释其语义。 */
    public static void appendBusinessInfo(String value) { if(LOCAL.get()!=null) setBusinessInfo(Objects.toString(LOCAL.get().data.get("businessInfo"),"")+"\n"+value); }
    /** 记录边界捕获的真实异常；同一对象只记录一次，业务内部异常不自动接入。 */
    public static void recordException(Throwable error) {
        RequestLogContext c=LOCAL.get(); if(c==null || error==null || !c.errors.add(error)) return;
        StringWriter out=new StringWriter(); error.printStackTrace(new PrintWriter(out));
        put("hasException",true); put("exceptionInfo",limit(Objects.toString(c.data.get("exceptionInfo"),"")+out));
    }
    /** 写入基础设施采集字段。 */
    public static void put(String key,Object value) { if(LOCAL.get()!=null) LOCAL.get().data.put(key,value); }
    /** 生成与当前线程无关的快照供后台发送。 */
    public Map<String,Object> snapshot() { return new LinkedHashMap<>(data); }
    /** 请求退出清理，避免线程复用串数据。 */
    public static void clear() { LOCAL.remove(); }
    /** 限制扩展文本长度，防止无限占用内存；不脱敏。 */
    public static String limit(String value) { return value==null?null:value.substring(0,Math.min(value.length(),65536)); }
}
