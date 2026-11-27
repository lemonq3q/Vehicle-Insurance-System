package com.example.insurancesystem.coordinator.backup;

import com.example.insurancesystem.backup.BackupInventory;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 本地状态协议验证，只操作临时目录；保护可靠登记、路径隔离及旧记录导入边界。
 */
class BackupStateStoreTest {
    @TempDir Path root;
    final ObjectMapper mapper=new ObjectMapper();

    /**
     * 更新后主状态与上一版备份都可解析；主状态损坏不自动回退或当成空状态覆盖。
     */
    @Test void persistsAtomicallyAndRejectsCorruption()throws Exception {
        BackupStateStore store=new BackupStateStore(root,mapper,"bucket|prefix/");var state=store.load("binlog");
        DatabaseBackupService.recordCycle(state,"uuid","bin.000001","2026-11-26");store.save("binlog",state);
        assertEquals(1,store.load("binlog").cycles.size());assertNotNull(mapper.readValue(root.resolve("state/binlog.json.bak").toFile(),BackupStateStore.State.class));
        Files.writeString(root.resolve("state/binlog.json"),"broken");assertThrows(IOException.class,()->store.load("binlog"));
        Files.delete(root.resolve("state/binlog.json"));assertThrows(IOException.class,()->store.load("binlog"));
    }

    /**
     * 换Bucket或实例必须使用新的状态根目录；路径引用不能越过暂存数据目录。
     */
    @Test void rejectsNamespaceAndPathMismatch()throws Exception {
        BackupStateStore store=new BackupStateStore(root,mapper,"bucket|prefix/");store.load("full");
        assertThrows(IOException.class,()->new BackupStateStore(root,mapper,"other|prefix/").load("full"));
        assertThrows(IOException.class,()->store.ownedPath(root.resolve("state/full.json").toString()));
        assertThrows(IOException.class,()->store.ownedPath(root.resolve("../outside.sql").toString()));
    }

    /**
     * 首次初始化导入旧全量清单，只保留原始key；已上传的文件从更新索引阶段继续。
     */
    @Test void importsLegacyFullWithoutRenamingOrUploadingAgain()throws Exception {
        Path directory=root.resolve("full/legacy");Files.createDirectories(directory);
        mapper.writeValue(directory.resolve("manifest.json").toFile(),Map.of("objectKey","prefix/full/legacy/database.sql.gz","backupId","legacy","uploaded",true,"startedAt","2026-11-25T20:00:00Z","sizeBytes",10));
        BackupStateStore store=new BackupStateStore(root,mapper,"bucket|prefix/");var state=store.load("full");
        var entry=state.files.get("prefix/full/legacy/database.sql.gz");assertEquals("2026-11-26",entry.backupDate);assertEquals(BackupInventory.Phase.PENDING_INDEX,entry.phase);
    }
}
