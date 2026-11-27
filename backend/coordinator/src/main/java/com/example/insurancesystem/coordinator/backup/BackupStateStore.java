package com.example.insurancesystem.coordinator.backup;

import com.example.insurancesystem.backup.BackupInventory;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/**
 * 本地两份状态的持久化边界。统一记录A2/A4/A6及周期，临时文件force后原子替换并保留bak。
 * 主状态损坏时拒绝写入和清理；不自动以空状态或旧bak覆盖较新的进度。
 */
public class BackupStateStore {
    private final Path root;
    private final ObjectMapper mapper;
    private final String namespace;

    /**
     * 本地状态绑定Bucket及实例前缀，避免改配置后复用旧状态造成跨目录上传或删除。
     */
    public BackupStateStore(Path root, ObjectMapper mapper, String namespace) throws IOException {
        this.root = root.toAbsolutePath().normalize(); this.mapper = mapper; this.namespace = namespace;
        Files.createDirectories(this.root.resolve("state"));
    }

    /**
     * 本地状态扩展远程协议，周期边界只用于协调器日期分配和保守清理。
     */
    public static class State extends BackupInventory {
        public String namespace;
        public List<Cycle> cycles = new ArrayList<>();
    }

    /**
     * 读取并校验整份状态；首次创建可导入旧版本地清单，原OSS对象不改名、不删除。
     */
    public State load(String type) throws IOException {
        Path file = statePath(type);
        if (Files.exists(file)) {
            com.fasterxml.jackson.databind.JsonNode node=mapper.readTree(file.toFile());BackupInventory.validateShape(node);
            State state = mapper.treeToValue(node, State.class);
            validate(state); return state;
        }
        if (Files.exists(file.resolveSibling(file.getFileName() + ".bak")))
            throw new IOException("本地状态缺失但存在备份，请人工检查恢复后再运行");
        State state = new State(); state.namespace = namespace;
        importLegacy(type, state);
        save(type, state);
        return state;
    }

    /**
     * 状态推进必须落盘成功后才继续下一步；失败时调用方停止批次并在下一轮重新读盘。
     */
    public void save(String type, State state) throws IOException {
        validate(state); state.updatedAt = Instant.now().toString(); Path target = statePath(type);
        Path temp = Files.createTempFile(target.getParent(), type + "-", ".writing");
        try {
            mapper.writeValue(temp.toFile(), state);
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) { channel.force(true); }
            if (Files.exists(target)) {
                Path backup = Files.createTempFile(target.getParent(), type + "-", ".bak-writing");
                try {
                    Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
                    try (FileChannel channel = FileChannel.open(backup, StandardOpenOption.WRITE)) { channel.force(true); }
                    Files.move(backup, target.resolveSibling(target.getFileName() + ".bak"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } finally { Files.deleteIfExists(backup); }
            }
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }

    /**
     * 本地文件引用必须属于本模块数据目录，不跟随符号链接访问任意服务器文件。
     */
    public Path ownedPath(String value) throws IOException {
        Path path = Path.of(value).toAbsolutePath().normalize();
        if (!(path.startsWith(root.resolve("full")) || path.startsWith(root.resolve("binlog"))))
            throw new IOException("本地备份路径越界");
        Path cursor = path;
        while (cursor != null && cursor.startsWith(root)) {
            if (Files.isSymbolicLink(cursor)) throw new IOException("备份路径不能经过符号链接");
            cursor = cursor.getParent();
        }
        return path;
    }

    /**
     * 校验基础协议、命名空间及本地阶段，任何损坏都阻止孤立文件清理。
     */
    private void validate(State state) throws IOException {
        if (state == null || !namespace.equals(state.namespace) || state.cycles == null)
            throw new IOException("本地备份状态损坏或与当前OSS位置不一致");
        state.validate();
        for (BackupInventory.Entry entry : state.files.values()) {
            if (entry.phase == null) throw new IOException("本地备份阶段缺失");
            if (entry.localPath != null) ownedPath(entry.localPath);
        }
        for (BackupInventory.Cycle cycle : state.cycles) {
            try {
                if (cycle.serverUuid == null || cycle.startFile == null) throw new IllegalArgumentException();
                LocalDate.parse(cycle.backupDate);
                if (cycle.endDate != null && LocalDate.parse(cycle.endDate).isBefore(LocalDate.parse(cycle.backupDate))) throw new IllegalArgumentException();
            } catch (RuntimeException e) { throw new IOException("本地binlog周期损坏", e); }
        }
    }

    /**
     * 只允许两种固定状态文件名，不接受用户传入路径。
     */
    private Path statePath(String type) {
        if (!Set.of("full", "binlog").contains(type)) throw new IllegalArgumentException("备份类型不合法");
        return root.resolve("state").resolve(type + ".json");
    }

    /**
     * 首次升级兼容旧本地manifest/单文件状态；无法确定的旧binlog日期保持未知，等待人工处理。
     */
    private void importLegacy(String type, State state) throws IOException {
        Path directory = root.resolve(type.equals("full") ? "full" : "state");
        if (!Files.isDirectory(directory)) return;
        try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
            for (Path file : (Iterable<Path>) paths::iterator) {
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                String name = file.getFileName().toString();
                if (type.equals("full") ? !name.equals("manifest.json") : !name.endsWith(".json") || name.equals("full.json") || name.equals("binlog.json")) continue;
                Map<String,Object> old = mapper.readValue(file.toFile(), new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>() {});
                if (old == null || !(old.get("objectKey") instanceof String)) throw new IOException("旧版备份状态无法安全迁移：" + name);
                BackupInventory.Entry entry = new BackupInventory.Entry();
                entry.type = type.equals("full") ? "FULL" : "BINLOG";
                entry.objectKey = (String) old.get("objectKey"); entry.id = Objects.toString(old.getOrDefault("backupId", entry.objectKey));
                entry.serverUuid = (String) old.get("serverUuid"); entry.fileName = type.equals("full") ? "database.sql.gz" : (String) old.get("file");
                entry.sizeBytes = ((Number) old.getOrDefault("sizeBytes", 0)).longValue(); entry.sourceSize = ((Number) old.getOrDefault("sourceSize", entry.sizeBytes)).longValue();
                entry.sha256 = (String) old.get("sha256"); entry.uploadedAt = (String) old.get("uploadCompletedAt");
                entry.revision = 1; entry.closed = type.equals("full") || Boolean.TRUE.equals(old.get("closed"));
                if (type.equals("full")) {
                    entry.startedAt = (String) old.get("startedAt"); entry.database = (String) old.get("database"); entry.runId = (String) old.get("runId");
                    entry.backupDate = (String) old.get("dataDate");
                    if (entry.backupDate == null && entry.startedAt != null) entry.backupDate = Instant.parse(entry.startedAt).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate().toString();
                    entry.retainThroughDate = entry.backupDate; entry.binlogStartFile = (String) old.get("binlogStartFile");
                    entry.binlogStartPosition = ((Number) old.getOrDefault("binlogStartPosition", 0)).longValue();
                    entry.afterDumpBinlog = (String) old.get("afterDumpBinlog");
                    entry.localPath = ownedPath(file.getParent().resolve("database.sql.gz").toString()).toString();
                    entry.phase = Boolean.TRUE.equals(old.get("uploaded")) ? BackupInventory.Phase.PENDING_INDEX : BackupInventory.Phase.PENDING_UPLOAD;
                } else entry.phase = BackupInventory.Phase.PENDING_INDEX;
                state.files.put(entry.objectKey, entry);
            }
        }
    }
}
