package com.example.insurancesystem.system;

/**
 * 保存当前线程是否正在访问归档表。MyBatis 动态表名插件据此把业务表透明路由到 _archive 表，
 * ThreadLocal 避免维护线程的归档状态影响并发 HTTP 请求。
 */
public class ArchiveContext {
    private static final ThreadLocal<Boolean> ARCHIVE_FLAG = ThreadLocal.withInitial(() -> false);

    /**
     * 在当前线程开启归档表路由，后续受动态表名插件处理的 SQL 将访问同名归档表。
     */
    public static void setArchive() {
        ARCHIVE_FLAG.set(true);
    }

    /**
     * 移除当前线程归档标记而非仅写入 false，防止线程池复用时残留上下文和值对象。
     */
    public static void clear() {
        ARCHIVE_FLAG.remove();
    }

    /**
     * 返回当前线程归档状态；未设置的线程由初始值视为普通在线表访问。
     */
    public static boolean isArchive() {
        return ARCHIVE_FLAG.get();
    }
}
