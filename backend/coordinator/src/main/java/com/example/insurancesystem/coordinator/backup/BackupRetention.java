package com.example.insurancesystem.coordinator.backup;

import com.example.insurancesystem.backup.BackupInventory;
import com.example.insurancesystem.backup.BackupInventory.Entry;
import java.time.*;
import java.util.*;

/**
 * 备份保留规则：查询用backupDate，删除用保守结束日，并保护最老有效全量的恢复依赖。
 * 未知日期、未闭合周期或无法比较的日志序列一律延后，不使用uploadedAt推断过期。
 */
public final class BackupRetention {
    private BackupRetention() { }

    /**
     * 对未知/开放周期不判过期；窗口边界为今天减去retentionDays-1个自然日。
     */
    public static boolean expired(Entry entry, LocalDate cutoff) {
        if(entry.backupDate==null)return false;
        String reference="BINLOG".equals(entry.type)?entry.retainThroughDate:Objects.toString(entry.retainThroughDate,entry.backupDate);
        return reference!=null&&LocalDate.parse(reference).isBefore(cutoff)&&(!"BINLOG".equals(entry.type)||entry.closed);
    }

    /**
     * 每个MySQL实例保留窗口之前最近一份全量，即使长时间无新备份也不删除最后的恢复起点。
     */
    public static Set<String> fullAnchors(Collection<Entry> full,LocalDate cutoff) {
        Map<String,Entry> anchors=new HashMap<>();
        for(Entry entry:full){if(!"FULL".equals(entry.type)||entry.backupDate==null||!LocalDate.parse(entry.backupDate).isBefore(cutoff))continue;
            String uuid=Objects.toString(entry.serverUuid,"");Entry old=anchors.get(uuid);
            if(old==null||entry.backupDate.compareTo(old.backupDate)>0||entry.backupDate.equals(old.backupDate)&&entry.objectKey.compareTo(old.objectKey)>0)anchors.put(uuid,entry);
        }
        Set<String> result=new HashSet<>();for(Entry entry:anchors.values())result.add(entry.objectKey);return result;
    }

    /**
     * 只清除早于所有保留全量起点的日志；缺少可信全量或序列不可比较时保守保护。
     */
    public static boolean neededByFull(Entry binlog,Collection<Entry> full,LocalDate cutoff) {
        Set<String> anchors=fullAnchors(full,cutoff);boolean found=false;
        for(Entry entry:full){if(!"FULL".equals(entry.type)||!Objects.equals(entry.serverUuid,binlog.serverUuid))continue;
            if(expired(entry,cutoff)&&!anchors.contains(entry.objectKey))continue;
            found=true;Integer comparison=BackupInventory.compareFiles(binlog.fileName,entry.binlogStartFile);
            if(comparison==null||comparison>=0)return true;
        }
        return !found;
    }
}
