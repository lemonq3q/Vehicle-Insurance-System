package com.example.insurancesystem.saas.service.impl;

import com.example.insurancesystem.saas.config.BalanceAccessProperties;
import com.example.insurancesystem.saas.config.EnterpriseDataRetentionProperties;
import com.example.insurancesystem.saas.config.ReminderProperties;
import com.example.insurancesystem.saas.config.WorkorderOverageBillingProperties;
import com.example.insurancesystem.saas.integration.client.MonitorReminderClient;
import com.example.insurancesystem.saas.mapper.ReminderMapper;
import com.example.insurancesystem.saas.service.ReminderService;
import com.example.insurancesystem.saas.support.PortalContextService;
import com.example.insurancesystem.saas.support.PortalMaps;
import com.example.insurancesystem.saas.support.ReminderType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 根据订阅、余额与工单快照生成九类企业风险提醒，并实现阶段合并。
 * 每日维护先复核失效，再按同一业务快照新增、恢复、升级或降级；稳定键防止重复创建。
 * 门户永久展示仍生效的提醒，生命周期版本与待同步标记负责跨服务重试和乱序保护。
 */
@Service
public class ReminderServiceImpl implements ReminderService {
    private static final Logger log = LoggerFactory.getLogger(ReminderServiceImpl.class);
    private static final Set<String> KNOWN_TYPES = java.util.Arrays.stream(ReminderType.values()).map(Enum::name).collect(Collectors.toSet());
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final ReminderMapper mapper;
    private final PortalContextService context;
    private final MonitorReminderClient monitor;
    private final ReminderProperties reminderProperties;
    private final BalanceAccessProperties balanceProperties;
    private final EnterpriseDataRetentionProperties retentionProperties;
    private final WorkorderOverageBillingProperties overageProperties;
    private final ObjectMapper objectMapper;

