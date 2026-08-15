package com.example.insurancesystem.utils;

import com.aliyun.ocr_api20210707.Client;
import com.aliyun.ocr_api20210707.models.RecognizeAllTextRequest;
import com.aliyun.ocr_api20210707.models.RecognizeAllTextResponse;
import com.aliyun.tea.TeaException;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.example.insurancesystem.domain.BusinessLicense;
import com.example.insurancesystem.domain.IdCard;
import com.example.insurancesystem.domain.workorder.VehicleCertificate;
import com.example.insurancesystem.domain.workorder.VehicleInvoice;
import com.example.insurancesystem.domain.workorder.VehicleLicense;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

@Component
/**
 * 封装阿里云通用文字识别接口，并把不同车险证件的键值结果映射为项目领域对象。
 * 外部服务失败时记录诊断信息并返回空对象或 null，让业务层决定是否提示重试而不传播供应商异常结构。
 */
public class OCRUtil {

    private static final Logger log = LoggerFactory.getLogger(OCRUtil.class);

    private static final String ENDPOINT = "ocr-api.cn-hangzhou.aliyuncs.com";
    private static final String TYPE_ID_CARD = "IdCard";
    private static final String TYPE_BUSINESS_LICENSE = "BusinessLicense";
    private static final String TYPE_CAR_INVOICE = "CarInvoice";
    private static final String TYPE_VEHICLE_CERTIFICATION = "VehicleCertification";
    private static final String TYPE_VEHICLE_LICENSE = "VehicleLicense";
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            TYPE_ID_CARD,
            TYPE_BUSINESS_LICENSE,
            TYPE_CAR_INVOICE,
            TYPE_VEHICLE_CERTIFICATION,
            TYPE_VEHICLE_LICENSE
    );

    private static Client client;

    @Autowired
    private JsonUtil jsonUtil;

    /**
     * 延迟创建并复用阿里云 OCR Client，凭证由默认凭证链获取，服务端点固定为杭州区域以减少重复连接初始化。
     */
    private static Client createClient() throws Exception {
        if (client != null) {
            return client;
        }
        com.aliyun.credentials.Client credential = SystemCommonUtil.getCredentialClient();
        Config config = new Config().setCredential(credential);
        config.endpoint = ENDPOINT;
        client = new Client(config);
        return client;
    }

    /**
     * 识别身份证正反面聚合结果并提取姓名、证件号；无有效键值时返回字段为空的 IdCard。
     */
    public IdCard recognizeIdcard(String url) {
        return recognizeDocument(url, TYPE_ID_CARD, this::buildIdCard, IdCard::new);
    }

    /**
     * 识别营业执照并映射企业名称与统一社会信用代码。
     */
    public BusinessLicense recognizeBusinessLicense(String url) {
        return recognizeDocument(url, TYPE_BUSINESS_LICENSE, this::buildBusinessLicense, BusinessLicense::new);
    }

    /**
     * 识别机动车行驶证，将车辆标识、登记日期、载重和座位等字段转换为 VehicleLicense。
     */
    public VehicleLicense recognizeVehicleLicense(String url) {
        return recognizeDocument(url, TYPE_VEHICLE_LICENSE, this::buildVehicleLicense, VehicleLicense::new);
    }

    /**
     * 识别机动车整车出厂合格证，并组合品牌与型号等工单建档字段。
     */
    public VehicleCertificate recognizeVehicleCertificate(String url) {
        return recognizeDocument(url, TYPE_VEHICLE_CERTIFICATION, this::buildVehicleCertificate, VehicleCertificate::new);
    }

    /**
     * 识别机动车销售发票，提取购方、车辆、载重和含税金额等信息。
     */
    public VehicleInvoice recognizeVehicleInvoice(String url) {
        return recognizeDocument(url, TYPE_CAR_INVOICE, this::buildVehicleInvoice, VehicleInvoice::new);
    }

    /**
     * 返回供应商完整识别数据的 JSON 文本，供尚未建立强类型映射的调用场景使用。
     */
    public String recognizeAllText(String url, String type) {
        Map<String, Object> result = executeRecognizeAllText(url, type);
        return result == null ? null : jsonUtil.parseObjectToJson(result);
    }

    /**
     * 复用统一 OCR 调用和键值提取流程，再由证件专用 mapper 构造领域对象；无数据时通过 supplier 返回安全空对象。
     */
    private <T> T recognizeDocument(String url,
                                    String type,
                                    Function<Map<String, Object>, T> mapper,
                                    Supplier<T> emptySupplier) {
        Map<String, Object> kvData = recognizeKvData(url, type);
        if (kvData.isEmpty()) {
            return emptySupplier.get();
        }
        return mapper.apply(kvData);
    }

    /**
     * 执行识别并从多子图响应中汇总键值数据；接口失败统一转换为空 Map，简化各证件映射器分支。
     */
    private Map<String, Object> recognizeKvData(String url, String type) {
        Map<String, Object> result = executeRecognizeAllText(url, type);
        if (result == null) {
            return Collections.emptyMap();
        }
        return extractKvData(result);
    }

    /**
     * 校验 OCR 类型白名单后调用阿里云 RecognizeAllText，将 SDK 数据先序列化再转为通用 Map。
     * 空响应、空 JSON、供应商业务异常和网络异常均记录后返回 null，避免 SDK 类型泄漏到业务层。
     */
    private Map<String, Object> executeRecognizeAllText(String url, String type) {
        if (!SUPPORTED_TYPES.contains(type)) {
            log.warn("不支持的 OCR 类型: {}", type);
            return null;
        }

        try {
            Client ocrClient = createClient();
            RecognizeAllTextRequest request = new RecognizeAllTextRequest();
            request.setUrl(url);
            request.setType(type);

            RecognizeAllTextResponse response = ocrClient.recognizeAllTextWithOptions(request, new RuntimeOptions());
            if (response == null || response.getBody() == null || response.getBody().getData() == null) {
                return null;
            }

            String resultJson = jsonUtil.parseObjectToJson(response.getBody().getData());
            if (resultJson == null || resultJson.isBlank()) {
                return null;
            }

            Map<String, Object> resultMap = jsonUtil.parseJsonToMap(resultJson);
            return resultMap == null ? null : resultMap;
        } catch (TeaException error) {
            logOcrError(type, error);
            return null;
        } catch (Exception error) {
            log.error("调用阿里云 OCR 失败, type={}", type, error);
            return null;
        }
    }

    /**
     * 遍历供应商返回的 subImages，将每张图的 kvInfo.data 按顺序合并；后出现字段覆盖前值，
     * 使身份证等多面证件可以得到一份完整字段集合，同时忽略结构异常的子图。
     */
    private Map<String, Object> extractKvData(Map<String, Object> allTextResult) {
        Object subImagesObj = allTextResult.get("subImages");
        if (!(subImagesObj instanceof List<?> subImages) || subImages.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> mergedData = new LinkedHashMap<>();
        for (Object subImageObj : subImages) {
            if (!(subImageObj instanceof Map<?, ?> subImageRaw)) {
                continue;
            }

            Map<String, Object> subImage = castMap(subImageRaw);
            Map<String, Object> kvInfo = getMap(subImage, "kvInfo");
            Map<String, Object> data = getMap(kvInfo, "data");
            if (data != null) {
                mergedData.putAll(data);
            }
        }

        return mergedData;
    }

    /**
     * 将身份证 OCR 字段映射为系统证件对象，只保留当前业务使用的姓名和证件号码。
     */
    private IdCard buildIdCard(Map<String, Object> data) {
        IdCard idCard = new IdCard();
        idCard.setName(getString(data, "name"));
        idCard.setIdNum(getString(data, "idNumber"));
        return idCard;
    }

    /**
     * 将营业执照供应商字段转换为组织名称和社会信用代码。
     */
    private BusinessLicense buildBusinessLicense(Map<String, Object> data) {
        BusinessLicense businessLicense = new BusinessLicense();
        businessLicense.setOrganizationName(getString(data, "companyName"));
        businessLicense.setSocialCreditCode(getString(data, "creditCode"));
        return businessLicense;
    }

    /**
     * 将行驶证文字字段转换为车辆实体；日期转秒级时间戳，带单位数字字段先提取首个数值再安全转换。
     */
    private VehicleLicense buildVehicleLicense(Map<String, Object> data) {
        VehicleLicense vehicleLicense = new VehicleLicense();
        vehicleLicense.setEngineCode(getString(data, "engineNumber"));
        vehicleLicense.setIssueDate(getTimestamp(data, "issueDate"));
        vehicleLicense.setBrandModel(getString(data, "model"));
        vehicleLicense.setOwnerName(getString(data, "owner"));
        vehicleLicense.setLicensePlate(getString(data, "licensePlateNumber"));
        vehicleLicense.setRegistrationDate(getTimestamp(data, "registrationDate"));
        vehicleLicense.setUsageNature(getString(data, "useNature"));
        vehicleLicense.setVehicleType(getString(data, "vehicleType"));
        vehicleLicense.setVehicleCode(getString(data, "vinCode"));
        vehicleLicense.setSeats(getExtractedInteger(data, "passengerCapacity"));
        vehicleLicense.setCurbWeight(getExtractedInteger(data, "curbWeight"));
        vehicleLicense.setApprovedLoadCapacity(getExtractedInteger(data, "approvedLoadCapacity"));
        return vehicleLicense;
    }

    /**
     * 将合格证字段映射为车辆实体，并拼接品牌与车型形成系统使用的品牌型号。
     */
    private VehicleCertificate buildVehicleCertificate(Map<String, Object> data) {
        VehicleCertificate vehicleCertificate = new VehicleCertificate();
        vehicleCertificate.setVehicleType(getString(data, "vehicleName"));
        vehicleCertificate.setDisplacement(getExtractedInteger(data, "displacement"));
        vehicleCertificate.setEngineCode(getString(data, "engineNumber"));
        vehicleCertificate.setApprovedLoadCapacity(getExtractedInteger(data, "maximumLadenMass"));
        vehicleCertificate.setSeats(getExtractedInteger(data, "passengerCapacity"));
        vehicleCertificate.setBrandModel(joinStrings(getString(data, "vehicleBrand"), getString(data, "vehicleModel")));
        vehicleCertificate.setVehicleCode(getString(data, "vinCode"));
        vehicleCertificate.setCurbWeight(getExtractedInteger(data, "equipmentWeight"));
        return vehicleCertificate;
    }

    /**
     * 将车辆发票字段映射为工单发票对象，金额兼容数字金额和中文金额两个可能的供应商键。
     */
    private VehicleInvoice buildVehicleInvoice(Map<String, Object> data) {
        VehicleInvoice vehicleInvoice = new VehicleInvoice();
        vehicleInvoice.setBuyerName(getString(data, "purchaserName"));
        vehicleInvoice.setBuyerIdNum(getString(data, "purchaseCode"));
        vehicleInvoice.setVehicleType(getString(data, "vehicleType"));
        vehicleInvoice.setBrandModel(getString(data, "brandMode"));
        vehicleInvoice.setEngineCode(getString(data, "engineNumber"));
        vehicleInvoice.setVehicleCode(getString(data, "vinCode"));
        vehicleInvoice.setSeats(getExtractedInteger(data, "passengerLimitNumber"));
        vehicleInvoice.setApprovedLoadCapacity(getExtractedInteger(data, "tonnage"));
        vehicleInvoice.setInvoiceAmount(BaseTypeConvertUtil.safeParseAmount(firstNonBlank(data, "invoiceAmount", "invoiceAmountCn")));
        return vehicleInvoice;
    }

    /**
     * 记录阿里云 TeaException 的类型、错误消息和供应商 Recommend 建议，同时保留异常堆栈便于定位请求问题。
     */
    private void logOcrError(String type, TeaException error) {
        Object recommend = error.getData() == null ? null : error.getData().get("Recommend");
        log.error("调用阿里云 OCR 失败, type={}, message={}, recommend={}",
                type,
                error.getMessage(),
                recommend,
                error);
    }

    /**
     * 从通用响应 Map 安全读取嵌套 Map，值类型不符时返回 null 而不强制转换抛错。
     */
    private Map<String, Object> getMap(Map<String, Object> source, String key) {
        if (source == null) {
            return null;
        }
        Object value = source.get(key);
        if (!(value instanceof Map<?, ?> rawMap)) {
            return null;
        }
        return castMap(rawMap);
    }

    /**
     * 按候选键顺序返回第一个非空字符串，兼容供应商不同模板使用不同字段名的情况。
     */
    private String firstNonBlank(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            String value = getString(data, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 安全读取字段并去除首尾空白，缺失值和纯空白统一视为 null。
     */
    private String getString(Map<String, Object> data, String key) {
        if (data == null) {
            return null;
        }
        Object value = data.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 从可能带“kg”“人”等单位的 OCR 文本中提取首个数字并转换为整数。
     */
    private Integer getExtractedInteger(Map<String, Object> data, String key) {
        return BaseTypeConvertUtil.safeParseInt(BaseTypeConvertUtil.extractFirstNumberString(getString(data, key)));
    }

    /**
     * 将 OCR 日期字符串按系统支持格式解析为 Unix 秒级时间戳。
     */
    private Long getTimestamp(Map<String, Object> data, String key) {
        return TimeConvertUtil.autoParseToTimestamp(getString(data, key));
    }

    /**
     * 忽略空值后顺序拼接多个 OCR 文本，所有值为空时返回 null 而不是空字符串。
     */
    private String joinStrings(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value != null) {
                builder.append(value);
            }
        }
        String result = builder.toString().trim();
        return result.isEmpty() ? null : result;
    }

    @SuppressWarnings("unchecked")
    /**
     * 在已通过 instanceof Map 校验后集中执行泛型擦除转换，避免业务提取代码散落未检查强转。
     */
    private Map<String, Object> castMap(Map<?, ?> source) {
        return (Map<String, Object>) source;
    }
}
