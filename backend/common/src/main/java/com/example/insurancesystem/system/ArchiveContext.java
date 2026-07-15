package com.example.insurancesystem.system;

public class ArchiveContext {
    private static final ThreadLocal<Boolean> ARCHIVE_FLAG = ThreadLocal.withInitial(() -> false);

    public static void setArchive() {
        ARCHIVE_FLAG.set(true);
    }

    public static void clear() {
        ARCHIVE_FLAG.remove();
    }

    public static boolean isArchive() {
        return ARCHIVE_FLAG.get();
    }
}