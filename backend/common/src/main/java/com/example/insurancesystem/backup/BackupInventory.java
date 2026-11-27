package com.example.insurancesystem.backup;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.time.*;
import java.util.*;

/**
 * 维护和监控共享的备份索引契约。OSS每种备份仅一份索引，按objectKey幂等覆盖记录。
 * 不依赖业务表；本地状态可扩展此协议，远端副本去除本机路径和上传阶段。
 */
public class BackupInventory {
    public int schemaVersion = 2;
    public String updatedAt;
    public Map<String, Entry> files = new LinkedHashMap<>();

    /**
     * 单文件版本及恢复属性；路径和所属日期在首次登记后固定，binlog后续更新只替换内容版本。
     * phase/localPath/lastError只用于本地恢复，COMPLETED并不要求本地大文件仍存在。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Entry implements Cloneable {
        public String id, type, serverUuid, fileName, backupDate, retainThroughDate, objectKey;
        public String localPath, sha256, startedAt, uploadedAt, lastError;
        public String database, runId, binlogStartFile, afterDumpBinlog, mysqlVersion, dumpCompletedAt;
        public long sizeBytes, sourceSize, revision, binlogStartPosition;
        public boolean closed;
        public Phase phase;

        /**
         * 返回独立远程记录，不泄露服务器路径或本地重试错误。
         */
        public Entry remoteCopy() {
            try {
                Entry result = (Entry) clone();
                result.localPath = null; result.phase = null; result.lastError = null;
                return result;
            } catch (CloneNotSupportedException e) { throw new IllegalStateException(e); }
        }
    }

    /**
     * 本地上传阶段，成功操作后才持久化推进。PENDING_INDEX可直接重复远程upsert覆盖，无需B5特判。
     */
    public enum Phase { PENDING_UPLOAD, PENDING_INDEX, COMPLETED }

    /**
     * 每日成功轮转边界；上个周期的endDate只有下次成功划分才确认，漏维护时保留开放区间。
     * 同一serverUuid下按数字文件序号分配日期，不能据扫描/上传时间猜旧日志归属。
     */
    public static class Cycle {
        public String serverUuid, startFile, backupDate, endDate;
    }

    /**
     * 先校验JSON必填结构再反序列化，避免缺少files的空对象被字段默认值误当作合法空索引。
     * 本地及远程使用同一协议入口，不兼容结构交由调用方报错，禁止静默重置已有记录。
     */
    public static void validateShape(JsonNode node)throws IOException {
        if(node==null||!node.isObject()||!node.has("schemaVersion")||!node.get("schemaVersion").isIntegralNumber()
                ||node.get("schemaVersion").asInt()!=2||!node.has("files")||!node.get("files").isObject())
            throw new IOException("备份索引缺少必要结构或协议不兼容");
    }

    /**
     * 校验索引基本结构，损坏或不兼容协议不能被当成空索引覆盖。
     */
    public void validate() throws IOException {
        if (schemaVersion != 2 || files == null) throw new IOException("备份索引格式不兼容或损坏");
        for (Map.Entry<String, Entry> item : files.entrySet()) {
            Entry entry = item.getValue();
            if (entry == null || !item.getKey().equals(entry.objectKey) || entry.id == null
                    || !Set.of("FULL", "BINLOG").contains(Objects.toString(entry.type, ""))
                    || entry.sizeBytes < 0) throw new IOException("备份索引记录不合法");
            try {
                if (entry.backupDate != null) LocalDate.parse(entry.backupDate);
                if (entry.retainThroughDate != null) LocalDate.parse(entry.retainThroughDate);
            } catch (DateTimeException e) { throw new IOException("备份归属日期不合法", e); }
        }
    }

    /**
     * 同一binlog文件名前缀内按无符号十进制序号比较；不同序列拒绝推断先后，保护恢复依赖。
     */
    public static Integer compareFiles(String left, String right) {
        if (left == null || right == null) return null;
        int a = left.lastIndexOf('.'), b = right.lastIndexOf('.');
        if (a < 0 || b < 0 || !left.substring(0, a).equals(right.substring(0, b))) return null;
        try { return new java.math.BigInteger(left.substring(a + 1)).compareTo(new java.math.BigInteger(right.substring(b + 1))); }
        catch (NumberFormatException e) { return null; }
    }
}
