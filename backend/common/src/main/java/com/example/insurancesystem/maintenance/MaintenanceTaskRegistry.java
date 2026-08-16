package com.example.insurancesystem.maintenance;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 收集当前业务服务中注册的维护任务，提供联机按编号查找和单机按代码顺序遍历。
 * 启动时发现重复 taskId 会直接失败，避免协调器将任务下发到不确定实现。
 */
@Component
public class MaintenanceTaskRegistry {
    private final Map<String, MaintenanceTask> tasks;

    public MaintenanceTaskRegistry(List<MaintenanceTask> taskList) {
        this.tasks = taskList.stream().collect(Collectors.toUnmodifiableMap(
                MaintenanceTask::taskId, Function.identity(),
                (left, right) -> { throw new IllegalStateException("Duplicate maintenance task: " + left.taskId()); }));
    }

    public Optional<MaintenanceTask> find(String taskId) { return Optional.ofNullable(tasks.get(taskId)); }

    public List<MaintenanceTask> standaloneTasks() {
        return tasks.values().stream().filter(MaintenanceTask::standaloneEnabled)
                .sorted(Comparator.comparingInt(MaintenanceTask::order)).collect(Collectors.toList());
    }
}
