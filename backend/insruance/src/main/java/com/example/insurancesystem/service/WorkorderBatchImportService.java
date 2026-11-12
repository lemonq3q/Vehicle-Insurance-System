package com.example.insurancesystem.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.insurancesystem.domain.Insurance;
import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.merchant.Merchant;
import com.example.insurancesystem.domain.merchant.importbatch.BatchImportLimits;
import com.example.insurancesystem.domain.merchant.importbatch.BatchImportFailure;
import com.example.insurancesystem.domain.merchant.importbatch.BatchImportResult;
import com.example.insurancesystem.domain.workorder.*;
import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.mapper.InsuranceMapper;
import com.example.insurancesystem.mapper.MerchantMapper;
import com.example.insurancesystem.mapper.UserMapper;
import com.example.insurancesystem.security.EnterpriseContextHolder;
import com.example.insurancesystem.utils.RenewalCalendar;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;

/**
 * 导入无附件的历史工单，按新版101列或旧101/93列契约解析，并在当前企业内匹配上下游和处理人。
 * 每行先校验后交给独立事务保存，失败行保留原始文字与原因；本服务不启用整文件事务或跨企业匹配。
 * 随请求创建局部解析器，避免 DataFormatter 在并发上传之间共享状态。
 */
@Service
public class WorkorderBatchImportService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WorkorderBatchImportService.class);
    private final WorkorderImportSchema schema;
    private final ObjectMapper json;
    private final MerchantMapper merchants;
    private final UserMapper users;
    private final InsuranceMapper insurances;
    private final WorkorderImportRowWriter writer;

    /**
     * 复用项目 JSON、Mapper 及工单事务服务，避免独立建立数据访问或文件上传机制。
     */
    public WorkorderBatchImportService(WorkorderImportSchema schema, ObjectMapper json, MerchantMapper merchants,
            UserMapper users, InsuranceMapper insurances, WorkorderImportRowWriter writer) {
        this.schema = schema; this.json = json; this.merchants = merchants;
        this.users = users; this.insurances = insurances; this.writer = writer;
    }

    /**
     * 校验固定 Sheet、第四行列头及大小，逐行匹配并保存有效工单。
     * @param file 单个 xlsx，最多20MB、10000条非空数据；公式单元格不作为导入值。
     * @return 与上下游导入一致的数量和逐行失败结构；模板错误不会产生任何写入。
     */
    public ResponseResult importFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > BatchImportLimits.MAX_FILE_BYTES
                || file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            return new ResponseResult(400, "请选择不超过20MB的官方xlsx模板");
        }
        Long enterpriseId = EnterpriseContextHolder.requireEnterpriseId();
        List<Insurance> products = insurances.selectList(new LambdaQueryWrapper<Insurance>()
                .eq(Insurance::getEnterpriseId, enterpriseId).eq(Insurance::getIsDelete, 0));
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheet("工单");
            List<WorkorderImportSchema.Column> columns = validateHeaders(sheet);
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            List<Row> rows = new ArrayList<>();
            /* 先检查整文件规模，再保存任何行，避免超限文件只导入前半部分。 */
            for (Row row : sheet) {
                if (row.getRowNum() >= 4 && !emptyRow(row, formatter)) rows.add(row);
                if (rows.size() > BatchImportLimits.MAX_DATA_ROWS) return new ResponseResult(400, "单次最多导入10000条工单");
            }
            BatchImportResult result = new BatchImportResult();
            result.setTotalRows(rows.size());
            for (Row row : rows) {
                Map<String, String> raw = rawRow(row, formatter, columns);
                try {
                    WorkorderDTO workorder = parseRow(row, formatter, enterpriseId, products, columns);
                    writer.write(workorder);
                    result.setSuccessRows(result.getSuccessRows() + 1);
                } catch (BusinessException | IllegalArgumentException exception) {
                    String reason = exception instanceof BusinessException ? ((BusinessException) exception).getMsg() : exception.getMessage();
                    result.addFailure(new BatchImportFailure("工单", row.getRowNum() + 1, raw, reason));
                } catch (Exception exception) {
                    log.error("工单导入行保存失败，enterpriseId={}，row={}", enterpriseId, row.getRowNum() + 1, exception);
                    result.addFailure(new BatchImportFailure("工单", row.getRowNum() + 1, raw, "本行保存失败，已回滚，请检查关联数据或联系管理员"));
                }
            }
            return new ResponseResult(200, "导入完成", result);
        } catch (BusinessException exception) {
            return new ResponseResult(exception.getCode(), exception.getMsg());
        } catch (Exception exception) {
            return new ResponseResult(400, "Excel文件无法读取，请重新使用官方模板");
        }
    }

    /**
     * 精确核对所有列和顺序，拒绝旧模板或额外附件列，防止长表字段错位。
     * @param sheet 固定业务数据 Sheet。
     */
    private List<WorkorderImportSchema.Column> validateHeaders(Sheet sheet) {
        if (sheet == null || sheet.getRow(3) == null) throw new BusinessException(400, "缺少工单Sheet或第四行表头");
        Row header = sheet.getRow(3);
        DataFormatter formatter = new DataFormatter(Locale.CHINA);
        int size = header.getLastCellNum();
        if (size == schema.getColumns().size() && matchesHeaders(header, formatter, schema.getColumns(), size)) return schema.getColumns();
        if ((size == schema.getLegacyColumns().size() || size == 93)
                && matchesHeaders(header, formatter, schema.getLegacyColumns(), size)) return schema.getLegacyColumns();
        throw new BusinessException(400, "模板表头或列顺序不符，请下载新版模板");
    }

    /**
     * 逐列确认模板版本，不凭列数猜测字段含义，尤其防止新旧101列顺序互相误读。
     */
    private boolean matchesHeaders(Row header, DataFormatter formatter, List<WorkorderImportSchema.Column> columns, int size) {
        for (int i = 0; i < size; i++) {
            if (!columns.get(i).label().equals(formatter.formatCellValue(header.getCell(i)).trim())) return false;
        }
        return true;
    }

    /**
     * 判断是否为空业务行，避免把模板预设空白输入区作为错误记录。
     * @param row 数据行。
     * @param formatter 当前请求的单元格格式器。
     * @return 契约列及额外单元格均为空时为true。
     */
    private boolean emptyRow(Row row, DataFormatter formatter) {
        for (Cell cell : row) if (!formatter.formatCellValue(cell).trim().isEmpty()) return false;
        return true;
    }

    /**
     * 保留失败行的原始显示内容以便用户修正，不将识别条件或证件内容写入日志。
     * @return 以官方列名为键的行文字。
     */
    private Map<String, String> rawRow(Row row, DataFormatter formatter, List<WorkorderImportSchema.Column> columns) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            result.put(columns.get(i).label().replace("* ", ""), formatter.formatCellValue(row.getCell(i)).trim());
        }
        return result;
    }

    /**
     * 将单行白名单字段转换为聚合 DTO，机构仅按同企业同方向名称精确匹配。
     * 处理人至少提供一项身份条件，非空条件全部参与 AND，稳定选最小用户ID，不使用当前账号冒充处理人。
     */
    private WorkorderDTO parseRow(Row row, DataFormatter formatter, Long enterpriseId, List<Insurance> products,
                                  List<WorkorderImportSchema.Column> columns) {
        Map<String, Object> fields = new LinkedHashMap<>();
        Map<String, String> identity = new HashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            WorkorderImportSchema.Column column = columns.get(i);
            Object value = parseCell(row.getCell(i), column, formatter);
            if (column.path().startsWith("handler")) {
                if (value != null) identity.put(column.path(), value.toString());
            } else if (value != null) {
                String[] path = column.path().split("\\.");
                if (path.length == 1) fields.put(path[0], value);
                else {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> nested = (Map<String, Object>) fields.computeIfAbsent(path[0], key -> new LinkedHashMap<>());
                    nested.put(path[1], value);
                }
            }
        }
        WorkorderDTO dto = json.convertValue(fields, WorkorderDTO.class);
        dto.setCreateMerchantId(matchMerchant(dto.getCreateMerchantName(), "DOWNSTREAM", enterpriseId));
        dto.setInsuranceMerchantId(matchMerchant(dto.getInsuranceMerchantName(), "UPSTREAM", enterpriseId));
        if (identity.isEmpty()) throw new BusinessException(400, "处理人姓名、登录邮箱、证件号至少填写一项");
        Long handler = users.selectImportHandler(enterpriseId, identity.get("handlerName"), identity.get("handlerEmail"), identity.get("handlerIdNum"));
        if (handler == null) throw new BusinessException(400, "本企业没有同时满足所填姓名、登录邮箱及证件号的有效处理人");
        dto.setHandleBy(handler);
        validateBusiness(dto);
        if (dto.getVehicleLicense() == null) dto.setVehicleLicense(new VehicleLicense());
        if (dto.getVehicleCertificate() == null) dto.setVehicleCertificate(new VehicleCertificate());
        if (dto.getVehicleInvoice() == null) dto.setVehicleInvoice(new VehicleInvoice());
        dto.setWorkorderInsuranceList(buildInsuranceSnapshot(products, dto));
        dto.setWorkorderFileList(Collections.emptyList());
        return dto;
    }

    /**
     * 按声明类型转换单元格，姓名及号码保持文本，金额限制数据库精度，日期接受Excel日期或严格日历文本。
     * @throws BusinessException 必填缺失、公式、超长、负数或非法日期时整行拒绝，不尝试纠正猜测。
     */
    private Object parseCell(Cell cell, WorkorderImportSchema.Column column, DataFormatter formatter) {
        String text = formatter.formatCellValue(cell).trim();
        if (cell != null && cell.getCellType() == CellType.FORMULA) throw new BusinessException(400, column.label() + "请填写实际值，不支持公式");
        if (text.isEmpty()) {
            if (column.required()) throw new BusinessException(400, column.label() + "不能为空");
            return null;
        }
        try {
            switch (column.type()) {
                case "enum":
                    Integer code = column.values().get(text);
                    if (code == null) throw new IllegalArgumentException();
                    return code;
                case "integer":
                    int integer = new BigDecimal(text).intValueExact();
                    if (integer < 0) throw new IllegalArgumentException();
                    return integer;
                case "decimal":
                    BigDecimal number = cell != null && cell.getCellType() == CellType.NUMERIC
                            ? BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros() : new BigDecimal(text.replace(",", "")).stripTrailingZeros();
                    int precision = column.path().endsWith("Percentage") ? 8 : 12;
                    if (number.signum() < 0 || number.scale() > 2 || number.precision() - number.scale() > precision - 2) throw new IllegalArgumentException();
                    return number;
                case "date":
                    LocalDate date = cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)
                            ? cell.getLocalDateTimeCellValue().toLocalDate()
                            : LocalDate.parse(text.replace('/', '-'), DateTimeFormatter.ofPattern("uuuu-M-d").withResolverStyle(ResolverStyle.STRICT));
                    if (date.getYear() < 1900 || date.getYear() > 2200) throw new IllegalArgumentException();
                    return date.atStartOfDay(RenewalCalendar.BUSINESS_ZONE).toEpochSecond();
                default:
                    if (cell != null && cell.getCellType() == CellType.NUMERIC && Math.abs(cell.getNumericCellValue()) >= 1e15) throw new IllegalArgumentException();
                    if (text.length() > column.maxLength()) throw new IllegalArgumentException();
                    return text;
            }
        } catch (Exception exception) {
            throw new BusinessException(400, column.label() + "格式不符，请按字段说明填写（数值非负，日期为yyyy-MM-dd）");
        }
    }

    /**
     * 在显式企业范围内匹配有效主体，重名历史数据拒绝导入，避免绑定到不确定机构。
     */
    private Long matchMerchant(String name, String direction, Long enterpriseId) {
        List<Merchant> matches = merchants.selectList(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getEnterpriseId, enterpriseId).eq(Merchant::getDirection, direction)
                .eq(Merchant::getName, name).eq(Merchant::getIsDelete, 0));
        if (matches.size() != 1) throw new BusinessException(400, ("UPSTREAM".equals(direction) ? "上游机构" : "提交店铺") + "名称未匹配或存在重名，请先维护上下游资料");
        return matches.get(0).getId();
    }

    /**
     * 校验跨字段业务关系，保费有值必须有对应起期，新增保单终期与起期需保持先后关系，车主按个人或组织维护有效名称。
     * 不导入附件或伪造OCR文件；三种起期、保单及支付承保信息写主表，金额和政策写 quote。
     */
    private void validateBusiness(WorkorderDTO dto) {
        if (dto.getOwnerType() == 0 && (dto.getOwnerName() == null || dto.getOwnerName().isBlank())) throw new BusinessException(400, "个人车主必须填写车主姓名");
        if (dto.getOwnerType() == 1 && (dto.getOrganizationName() == null || dto.getOrganizationName().isBlank())) throw new BusinessException(400, "组织车主必须填写组织名称");
        if (dto.getOwnerPhone() != null && !dto.getOwnerPhone().matches("^1[3-9]\\d{9}$")) throw new BusinessException(400, "车主电话格式不正确");
        if (dto.getCommercialInsuranceStartTime() == null && dto.getCompulsoryInsuranceStartTime() == null && dto.getNonMotorInsuranceStartTime() == null) throw new BusinessException(400, "至少填写商业、交强或非车险中的一种起期");
        if (positive(dto.getCommercialAmount()) && dto.getCommercialInsuranceStartTime() == null) throw new BusinessException(400, "有商业保费时必须填写商业起期");
        if ((positive(dto.getCompulsoryAmount()) || positive(dto.getVehicleAndTaxAmount())) && dto.getCompulsoryInsuranceStartTime() == null) throw new BusinessException(400, "有交强保费或车船税时必须填写交强起期");
        if (dto.getNonMotorAmount() != null && dto.getNonMotorInsuranceStartTime() == null)
            throw new BusinessException(400, "有非车险保费时必须填写非车险起期");
        org.springframework.beans.BeanWrapper model = new org.springframework.beans.BeanWrapperImpl(dto);
        /* 新增保单终期允许历史缺失；一旦填写则必须有对应起期且严格晚于起期。 */
        for (String prefix : List.of("commercial", "compulsory", "nonMotor")) {
            Long start = (Long) model.getPropertyValue(prefix + "InsuranceStartTime");
            Long end = (Long) model.getPropertyValue(prefix + "InsuranceEndTime");
            if (end != null && (start == null || end <= start)) throw new BusinessException(400, "保单终期必须晚于对应起期");
        }
        if (dto.getVehicleLicense() != null && dto.getVehicleLicense().getIsTransfer() != null
                && dto.getVehicleLicense().getIsTransfer() != 0 && dto.getVehicleLicense().getIsTransfer() != 1) throw new BusinessException(400, "是否过户只能填写0或1");
    }

    /**
     * 根据实际报价生成全部有效险种快照；有商业保费逐项保留企业默认值，不擅自启用默认不投保的附加险。
     * 独立交强险与车船税分别判断金额，合并产品任一金额为正即启用；增值服务沿用原默认项。
     */
    private List<WorkorderInsurance> buildInsuranceSnapshot(List<Insurance> products, WorkorderDTO dto) {
        if (products.isEmpty()) throw new BusinessException(400, "当前企业尚未配置险种");
        List<WorkorderInsurance> result = new ArrayList<>();
        for (Insurance product : products) {
            WorkorderInsurance snapshot = new WorkorderInsurance();
            snapshot.setInsuranceId(product.getId());
            String value = product.getDefaultOptionJson();
            if (Integer.valueOf(1).equals(product.getType())) {
                value = positive(dto.getCommercialAmount()) ? product.getDefaultOptionJson() : "不投保";
                if (value == null || value.isBlank()) throw new BusinessException(400, "商业险“" + product.getName() + "”未配置默认选项");
            } else if (Integer.valueOf(3).equals(product.getType())) {
                String name = product.getName() == null ? "" : product.getName().trim();
                boolean hasTax = name.contains("车船税");
                boolean hasCompulsory = name.contains("交强");
                boolean needed = (hasTax && positive(dto.getVehicleAndTaxAmount()))
                        || (hasCompulsory && positive(dto.getCompulsoryAmount()));
                value = needed ? product.getDefaultOptionJson() : inactiveInsuranceOption(product);
                if (value == null || value.isBlank()) throw new BusinessException(400, "险种“" + product.getName() + "”未配置默认选项");
            }
            snapshot.setOptionJson(value);
            snapshot.setDeductibleOptionJson(product.getDefaultDeductibleOptionJson());
            result.add(snapshot);
        }
        return result;
    }

    /**
     * 报价金额严格大于零才视为存在；空和零均不推定投保，不改变原始报价金额。
     */
    private boolean positive(BigDecimal value) { return value != null && value.signum() > 0; }

    /**
     * 对没有对应保费的强制险选择配置中的关闭选项，兼容合并产品的“不投保”和独立产品的“不需要”。
     * 不反向推测投保值，不改变险种配置；不存在合法关闭项时拒绝该行，避免保存选项外的字符串。
     * @param product 当前企业的有效险种配置。
     * @return 当前险种可用的关闭选项。
     */
    private String inactiveInsuranceOption(Insurance product) {
        try {
            for (com.fasterxml.jackson.databind.JsonNode option : json.readTree(product.getOptionsJson())) {
                String value = option.path("value").asText();
                if ("不投保".equals(value) || "不需要".equals(value)) return value;
            }
        } catch (Exception exception) {
            throw new BusinessException(400, "险种“" + product.getName() + "”选项配置无效");
        }
        throw new BusinessException(400, "险种“" + product.getName() + "”没有不投保或不需要选项");
    }
}
