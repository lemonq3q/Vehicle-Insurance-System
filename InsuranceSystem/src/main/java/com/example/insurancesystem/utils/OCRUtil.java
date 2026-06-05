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

    public IdCard recognizeIdcard(String url) {
        return recognizeDocument(url, TYPE_ID_CARD, this::buildIdCard, IdCard::new);
    }

    public BusinessLicense recognizeBusinessLicense(String url) {
        return recognizeDocument(url, TYPE_BUSINESS_LICENSE, this::buildBusinessLicense, BusinessLicense::new);
    }

    public VehicleLicense recognizeVehicleLicense(String url) {
        return recognizeDocument(url, TYPE_VEHICLE_LICENSE, this::buildVehicleLicense, VehicleLicense::new);
    }

    public VehicleCertificate recognizeVehicleCertificate(String url) {
        return recognizeDocument(url, TYPE_VEHICLE_CERTIFICATION, this::buildVehicleCertificate, VehicleCertificate::new);
    }

    public VehicleInvoice recognizeVehicleInvoice(String url) {
        return recognizeDocument(url, TYPE_CAR_INVOICE, this::buildVehicleInvoice, VehicleInvoice::new);
    }

    public String recognizeAllText(String url, String type) {
        Map<String, Object> result = executeRecognizeAllText(url, type);
        return result == null ? null : jsonUtil.parseObjectToJson(result);
    }

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

    private Map<String, Object> recognizeKvData(String url, String type) {
        Map<String, Object> result = executeRecognizeAllText(url, type);
        if (result == null) {
            return Collections.emptyMap();
        }
        return extractKvData(result);
    }

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

    private IdCard buildIdCard(Map<String, Object> data) {
        IdCard idCard = new IdCard();
        idCard.setName(getString(data, "name"));
        idCard.setIdNum(getString(data, "idNumber"));
        return idCard;
    }

    private BusinessLicense buildBusinessLicense(Map<String, Object> data) {
        BusinessLicense businessLicense = new BusinessLicense();
        businessLicense.setOrganizationName(getString(data, "companyName"));
        businessLicense.setSocialCreditCode(getString(data, "creditCode"));
        return businessLicense;
    }

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

    private void logOcrError(String type, TeaException error) {
        Object recommend = error.getData() == null ? null : error.getData().get("Recommend");
        log.error("调用阿里云 OCR 失败, type={}, message={}, recommend={}",
                type,
                error.getMessage(),
                recommend,
                error);
    }

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

    private String firstNonBlank(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            String value = getString(data, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

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

    private Integer getExtractedInteger(Map<String, Object> data, String key) {
        return BaseTypeConvertUtil.safeParseInt(BaseTypeConvertUtil.extractFirstNumberString(getString(data, key)));
    }

    private Long getTimestamp(Map<String, Object> data, String key) {
        return TimeConvertUtil.autoParseToTimestamp(getString(data, key));
    }

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
    private Map<String, Object> castMap(Map<?, ?> source) {
        return (Map<String, Object>) source;
    }
}
