package com.example.insurancesystem.monitor.service;

import com.example.insurancesystem.handler.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 维护监控平台销售推广目标并编排模拟推广。该服务负责目标 CRUD、Excel 模板与逐行导入、
 * 条件/显式/全量选择计数以及 6000 条上限截断。电话和邮件渠道当前只生成模拟发送结果，
 * 不连接外部供应商；推广页 Excel 只做一次性解析并返回浏览器，不在数据库或服务端内存中保存名单。
 */
@Service
public class MonitorPromotionService {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE = Pattern.compile("^[0-9+()\\-\\s]{6,32}$");
    private static final List<String> HEADERS = List.of("名称*", "电话", "邮箱", "备注");
    private static final String SELECT_FIELDS = "SELECT id,name,phone,email,extra_fields extraFields," +
            "source_type sourceType,import_batch_no importBatchNo,status,remark,created_at createdAt,updated_at updatedAt " +
            "FROM monitor_promotion_target ";
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final MonitorSystemLogService systemLog;
    private final int maxTargets;

    public MonitorPromotionService(JdbcTemplate jdbc, ObjectMapper objectMapper, MonitorSystemLogService systemLog,
            @Value("${monitor.promotion.max-targets:6000}") int maxTargets) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.systemLog = systemLog;
        this.maxTargets = maxTargets;
    }

    /**
     * 按名称、电话、邮箱关键字、来源和状态查询推广目标。查询只返回未删除记录，并限制单页最多
     * 100 条；联系方式当前返回原值，由监控平台鉴权和页面权限共同限制访问范围。
     */
    public Map<String, Object> page(String keyword, String sourceType, Integer status, String channel, int pageNo, int pageSize) {
        validatePage(pageNo, pageSize);
        Query query = query(keyword, sourceType, status);
        String channelCondition = channelCondition(channel);
        long total = jdbc.queryForObject("SELECT COUNT(1) FROM monitor_promotion_target" + query.where + channelCondition,
                Long.class, query.args.toArray());
        List<Object> args = new ArrayList<>(query.args);
        args.add((pageNo - 1) * pageSize); args.add(pageSize);
        List<Map<String, Object>> rows = total == 0 ? List.of() : jdbc.queryForList(
                SELECT_FIELDS + query.where + channelCondition + " ORDER BY id DESC LIMIT ?,?", args.toArray());
        rows.forEach(this::expandExtraFields);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", rows); result.put("pageNo", pageNo); result.put("pageSize", pageSize); result.put("total", total);
        return result;
    }

    /** 读取单条未删除推广目标，供编辑弹窗刷新数据库最终值。 */
    public Map<String, Object> detail(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(SELECT_FIELDS + "WHERE id=? AND deleted=0", id);
        if (rows.isEmpty()) throw new BusinessException(404, "推广目标不存在");
        expandExtraFields(rows.get(0));
        return rows.get(0);
    }

    /**
     * 创建手工推广目标。名称和至少一种联系方式在写入前完成校验，扩展字段只接收公司与职位，
     * 避免客户端把未声明结构直接写入 JSON；创建与系统审计日志共用事务。
     */
    @Transactional
    public Map<String, Object> create(Map<String, Object> body, Long operatorId, String operatorName) {
        TargetInput input = validateTarget(body);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO monitor_promotion_target(name,phone,email,extra_fields,source_type,status,remark," +
                            "created_by,updated_by,deleted,created_at,updated_at) VALUES(?,?,?,?, 'MANUAL',?,?,?, ?,0,NOW(),NOW())",
                    Statement.RETURN_GENERATED_KEYS);
            bindTarget(statement, input, operatorId);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) throw new BusinessException(500, "推广目标创建失败");
        Map<String, Object> after = detail(key.longValue());
        systemLog.recordOperation("PROMOTION_TARGET_CREATE", "新增推广目标", "promotion", operatorId, operatorName,
                null, null, "PROMOTION_TARGET", key, input.name, input.remark,
                "新增推广目标“" + input.name + "”", null, maskedSnapshot(after));
        return after;
    }

    /** 锁定推广目标后更新可编辑字段，确保审计前后快照属于同一事务版本。 */
    @Transactional
    public Map<String, Object> update(Long id, Map<String, Object> body, Long operatorId, String operatorName) {
        Map<String, Object> before = lockedDetail(id);
        TargetInput input = validateTarget(body);
        jdbc.update("UPDATE monitor_promotion_target SET name=?,phone=?,email=?,extra_fields=?,status=?,remark=?," +
                        "updated_by=?,updated_at=NOW() WHERE id=? AND deleted=0", input.name, input.phone, input.email,
                input.extraJson, input.status, input.remark, operatorId, id);
        Map<String, Object> after = detail(id);
        systemLog.recordOperation("PROMOTION_TARGET_UPDATE", "修改推广目标", "promotion", operatorId, operatorName,
                null, null, "PROMOTION_TARGET", id, input.name, input.remark,
                "修改推广目标“" + input.name + "”", maskedSnapshot(before), maskedSnapshot(after));
        return after;
    }

    /**
     * 软删除指定推广目标。该操作不再收集删除原因，也不写入系统审计日志；
     * SQL 受影响行数为 0 时表示目标不存在或已被删除，统一返回 404，防止客户端误判为删除成功。
     * @param id 待删除的推广目标主键。
     * @param operatorId 当前登录用户主键，仅用于更新目标记录的 updated_by。
     */
    @Transactional
    public void delete(Long id, Long operatorId) {
        int affected = jdbc.update(
                "UPDATE monitor_promotion_target SET deleted=1,updated_by=?,updated_at=NOW() WHERE id=? AND deleted=0",
                operatorId, id);
        if (affected == 0) throw new BusinessException(404, "推广目标不存在");
    }

    /**
     * 生成与批量导入解析器严格一致的 xlsx 模板。模板仅暴露前端当前可编辑的名称、电话、邮箱和备注，
     * 数据库中的公司与职位扩展字段仍保留。整列默认格式设为文本，保证新增行中的电话不会转成数值或科学计数法，
     * 邮箱也不会被 Excel 自动识别为超链接。第二行仅为可删除的示例数据。
     */
    public void writeTemplate(HttpServletResponse response) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=promotion-target-template.xlsx");
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("推广信息");
            CellStyle textStyle = workbook.createCellStyle();
            textStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));
            for (int i = 0; i < HEADERS.size(); i++) sheet.setDefaultColumnStyle(i, textStyle);
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.size(); i++) {
                Cell cell = header.createCell(i, CellType.STRING);
                cell.setCellStyle(textStyle);
                cell.setCellValue(HEADERS.get(i));
            }
            Row sample = sheet.createRow(1);
            List<String> values = List.of("张三", "13800138000", "zhangsan@example.com", "示例行可删除");
            for (int i = 0; i < values.size(); i++) {
                Cell cell = sample.createCell(i, CellType.STRING);
                cell.setCellStyle(textStyle);
                cell.setCellValue(values.get(i));
            }
            for (int i = 0; i < HEADERS.size(); i++) sheet.setColumnWidth(i, i == 2 ? 7200 : 5200);
            workbook.write(response.getOutputStream());
        } catch (Exception exception) {
            throw new BusinessException(500, "推广信息模板生成失败");
        }
    }

    /**
     * 解析官方模板并逐行校验。有效行在同一事务中批量写入，失败行不入库并携带 Excel 行号与原因；
     * 单次最多读取 10000 个数据行，防止异常工作簿占用过多内存。
     */
    @Transactional
    public Map<String, Object> importTargets(MultipartFile file, Long operatorId, String operatorName) {
        ParsedWorkbook parsed = parseWorkbook(file, true);
        String batchNo = "PT" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) +
                UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        for (TargetInput input : parsed.valid) {
            jdbc.update("INSERT INTO monitor_promotion_target(name,phone,email,extra_fields,source_type,import_batch_no," +
                            "status,remark,created_by,updated_by,deleted,created_at,updated_at) VALUES(?,?,?,?, 'EXCEL_IMPORT',?,?,?, ?,?,0,NOW(),NOW())",
                    input.name, input.phone, input.email, input.extraJson, batchNo, input.status, input.remark, operatorId, operatorId);
        }
        Map<String, Object> result = importResult(parsed, batchNo);
        systemLog.recordOperation("PROMOTION_TARGET_IMPORT", "导入推广目标", "promotion", operatorId, operatorName,
                null, null, "PROMOTION_IMPORT_BATCH", batchNo, batchNo, "Excel批量导入",
                "批量导入推广目标：成功" + parsed.valid.size() + "条，失败" + parsed.failures.size() + "条", null,
                Map.of("batchNo", batchNo, "successRows", parsed.valid.size(), "failureRows", parsed.failures.size()));
        return result;
    }

    /**
     * 解析推广页 Excel 并把全部有效行返回浏览器用于本地分页和勾选。
     * 该流程既不写目标主表，也不创建服务端缓存或临时令牌；失败行仅用于页面反馈。
     */
    public Map<String, Object> previewImport(MultipartFile file) {
        ParsedWorkbook parsed = parseWorkbook(file, false);
        Map<String, Object> result = importResult(parsed, null);
        result.put("list", parsed.rows);
        return result;
    }

    /** 根据渠道和选择模式计算可触达数量、实际发送数及是否被 6000 条上限截断。 */
    public Map<String, Object> preview(Map<String, Object> body) {
        Selection selection = selection(body, false);
        long deliverable = countForSelection(selection);
        return previewResult(deliverable);
    }

    /**
     * 调用当前模拟渠道实现完成群发。数据库候选按 id 升序直接 LIMIT，临时 Excel 按文件顺序截断，
     * 因此无条件全选超过上限时不会把全量资料装入内存；返回模拟批次号和成功数量供页面反馈。
     */
    public Map<String, Object> promote(Map<String, Object> body, Long operatorId, String operatorName) {
        Selection selection = selection(body, true);
        List<Map<String, Object>> recipients = new ArrayList<>();
        if (!selection.ids.isEmpty() || "FILTER".equals(selection.mode) || "ALL".equals(selection.mode)) {
            recipients.addAll(selectDatabase(selection));
        }
        if (selection.imported != null && recipients.size() < maxTargets) {
            recipients.addAll(selectImported(selection, maxTargets - recipients.size()));
        }
        String batchNo = "MOCK" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        systemLog.recordOperation("PROMOTION_SEND_MOCK", "模拟群发推广", "promotion", operatorId, operatorName,
                null, null, "PROMOTION_BATCH", batchNo, batchNo, "模拟渠道，未连接真实供应商",
                "通过" + selection.channel + "模拟推广" + recipients.size() + "个目标", null,
                Map.of("channel", selection.channel, "recipientCount", recipients.size(), "mock", true));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchNo", batchNo); result.put("channel", selection.channel); result.put("requestedCount", countForSelection(selection));
        result.put("sentCount", recipients.size()); result.put("successCount", recipients.size()); result.put("failureCount", 0);
        result.put("truncated", countForSelection(selection) > maxTargets); result.put("mock", true);
        return result;
    }

    /** 将经过校验的目标字段绑定到创建语句，保持写入顺序和 SQL 占位符一致。 */
    private void bindTarget(PreparedStatement statement, TargetInput input, Long operatorId) throws java.sql.SQLException {
        statement.setString(1, input.name); statement.setString(2, input.phone); statement.setString(3, input.email);
        statement.setString(4, input.extraJson); statement.setInt(5, input.status); statement.setString(6, input.remark);
        statement.setObject(7, operatorId); statement.setObject(8, operatorId);
    }

    /** 解析并验证单个目标，电话和邮箱至少存在一种且已有值必须满足基础格式。 */
    private TargetInput validateTarget(Map<String, Object> body) {
        if (body == null) throw new BusinessException(400, "推广目标参数不能为空");
        return targetInput(text(body.get("name")), text(body.get("phone")), text(body.get("email")),
                text(body.get("companyName")), text(body.get("position")), text(body.get("remark")),
                body.get("status") == null ? 1 : integer(body.get("status"), "状态", 0, 1));
    }

    /** 创建标准化强类型输入；该方法同时供页面 CRUD 和 Excel 行校验复用。 */
    private TargetInput targetInput(String name, String phone, String email, String company, String position,
            String remark, int status) {
        name = required(name, "名称", 100); phone = nullable(phone, 32); email = nullable(email, 254);
        company = nullable(company, 150); position = nullable(position, 100); remark = nullable(remark, 500);
        if (phone == null && email == null) throw new BusinessException(400, "电话和邮箱至少填写一项");
        if (phone != null && !PHONE.matcher(phone).matches()) throw new BusinessException(400, "电话格式不正确");
        if (email != null) { email = email.toLowerCase(Locale.ROOT); if (!EMAIL.matcher(email).matches()) throw new BusinessException(400, "邮箱格式不正确"); }
        Map<String, Object> extras = new LinkedHashMap<>();
        if (company != null) extras.put("companyName", company); if (position != null) extras.put("position", position);
        try { return new TargetInput(name, phone, email, extras.isEmpty() ? null : objectMapper.writeValueAsString(extras), status, remark); }
        catch (JsonProcessingException exception) { throw new BusinessException(500, "推广扩展信息序列化失败"); }
    }

    /** 校验工作簿文件名、固定 Sheet、表头和数据行，返回可写入目标及逐行失败信息。 */
    private ParsedWorkbook parseWorkbook(MultipartFile file, boolean allowEmpty) {
        if (file == null || file.isEmpty() || file.getOriginalFilename() == null ||
                !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx"))
            throw new BusinessException(400, "请选择官方 .xlsx 推广信息模板");
        List<TargetInput> valid = new ArrayList<>(); List<Map<String, Object>> failures = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter(); int totalRows = 0;
        try (InputStream stream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(stream)) {
            Sheet sheet = workbook.getSheet("推广信息");
            if (sheet == null) throw new BusinessException(400, "模板缺少“推广信息”工作表");
            Row header = sheet.getRow(0);
            for (int i = 0; i < HEADERS.size(); i++) if (header == null || !HEADERS.get(i).equals(cell(formatter, header, i)))
                throw new BusinessException(400, "模板表头已被修改，请重新下载官方模板");
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex); if (row == null || emptyRow(formatter, row)) continue;
                totalRows++; if (totalRows > 10000) throw new BusinessException(400, "单次导入不能超过10000行");
                Map<String, Object> previewRow = new LinkedHashMap<>();
                previewRow.put("rowNumber", rowIndex + 1); previewRow.put("name", cell(formatter,row,0));
                previewRow.put("phone", cell(formatter,row,1)); previewRow.put("email", cell(formatter,row,2));
                previewRow.put("remark", cell(formatter,row,3));
                try {
                    TargetInput input = targetInput(cell(formatter,row,0), cell(formatter,row,1), cell(formatter,row,2),
                            null, null, cell(formatter,row,3), 1);
                    valid.add(input); previewRow.put("valid", true);
                } catch (BusinessException exception) {
                    Map<String, Object> failure = Map.of("rowNumber", rowIndex + 1, "name", cell(formatter,row,0), "reason", exception.getMessage());
                    failures.add(failure); previewRow.put("valid", false); previewRow.put("reason", exception.getMessage());
                }
                rows.add(previewRow);
            }
        } catch (BusinessException exception) { throw exception; }
        catch (Exception exception) { throw new BusinessException(400, "工作簿无法读取，请重新下载模板填写"); }
        if (!allowEmpty && valid.isEmpty()) throw new BusinessException(400, "Excel中没有可推广的有效数据");
        return new ParsedWorkbook(totalRows, valid, failures, rows);
    }

    /** 将显式 ID、筛选全选、无条件全选或临时导入批次转换为统一选择对象。 */
    @SuppressWarnings("unchecked")
    private Selection selection(Map<String, Object> body, boolean requireContent) {
        if (body == null) throw new BusinessException(400, "推广参数不能为空");
        String channel = required(text(body.get("channel")), "推广渠道", 16).toUpperCase(Locale.ROOT);
        if (!List.of("PHONE", "EMAIL").contains(channel)) throw new BusinessException(400, "推广渠道不支持");
        String content = text(body.get("content"));
        if (requireContent) content = required(content, "推广内容", 2000);
        String mode = required(text(body.get("selectionMode")), "选择方式", 20).toUpperCase(Locale.ROOT);
        if (!List.of("IDS", "FILTER", "ALL", "IMPORT", "MIXED").contains(mode)) throw new BusinessException(400, "选择方式不支持");
        List<Long> ids = new ArrayList<>();
        if ("IDS".equals(mode) || "MIXED".equals(mode)) {
            Object values = body.get("targetIds");
            if (values instanceof List) for (Object value : (List<Object>) values) ids.add(Long.valueOf(String.valueOf(value)));
            if (ids.size() > maxTargets) throw new BusinessException(400, "显式选择不能超过" + maxTargets + "条");
        }
        Map<String, Object> filters = body.get("filters") instanceof Map ? (Map<String, Object>) body.get("filters") : Map.of();
        ImportedBatch imported = null;
        if ("IMPORT".equals(mode) || "MIXED".equals(mode)) {
            List<TargetInput> targets = new ArrayList<>();
            Object values = body.get("importedTargets");
            if (values instanceof List) for (Object value : (List<Object>) values) {
                if (!(value instanceof Map)) throw new BusinessException(400, "Excel推广目标格式无效");
                Map<String, Object> row = (Map<String, Object>) value;
                targets.add(targetInput(text(row.get("name")), text(row.get("phone")), text(row.get("email")),
                        null, null, text(row.get("remark")), 1));
            }
            imported = new ImportedBatch(targets);
            if (ids.size() + targets.size() > maxTargets) throw new BusinessException(400, "推广目标不能超过" + maxTargets + "条");
        }
        if (("IDS".equals(mode) || "IMPORT".equals(mode) || "MIXED".equals(mode)) && ids.isEmpty()
                && (imported == null || imported.targets.isEmpty())) {
            throw new BusinessException(400, "请至少选择一个推广目标");
        }
        /*
         * 无条件全选必须彻底忽略页面残留筛选值；否则用户切换到“无条件全选”后看到的计数仍会被
         * 旧关键词缩小。FILTER 和 IDS 保留查询条件，其中 IDS 最终仍以显式主键集合为准。
         */
        Query targetQuery = "ALL".equals(mode) ? query("", "", null) : query(text(filters.get("keyword")),
                text(filters.get("sourceType")), filters.get("status") == null || text(filters.get("status")).isBlank()
                        ? null : integer(filters.get("status"), "状态", 0, 1));
        return new Selection(channel, content, mode, ids, targetQuery, imported);
    }

    /** 根据选择对象统计数据库中的渠道有效目标，显式 ID 使用参数占位符避免拼接不可信值。 */
    private long countDatabase(Selection selection) {
        SqlSelection sql = databaseSelection(selection, true);
        return jdbc.queryForObject("SELECT COUNT(1) FROM monitor_promotion_target" + sql.where, Long.class, sql.args.toArray());
    }

    /** 选取最多配置上限条数据库目标，仅返回模拟渠道调用所需的最小字段。 */
    private List<Map<String, Object>> selectDatabase(Selection selection) {
        SqlSelection sql = databaseSelection(selection, true); List<Object> args = new ArrayList<>(sql.args); args.add(maxTargets);
        String contact = "PHONE".equals(selection.channel) ? "phone" : "email";
        return jdbc.queryForList("SELECT id,name," + contact + " contact FROM monitor_promotion_target" + sql.where +
                " ORDER BY id ASC LIMIT ?", args.toArray());
    }

    /** 合成 CRUD 筛选、启用状态、渠道非空和显式 ID 条件，供预览与发送共用完全相同的范围。 */
    private SqlSelection databaseSelection(Selection selection, boolean activeOnly) {
        StringBuilder where = new StringBuilder(selection.query.where); List<Object> args = new ArrayList<>(selection.query.args);
        if (activeOnly && selection.query.status == null) where.append(" AND status=1");
        where.append(" AND ").append("PHONE".equals(selection.channel) ? "phone" : "email").append(" IS NOT NULL");
        if ("IDS".equals(selection.mode) || "MIXED".equals(selection.mode)) {
            where.append(" AND id IN (");
            for (int i = 0; i < selection.ids.size(); i++) { if (i > 0) where.append(','); where.append('?'); args.add(selection.ids.get(i)); }
            where.append(')');
        }
        return new SqlSelection(where.toString(), args);
    }

    /** 统计临时 Excel 中满足所选渠道的数据行。 */
    private long countImported(Selection selection) { return selection.imported.targets.stream().filter(item -> contact(item, selection.channel) != null).count(); }
    /** 按文件原始顺序截取临时 Excel 中最多配置上限条有效收件人。 */
    private List<Map<String, Object>> selectImported(Selection selection, int limit) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TargetInput item : selection.imported.targets) {
            String contact = contact(item, selection.channel); if (contact == null) continue;
            rows.add(Map.of("name", item.name, "contact", contact)); if (rows.size() == limit) break;
        }
        return rows;
    }
    private String contact(TargetInput input, String channel) { return "PHONE".equals(channel) ? input.phone : input.email; }
    private long countForSelection(Selection selection) {
        long count = selection.ids.isEmpty() && !"FILTER".equals(selection.mode) && !"ALL".equals(selection.mode)
                ? 0 : countDatabase(selection);
        if (selection.imported != null) count += countImported(selection);
        return count;
    }

    /** 组装前端在点击推广前展示的上限提示元数据。 */
    private Map<String, Object> previewResult(long count) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matchedCount", count); result.put("deliverableCount", count); result.put("limit", maxTargets);
        result.put("sendCount", Math.min(count, maxTargets)); result.put("truncated", count > maxTargets); return result;
    }

    /** 生成列表查询条件；关键字同时覆盖名称、电话和邮箱，所有值使用 JDBC 参数绑定。 */
    private Query query(String keyword, String sourceType, Integer status) {
        StringBuilder where = new StringBuilder(" WHERE deleted=0"); List<Object> args = new ArrayList<>();
        keyword = text(keyword); if (!keyword.isBlank()) { if (keyword.length() > 100) throw new BusinessException(400, "关键词不能超过100个字符"); where.append(" AND (name LIKE ? OR phone LIKE ? OR email LIKE ?)"); String like = "%" + keyword + "%"; args.add(like); args.add(like); args.add(like); }
        sourceType = text(sourceType).toUpperCase(Locale.ROOT); if (!sourceType.isBlank()) { if (!List.of("MANUAL","EXCEL_IMPORT").contains(sourceType)) throw new BusinessException(400, "数据来源无效"); where.append(" AND source_type=?"); args.add(sourceType); }
        if (status != null) { if (status != 0 && status != 1) throw new BusinessException(400, "状态无效"); where.append(" AND status=?"); args.add(status); }
        return new Query(where.toString(), args, status);
    }

    /** 将数据库 JSON 扩展列展开为页面稳定字段，并移除原始 JSON，防止页面重复解析。 */
    @SuppressWarnings("unchecked")
    private void expandExtraFields(Map<String, Object> row) {
        Object raw = row.remove("extraFields"); if (raw == null) return;
        try { Map<String, Object> extras = objectMapper.readValue(String.valueOf(raw), Map.class); row.putAll(extras); }
        catch (Exception exception) { row.put("extraFieldsInvalid", true); }
    }

    /** 对审计快照中的联系方式脱敏，避免系统日志成为个人信息的旁路明文副本。 */
    private Map<String, Object> maskedSnapshot(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>(source);
        if (result.get("phone") != null) { String value = String.valueOf(result.get("phone")); result.put("phone", value.length() > 7 ? value.substring(0,3) + "****" + value.substring(value.length()-4) : "***"); }
        if (result.get("email") != null) { String value = String.valueOf(result.get("email")); int at = value.indexOf('@'); result.put("email", at > 1 ? value.substring(0,1) + "***" + value.substring(at) : "***"); }
        return result;
    }

    /** 在更新或删除前锁定目标，保证并发写入时审计快照准确。 */
    private Map<String, Object> lockedDetail(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(SELECT_FIELDS + "WHERE id=? AND deleted=0 FOR UPDATE", id);
        if (rows.isEmpty()) throw new BusinessException(404, "推广目标不存在"); expandExtraFields(rows.get(0)); return rows.get(0);
    }
    /** 列表接口保持单页最多 100 条，推广页批量选择通过连续分页累计，避免超大单次响应。 */
    private void validatePage(int pageNo, int pageSize) { if (pageNo < 1 || pageSize < 1 || pageSize > 100) throw new BusinessException(400, "分页参数不合法"); }
    /** 为推广搜索附加启用状态之外的渠道非空约束，信息录入页未传渠道时保持原查询语义。 */
    private String channelCondition(String channel) {
        channel = text(channel).toUpperCase(Locale.ROOT);
        if (channel.isBlank()) return "";
        if ("PHONE".equals(channel)) return " AND phone IS NOT NULL AND TRIM(phone)<>''";
        if ("EMAIL".equals(channel)) return " AND email IS NOT NULL AND TRIM(email)<>''";
        throw new BusinessException(400, "推广渠道不支持");
    }
    private int integer(Object value, String label, int min, int max) { try { int result = Integer.parseInt(String.valueOf(value)); if (result < min || result > max) throw new NumberFormatException(); return result; } catch (Exception exception) { throw new BusinessException(400, label + "参数无效"); } }
    private String required(String value, String label, int max) { value = text(value); if (value.isBlank()) throw new BusinessException(400, "请填写" + label); if (value.length() > max) throw new BusinessException(400, label + "不能超过" + max + "个字符"); return value; }
    private String nullable(String value, int max) { value = text(value); if (value.isBlank()) return null; if (value.length() > max) throw new BusinessException(400, "字段长度不能超过" + max + "个字符"); return value; }
    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private String cell(DataFormatter formatter, Row row, int index) { Cell cell = row.getCell(index); return cell == null ? "" : formatter.formatCellValue(cell).trim(); }
    private boolean emptyRow(DataFormatter formatter, Row row) { for (int i=0;i<HEADERS.size();i++) if (!cell(formatter,row,i).isBlank()) return false; return true; }
    private Map<String, Object> importResult(ParsedWorkbook parsed, String batchNo) { Map<String,Object> result=new LinkedHashMap<>(); result.put("batchNo",batchNo); result.put("totalRows",parsed.totalRows); result.put("successRows",parsed.valid.size()); result.put("failureRows",parsed.failures.size()); result.put("failures",parsed.failures); return result; }

    /** 保存已标准化的推广目标输入，防止持久化阶段再次读取不可信请求对象。 */
    private static final class TargetInput { final String name,phone,email,extraJson,remark; final int status; TargetInput(String name,String phone,String email,String extraJson,int status,String remark){this.name=name;this.phone=phone;this.email=email;this.extraJson=extraJson;this.status=status;this.remark=remark;} }
    /** 保存分页/推广复用的目标查询条件及其安全绑定参数。 */
    private static final class Query { final String where; final List<Object> args; final Integer status; Query(String where,List<Object> args,Integer status){this.where=where;this.args=args;this.status=status;} }
    /** 保存最终数据库选择 SQL 条件及参数，确保预览与发送范围一致。 */
    private static final class SqlSelection { final String where; final List<Object> args; SqlSelection(String where,List<Object> args){this.where=where;this.args=args;} }
    /** 保存 Excel 解析的有效行与逐行失败结果。 */
    private static final class ParsedWorkbook { final int totalRows; final List<TargetInput> valid; final List<Map<String,Object>> failures,rows; ParsedWorkbook(int totalRows,List<TargetInput> valid,List<Map<String,Object>> failures,List<Map<String,Object>> rows){this.totalRows=totalRows;this.valid=valid;this.failures=failures;this.rows=rows;} }
    /** 保存当前请求携带的 Excel 推广目标，仅在一次预览或发送调用的生命周期内存在。 */
    private static final class ImportedBatch { final List<TargetInput> targets; ImportedBatch(List<TargetInput> targets){this.targets=List.copyOf(targets);} }
    /** 保存渠道、选择模式、筛选条件和临时名单，统一驱动预览与模拟发送。 */
    private static final class Selection { final String channel,content,mode; final List<Long> ids; final Query query; final ImportedBatch imported; Selection(String channel,String content,String mode,List<Long> ids,Query query,ImportedBatch imported){this.channel=channel;this.content=content;this.mode=mode;this.ids=ids;this.query=query;this.imported=imported;} }
}
