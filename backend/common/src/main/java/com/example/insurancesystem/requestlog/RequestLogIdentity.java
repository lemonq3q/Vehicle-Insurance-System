package com.example.insurancesystem.requestlog;

/** 日志账号命名空间分类器，仅用于诊断归属，不能作为鉴权依据；兼容旧队列及历史来源。 */
public final class RequestLogIdentity {
    private RequestLogIdentity() { }
    /** 已登录账号按服务区分；无账号的内部请求归SYSTEM，其余匿名，未知账号来源明确UNKNOWN。 */
    public static String resolve(String service,Object userId,String uri) {
        if(userId!=null) {
            if("InsuranceSystemMonitor".equals(service))return "MONITOR_USER";
            if("InsuranceSystem".equals(service)||"InsuranceSystemSaas".equals(service))return "TENANT_USER";
            return "UNKNOWN";
        }
        return uri!=null&&uri.startsWith("/internal/")?"SYSTEM":"ANONYMOUS";
    }
}