    public ReminderServiceImpl(ReminderMapper mapper, PortalContextService context,
            MonitorReminderClient monitor, ReminderProperties reminderProperties,
            BalanceAccessProperties balanceProperties, EnterpriseDataRetentionProperties retentionProperties,
            WorkorderOverageBillingProperties overageProperties, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.context = context;
        this.monitor = monitor;
        this.reminderProperties = reminderProperties;
        this.balanceProperties = balanceProperties;
        this.retentionProperties = retentionProperties;
        this.overageProperties = overageProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 每日维护读取当前企业快照并计算应有提醒，先分批失效旧风险，再新增、恢复及双向调整阶段。
     * 两阶段复用同一计算结果，避免重复查询导致同一次维护先失效又恢复；不使用全轮数据库事务。
     * 升级或恢复会重新提示，降级保留已读历史；同步失败保留持久化标记等待后续每日维护补偿。
     */
    @Override
    public synchronized void generateDailyReminders() {
        LocalDateTime now = LocalDateTime.now();
        Map<Long, List<Map<String, Object>>> expected = new HashMap<>();
        for (Map<String, Object> source : PortalMaps.camel(mapper.findReminderCandidates())) {
            expected.computeIfAbsent(number(source, "enterpriseId"), ignored -> new ArrayList<>())
                    .addAll(collectReminders(source, now));
        }
        int batchSize = Math.max(1, Math.min(reminderProperties.getReconciliationBatchSize(), 500));
        reconcileActiveReminders(expected, now, batchSize);
        /*
         * 同键失效记录由upgrade恢复；仍生效记录仅在阶段变化时写入，下降单独保留阅读及触发历史。
         * 此阶段不调用远端，全部本地状态调整结束后统一补偿待同步记录。
         */
        for (List<Map<String, Object>> reminders : expected.values()) {
            for (Map<String, Object> reminder : reminders) {
                if (mapper.insert(reminder) == 0 && mapper.upgrade(reminder) == 0) mapper.downgrade(reminder);
            }
        }
        synchronizePending(batchSize);
    }

    /**
     * 使用本轮已计算的提醒快照分批复核稳定键；只失效已知业务类型，未知扩展保守保留。
     * 游标只控制单批读取量，一次每日维护持续扫描至结束；版本条件保护并发状态变化。
     */
    private void reconcileActiveReminders(Map<Long, List<Map<String, Object>>> expected,
            LocalDateTime now, int batchSize) {
        Map<Long, Set<String>> validKeys = new HashMap<>();
        expected.forEach((enterpriseId, reminders) -> validKeys.put(enterpriseId,
                reminders.stream().map(row -> string(row, "reminderKey")).collect(Collectors.toSet())));
        long cursor = 0;
        while (true) {
            List<Map<String, Object>> batch = PortalMaps.camel(mapper.findActiveBatch(cursor, batchSize));
            if (batch.isEmpty()) break;
            for (Map<String, Object> reminder : batch) {
                if (KNOWN_TYPES.contains(string(reminder, "reminderType"))
                        && !validKeys.getOrDefault(number(reminder, "enterpriseId"), Set.of()).contains(string(reminder, "reminderKey"))) {
                    mapper.invalidate(number(reminder, "id"), number(reminder, "lifecycleVersion"), now);
                }
                cursor = number(reminder, "id");
            }
        }
    }


    /**
     * 只计算候选提醒，不产生数据库或网络副作用。每日生成与定时复核使用完全相同的阈值和稳定键。
     * 参数来自数据库当前业务快照，返回可能为空的提醒集合；不存在的业务日期不会构造错误提醒。
     */
    private List<Map<String, Object>> collectReminders(Map<String, Object> source, LocalDateTime now) {
        List<Map<String, Object>> reminders = new ArrayList<>();
        synchronizeUnavailableAutoRenewPlanReminder(source, now, reminders);
        generateExpiryReminders(source, now, reminders);
        generateQuotaReminders(source, now, reminders);
        generateBalanceReminders(source, now, reminders);
        generateDataDeletionReminder(source, now, reminders);
        return reminders;
    }

    /**
     * 主键游标遍历持久化同步意图，包括已失效记录；失败保留sync_pending供下一次运行重试。
     * 本轮继续处理后续记录，不因第一条网络失败而饿死其他企业，内存仅保留一批。
     */
    private void synchronizePending(int batchSize) {
        long cursor = 0;
        while (true) {
            List<Map<String, Object>> batch = PortalMaps.camel(mapper.findPendingSyncBatch(cursor, batchSize));
            if (batch.isEmpty()) break;
            for (Map<String, Object> reminder : batch) {
                try { synchronize(reminder); }
                catch (Exception exception) { log.warn("Failed to synchronize reminder {}", number(reminder, "id"), exception); }
                cursor = number(reminder, "id");
            }
        }
    }

    /**
     * 发送本地已提交的完整生命周期快照；企业展示信息来自原业务快照，不伪造当前企业资料。
     * 仅远端确认成功才按发送版本清除待同步标记，迟到响应不能确认并发产生的新状态。
     */
    private void synchronize(Map<String, Object> reminder) {
        Map<String, Object> source;
        try {
            source = objectMapper.readValue(string(reminder, "businessDataJson"),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("提醒业务快照解析失败", exception);
        }
        Map<String, Object> request = new LinkedHashMap<>(reminder);
        request.put("enterpriseName", string(source, "enterpriseName"));
        request.put("title", "【" + severityName(string(reminder, "severity")) + "】" + string(source, "enterpriseName") + "：" + string(reminder, "title"));
        request.put("content", "企业“" + string(source, "enterpriseName") + "”（" + string(source, "enterpriseCode") + "）" + string(reminder, "content"));
        request.put("triggeredAt", reminder.get("lastTriggeredAt"));
        monitor.merge(request);
        mapper.acknowledgeSync(number(reminder, "id"), number(reminder, "lifecycleVersion"));
    }

    /**
     * 在套餐进入 7 天或 1 天到期阶段时，检查实际自动续费目标套餐是否仍然上架。目标套餐下架或
     * 被逻辑删除时生成重要提醒；恢复上架后本规则不再命中，周期复核将原提醒标记失效并同步。
     * 不删除历史记录，同一周期再次下架时可重新生效。
     */
    private void synchronizeUnavailableAutoRenewPlanReminder(Map<String, Object> source, LocalDateTime now, List<Map<String, Object>> reminders) {
        if (integer(source, "subscriptionStatus") != 1 || !bool(source, "autoRenewEnabled")) return;
        LocalDateTime target = date(source, "nextRenewAt");
        if (target == null) target = date(source, "endAt");
        Stage stage = expiryStage(remainingDays(now, target));
        if (stage == null) return;
        String key = "AUTO_RENEW_PLAN_UNAVAILABLE:subscription-" + number(source, "subscriptionId") + ":renew-" + target;
        if (integer(source, "renewalPlanStatus") == 1) return;
        String plan = string(source, "renewalPlanName");
        if (plan.isBlank()) plan = string(source, "planName");
        String content = String.format(Locale.ROOT,
                "您的“%s”套餐已下架，当前自动续费将在 %s 无法执行。请在套餐到期前选择其他可用套餐。",
                plan, display(target));
        emit(reminders, source, ReminderType.AUTO_RENEW_PLAN_UNAVAILABLE, key, stage, "WARNING",
                "自动续费套餐已下架", content, target, now);
    }

    /** 获取当前企业近期提醒，Map 转换统一保持门户接口的驼峰字段风格。 */
    @Override
    public List<Map<String, Object>> recent() {
        return PortalMaps.camel(mapper.findRecent(context.enterpriseId()));
    }

    /** 套餐到期两阶段提醒：自动续费仅在可用余额不足时提醒，未自动续费则无条件提醒。 */
    private void generateExpiryReminders(Map<String, Object> source, LocalDateTime now, List<Map<String, Object>> reminders) {
        if (integer(source, "subscriptionStatus") != 1) return;
        LocalDateTime target = date(source, bool(source, "autoRenewEnabled") ? "nextRenewAt" : "endAt");
        if (target == null) target = date(source, "endAt");
        int remainingDays = remainingDays(now, target);
        Stage stage = expiryStage(remainingDays);
        if (stage == null) return;

        String plan = string(source, "planName");
        if (bool(source, "autoRenewEnabled")) {
            BigDecimal available = money(source, "balanceAmount").subtract(money(source, "frozenAmount"));
            BigDecimal amount = money(source, "renewalAmount");
            if (available.compareTo(amount) >= 0) return;
            BigDecimal shortfall = amount.subtract(available);
            String content = String.format(Locale.ROOT,
                    "您的“%s”套餐将于 %s 自动续费，预计续费金额为 ¥%s。当前可用余额为 ¥%s，尚缺 ¥%s，请于续费前完成充值。",
                    plan, display(target), amount(amount), amount(available), amount(shortfall));
            emit(reminders, source, ReminderType.AUTO_RENEW_BALANCE_INSUFFICIENT,
                    "AUTO_RENEW_BALANCE_INSUFFICIENT:subscription-" + number(source, "subscriptionId") + ":renew-" + target,
                    stage, "WARNING", "自动续费余额不足，请及时充值", content, target, now);
        } else {
            String content = String.format(Locale.ROOT,
                    "您的“%s”套餐将于 %s 到期，剩余 %d 天。当前未开启自动续费，请及时续费或开启自动续费。",
                    plan, display(target), remainingDays);
            emit(reminders, source, ReminderType.SUBSCRIPTION_EXPIRING_NO_AUTO_RENEW,
                    "SUBSCRIPTION_EXPIRING_NO_AUTO_RENEW:subscription-" + number(source, "subscriptionId") + ":end-" + target,
                    stage, "WARNING", "套餐将在" + remainingDays + "天后到期", content, target, now);
        }
    }

    /** 工单额度先按 80%/90%合并预警；达到额度后改用 1、2、4、8 倍指数阶段的独立超额提醒。 */
    private void generateQuotaReminders(Map<String, Object> source, LocalDateTime now, List<Map<String, Object>> reminders) {
        if (integer(source, "subscriptionStatus") != 1) return;
        long limit = number(source, "workorderLimit");
        long used = number(source, "usedCount");
        if (limit <= 0) return;
        LocalDateTime expiresAt = date(source, "endAt");
        if (used >= limit) {
            long multiplier = 1;
            while (multiplier <= Long.MAX_VALUE / 2 && used / limit >= multiplier * 2) multiplier *= 2;
            int exponent = Long.numberOfTrailingZeros(multiplier);
            Stage stage = new Stage("MULTIPLIER_" + multiplier, (exponent + 1) * 10);
            long overage = Math.max(0, used - limit);
            BigDecimal estimate = overageProperties.getUnitPrice().multiply(BigDecimal.valueOf(overage));
            String content = String.format(Locale.ROOT,
                    "本周期套餐额度为 %d 单，现已使用 %d 单，达到额度的 %d 倍，超出 %d 单，当前预计超额费用为 ¥%s。",
                    limit, used, multiplier, overage, amount(estimate));
            emit(reminders, source, ReminderType.WORKORDER_QUOTA_REACHED,
                    "WORKORDER_QUOTA_REACHED:subscription-" + number(source, "subscriptionId") + ":period-" + date(source, "startAt") + "-" + expiresAt,
                    stage, "WARNING", "工单数量已达到套餐额度的" + multiplier + "倍", content, expiresAt, now);
            return;
        }
        int percent = BigDecimal.valueOf(used).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(limit), 0, RoundingMode.DOWN).intValue();
        Stage stage = percent >= reminderProperties.getQuotaUrgentPercent()
                ? new Stage("90_PERCENT", 20)
                : percent >= reminderProperties.getQuotaFirstPercent() ? new Stage("80_PERCENT", 10) : null;
        if (stage == null) return;
        String content = String.format(Locale.ROOT,
                "本周期套餐额度为 %d 单，已使用 %d 单（%d%%），剩余 %d 单，请提前评估用量。",
                limit, used, percent, limit - used);
        emit(reminders, source, ReminderType.WORKORDER_QUOTA_NEAR_LIMIT,
                "WORKORDER_QUOTA_NEAR_LIMIT:subscription-" + number(source, "subscriptionId") + ":period-" + date(source, "startAt") + "-" + expiresAt,
                stage, "WARNING", "工单额度即将用尽", content, expiresAt, now);
    }

    /** 负余额、达到暂停阈值80%和已经欠费暂停是三个独立类别，共同复用一次欠费周期标识。 */
    private void generateBalanceReminders(Map<String, Object> source, LocalDateTime now, List<Map<String, Object>> reminders) {
        BigDecimal balance = money(source, "balanceAmount");
        boolean suspended = integer(source, "subscriptionStatus") == 3 && "ARREARS".equals(string(source, "suspendReason"));
        if (balance.signum() >= 0 && !suspended) return;
        long walletId = number(source, "walletId");
        long cycleId = number(source, "arrearsCycleId");
        String cycle = cycleId > 0 ? String.valueOf(cycleId) : "current-negative";
        LocalDateTime expiresAt = date(source, "endAt");
        String baseKey = ":wallet-" + walletId + ":cycle-" + cycle;
        /*
         * 负余额风险和欠费暂停是独立条件：余额补到0时前者解除，套餐尚未恢复时后者仍需提醒。
         */
        if (balance.signum() < 0) emit(reminders, source, ReminderType.WALLET_BALANCE_NEGATIVE, "WALLET_BALANCE_NEGATIVE" + baseKey,
                new Stage("NEGATIVE", 10), "WARNING", "账户余额已为负数，请尽快充值",
                "当前账户余额为 ¥" + amount(balance) + "，请尽快充值，以免欠费继续增加并影响套餐使用。", expiresAt, now);

        BigDecimal suspend = balanceProperties.getSuspendThreshold();
        BigDecimal near = suspend.multiply(reminderProperties.getNearSuspensionRatio()).setScale(2, RoundingMode.HALF_UP);
        if (balance.compareTo(near) <= 0 && balance.compareTo(suspend) >= 0) {
            emit(reminders, source, ReminderType.WALLET_BALANCE_NEAR_SUSPENSION, "WALLET_BALANCE_NEAR_SUSPENSION" + baseKey,
                    new Stage("NEAR_SUSPENSION", 10), "CRITICAL", "账户余额接近套餐停止阈值",
                    "当前余额为 ¥" + amount(balance) + "，已达到欠费预警阈值 ¥" + amount(near)
                            + "。余额低于 ¥" + amount(suspend) + " 时套餐服务将暂停，请尽快充值。", expiresAt, now);
        }
        if (suspended) {
            LocalDateTime suspendedAt = date(source, "suspendedAt");
            emit(reminders, source, ReminderType.SUBSCRIPTION_SUSPENDED_ARREARS,
                    "SUBSCRIPTION_SUSPENDED_ARREARS:subscription-" + number(source, "subscriptionId") + ":suspended-" + suspendedAt,
                    new Stage("SUSPENDED", 10), "CRITICAL", "套餐服务已因欠费暂停",
                    "套餐因欠费暂停尚未恢复，当前余额为 ¥" + amount(balance)
                            + "。请充值至不低于 ¥" + amount(balanceProperties.getRestoreThreshold()) + " 以恢复服务。",
                    expiresAt, now);
        }
    }

    /** 套餐结束后按资料保留期计算清理日，并在 30、15、3 天阶段更新同一条提醒。 */
    private void generateDataDeletionReminder(Map<String, Object> source, LocalDateTime now, List<Map<String, Object>> reminders) {
        LocalDateTime endAt = date(source, "endAt");
        if (endAt == null || !endAt.isBefore(now)) return;
        LocalDateTime deleteAt = endAt.plusDays(retentionProperties.getRetentionDays());
        int days = remainingDays(now, deleteAt);
        if (days < 0) return;
        Stage stage = deletionStage(days);
        if (stage == null) return;
        String content = "您的套餐已于 " + display(endAt) + " 结束，相关车险业务资料预计自 " + display(deleteAt)
                + " 起进入清理流程，剩余 " + days + " 天。请在期限前重新开通套餐并备份所需资料。";
        emit(reminders, source, ReminderType.ENTERPRISE_DATA_DELETION_APPROACHING,
                "ENTERPRISE_DATA_DELETION_APPROACHING:subscription-" + number(source, "subscriptionId") + ":delete-" + deleteAt,
                stage, "CRITICAL", "企业资料即将进入清理流程", content, deleteAt, now);
    }

    /**
     * 组装门户文案与业务快照加入纯计算结果，调用方决定是生成持久化记录还是仅复核原稳定键。
     * 本方法不写数据库或请求监控，避免周期复核误触发新提醒。
     */
    private void emit(List<Map<String, Object>> reminders, Map<String, Object> source, ReminderType type, String key, Stage stage, String severity,
            String title, String content, LocalDateTime expiresAt, LocalDateTime now) {
        Map<String, Object> reminder = new HashMap<>();
        reminder.put("enterpriseId", number(source, "enterpriseId"));
        reminder.put("reminderType", type.name());
        reminder.put("reminderKey", key);
        reminder.put("reminderStage", stage.code);
        reminder.put("stageLevel", stage.level);
        reminder.put("severity", severity);
        reminder.put("title", title);
        reminder.put("content", content);
        reminder.put("businessDataJson", json(source));
        reminder.put("triggeredAt", now);
        reminder.put("expiresAt", expiresAt);
        reminders.add(reminder);
    }

    private Stage expiryStage(int days) {
        if (days < 0) return null;
        if (days <= reminderProperties.getUrgentExpiryWarningDays()) return new Stage("1D", 20);
        if (days <= reminderProperties.getFirstExpiryWarningDays()) return new Stage("7D", 10);
        return null;
    }

    private Stage deletionStage(int days) {
        int[] thresholds = reminderProperties.getDataDeletionWarningDays();
        Stage selected = null;
        for (int i = 0; i < thresholds.length; i++) {
            if (days <= thresholds[i]) selected = new Stage(thresholds[i] + "D", (i + 1) * 10);
        }
        return selected;
    }

    private int remainingDays(LocalDateTime from, LocalDateTime target) {
        if (target == null) return -1;
        long seconds = Duration.between(from, target).getSeconds();
        if (seconds < 0) return -1;
        return (int) Math.ceil(seconds / 86400.0);
    }

    private String json(Map<String, Object> source) {
        try { return objectMapper.writeValueAsString(source); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("提醒业务快照序列化失败", exception); }
    }

    private String severityName(String value) { return "CRITICAL".equals(value) ? "紧急" : "WARNING".equals(value) ? "重要" : "提醒"; }
    private String display(LocalDateTime value) { return value == null ? "-" : DISPLAY_TIME.format(value); }
    private String amount(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP).toPlainString(); }
    private String string(Map<String, Object> map, String key) { Object value = map.get(key); return value == null ? "" : String.valueOf(value); }
    private long number(Map<String, Object> map, String key) { Object value = map.get(key); return value instanceof Number ? ((Number) value).longValue() : 0L; }
    private int integer(Map<String, Object> map, String key) { return (int) number(map, key); }
    private boolean bool(Map<String, Object> map, String key) { Object value = map.get(key); return value instanceof Boolean ? (Boolean) value : number(map, key) == 1; }
    private BigDecimal money(Map<String, Object> map, String key) { Object value = map.get(key); return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value)); }
    private LocalDateTime date(Map<String, Object> map, String key) { Object value = map.get(key); return value instanceof LocalDateTime ? (LocalDateTime) value : null; }

    /** 不可变阶段值对象，确保模板阶段编码和可比较等级始终成对传递。 */
    private static final class Stage {
        private final String code;
        private final int level;
        private Stage(String code, int level) { this.code = code; this.level = level; }
    }
}
