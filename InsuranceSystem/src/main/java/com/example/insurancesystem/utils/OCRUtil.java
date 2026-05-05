package com.example.insurancesystem.utils;
import com.aliyun.ocr_api20210707.models.*;
import com.aliyun.tea.*;
import com.aliyun.ocr_api20210707.Client;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.example.insurancesystem.domain.BusinessLicense;
import com.example.insurancesystem.domain.IdCard;
import com.example.insurancesystem.domain.workorder.VehicleCertificate;
import com.example.insurancesystem.domain.workorder.VehicleInvoice;
import com.example.insurancesystem.domain.workorder.VehicleLicense;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OCRUtil {

    private static com.aliyun.ocr_api20210707.Client client;

    private static final String ENDPOINT = "ocr-api.cn-hangzhou.aliyuncs.com";

    @Autowired
    private JsonUtil jsonUtil;

    /**
     * <b>description</b> :
     * <p>使用凭据初始化账号Client</p>
     * @return Client
     *
     * @throws Exception
     */
    private static Client createClient() throws Exception {
        if (client != null) {
            return client;
        }
        com.aliyun.credentials.Client credential = SystemCommonUtil.getCredentialClient();

        Config config = new Config()
                .setCredential(credential);

        // Endpoint 请参考 https://api.aliyun.com/product/ocr-api
        config.endpoint = ENDPOINT;
        client = new Client(config);
        return client;
    }

    public IdCard recognizeIdcard(String url) {
        IdCard idCard = new IdCard();
        try {
            Client client = createClient();
            RecognizeIdcardRequest recognizeIdcardRequest = new RecognizeIdcardRequest();
            recognizeIdcardRequest.setUrl(url);
            RuntimeOptions runtime = new RuntimeOptions();

            RecognizeIdcardResponse resp = client.recognizeIdcardWithOptions(recognizeIdcardRequest, runtime);
            if (resp != null && resp.getBody() != null && resp.getBody().getData() != null){
                String resultJson = resp.getBody().getData();
                Map<String, Object> map = jsonUtil.parseJsonToMap(resultJson);

                Map<String, Object> dataMap = (Map<String, Object>) map.get("data");
                if (dataMap == null){
                    return idCard;
                }
                Map<String, Object> faceMap = (Map<String, Object>) dataMap.get("face");
                if (faceMap == null) {
                    return idCard;
                }
                Map<String, Object> faceData = (Map<String, Object>) faceMap.get("data");
                if (faceData == null) {
                    return idCard;
                }
                // 提取姓名
                String name = (String) faceData.get("name");
                if (name != null) {
                    idCard.setName(name);
                }
                // 提取身份证号
                String idNum = (String) faceData.get("idNumber");
                if (idNum != null) {
                    idCard.setIdNum(idNum);
                }
            }
        } catch (TeaException error) {
            // 错误 message
            System.out.println(error.getMessage());
            // 诊断地址
            System.out.println(error.getData().get("Recommend"));
        } catch (Exception _error) {
            TeaException error = new TeaException(_error.getMessage(), _error);
            // 此处仅做打印展示，请谨慎对待异常处理，在工程项目中切勿直接忽略异常。
            // 错误 message
            System.out.println(error.getMessage());
            // 诊断地址
            System.out.println(error.getData().get("Recommend"));
        }

        return idCard;
    }

    public BusinessLicense recognizeBusinessLicense(String url) {
        BusinessLicense businessLicense = new BusinessLicense();
        try {
            Client client = createClient();
            RecognizeBusinessLicenseRequest recognizeBusinessLicenseRequest = new RecognizeBusinessLicenseRequest();
            recognizeBusinessLicenseRequest.setUrl(url);
            RuntimeOptions runtime = new com.aliyun.teautil.models.RuntimeOptions();
            RecognizeBusinessLicenseResponse resp = client.recognizeBusinessLicenseWithOptions(recognizeBusinessLicenseRequest, runtime);

            if (resp != null && resp.getBody() != null && resp.getBody().getData() != null){
                String resultJson = resp.getBody().getData();
                Map<String, Object> map = jsonUtil.parseJsonToMap(resultJson);

                Map<String, Object> dataMap = (Map<String, Object>) map.get("data");
                if (dataMap == null){
                    return businessLicense;
                }
                // 组织名称
                businessLicense.setOrganizationName((String) dataMap.get("companyName"));
                // 社会信用代码
                businessLicense.setSocialCreditCode((String) dataMap.get("creditCode"));
            }
        } catch (TeaException error) {
            // 错误 message
            System.out.println(error.getMessage());
            // 诊断地址
            System.out.println(error.getData().get("Recommend"));
        } catch (Exception _error) {
            TeaException error = new TeaException(_error.getMessage(), _error);
            // 错误 message
            System.out.println(error.getMessage());
            // 诊断地址
            System.out.println(error.getData().get("Recommend"));
        }
        return businessLicense;
    }


    public VehicleLicense recognizeVehicleLicense(String url) {
        VehicleLicense vehicleLicense = new VehicleLicense();
        try {
            Client client = createClient();
            RecognizeVehicleLicenseRequest recognizeVehicleLicenseRequest = new RecognizeVehicleLicenseRequest();
            recognizeVehicleLicenseRequest.setUrl(url);
            RuntimeOptions runtime = new com.aliyun.teautil.models.RuntimeOptions();
            RecognizeVehicleLicenseResponse resp = client.recognizeVehicleLicenseWithOptions(recognizeVehicleLicenseRequest, runtime);

            if (resp != null && resp.getBody() != null && resp.getBody().getData() != null){
                String resultJson = resp.getBody().getData();
                Map<String, Object> map = jsonUtil.parseJsonToMap(resultJson);

                Map<String, Object> dataMap = (Map<String, Object>) map.get("data");
                if (dataMap == null){
                    return vehicleLicense;
                }
                Map<String, Object> faceMap = (Map<String, Object>) dataMap.get("face");
                if (faceMap != null) {
                    Map<String, Object> faceDataMap = (Map<String, Object>) faceMap.get("data");
                    if (faceDataMap != null) {
                        vehicleLicense.setEngineCode((String) faceDataMap.get("engineNumber"));
                        vehicleLicense.setIssueDate(TimeConvertUtil.autoParseToTimestamp((String) faceDataMap.get("issueDate")));
                        vehicleLicense.setBrandModel((String) faceDataMap.get("model"));
                        vehicleLicense.setOwnerName((String) faceDataMap.get("owner"));
                        vehicleLicense.setLicensePlate((String) faceDataMap.get("licensePlateNumber"));
                        vehicleLicense.setRegistrationDate(TimeConvertUtil.autoParseToTimestamp((String) faceDataMap.get("registrationDate")));
                        vehicleLicense.setUsageNature((String) faceDataMap.get("useNature"));
                        vehicleLicense.setVehicleType((String) faceDataMap.get("vehicleType"));
                        vehicleLicense.setVehicleCode((String) faceDataMap.get("vinCode"));
                    }
                }
                Map<String, Object> backMap = (Map<String, Object>) dataMap.get("back");
                if (backMap != null) {
                    Map<String, Object> backDataMap = (Map<String, Object>) backMap.get("data");
                    if (backDataMap != null) {
                        vehicleLicense.setLicensePlate((String) backDataMap.get("licensePlateNumber"));
                        vehicleLicense.setSeats(BaseTypeConvertUtil.safeParseInt(BaseTypeConvertUtil.extractFirstNumberString((String) backDataMap.get("passengerCapacity"))));
                        vehicleLicense.setCurbWeight(BaseTypeConvertUtil.safeParseInt(BaseTypeConvertUtil.extractFirstNumberString((String) backDataMap.get("curbWeight"))));
                        vehicleLicense.setApprovedLoadCapacity(BaseTypeConvertUtil.safeParseInt(BaseTypeConvertUtil.extractFirstNumberString((String) backDataMap.get("approvedLoadCapacity"))));
                    }
                }
            }
        } catch (TeaException error) {
            // 错误 message
            System.out.println(error.getMessage());
            // 诊断地址
            System.out.println(error.getData().get("Recommend"));
        } catch (Exception _error) {
            TeaException error = new TeaException(_error.getMessage(), _error);
            // 错误 message
            System.out.println(error.getMessage());
            // 诊断地址
            System.out.println(error.getData().get("Recommend"));
        }
        return vehicleLicense;
    }

    public VehicleCertificate recognizeVehicleCertificate(String url) {
        VehicleCertificate vehicleCertificate = new VehicleCertificate();
        try {
            Client client = createClient();
            RecognizeVehicleCertificationRequest recognizeVehicleCertificateRequest = new RecognizeVehicleCertificationRequest();
            recognizeVehicleCertificateRequest.setUrl(url);
            RuntimeOptions runtime = new com.aliyun.teautil.models.RuntimeOptions();
            RecognizeVehicleCertificationResponse resp = client.recognizeVehicleCertificationWithOptions(recognizeVehicleCertificateRequest, runtime);

            if (resp != null && resp.getBody() != null && resp.getBody().getData() != null){
                String resultJson = resp.getBody().getData();
                Map<String, Object> map = jsonUtil.parseJsonToMap(resultJson);

                Map<String, Object> dataMap = (Map<String, Object>) map.get("data");
                if (dataMap == null){
                    return vehicleCertificate;
                }
                vehicleCertificate.setVehicleType((String) dataMap.get("vehicleName"));
                vehicleCertificate.setDisplacement(BaseTypeConvertUtil.safeParseInt(BaseTypeConvertUtil.extractFirstNumberString((String) dataMap.get("displacement"))));
                vehicleCertificate.setEngineCode((String) dataMap.get("engineNumber"));
                vehicleCertificate.setApprovedLoadCapacity(BaseTypeConvertUtil.safeParseInt(BaseTypeConvertUtil.extractFirstNumberString((String) dataMap.get("maximumLadenMass"))));
                vehicleCertificate.setSeats(BaseTypeConvertUtil.safeParseInt(BaseTypeConvertUtil.extractFirstNumberString((String) dataMap.get("passengerCapacity"))));
                vehicleCertificate.setBrandModel((String) dataMap.get("vehicleBrand") + dataMap.get("vehicleModel"));
                vehicleCertificate.setVehicleCode((String) dataMap.get("vinCode"));
                vehicleCertificate.setCurbWeight(BaseTypeConvertUtil.safeParseInt(BaseTypeConvertUtil.extractFirstNumberString((String) dataMap.get("equipmentWeight"))));
            }
        } catch (TeaException error) {
            // 错误 message
            System.out.println(error.getMessage());
            // 诊断地址
            System.out.println(error.getData().get("Recommend"));
        } catch (Exception _error) {
            TeaException error = new TeaException(_error.getMessage(), _error);
            // 错误 message
            System.out.println(error.getMessage());
            // 诊断地址
            System.out.println(error.getData().get("Recommend"));
        }
        return vehicleCertificate;
    }

    public VehicleInvoice recognizeVehicleInvoice(String url) {
        VehicleInvoice vehicleInvoice = new VehicleInvoice();
        try {
            Client client = createClient();
            RecognizeCarInvoiceRequest recognizeCarInvoiceRequest = new RecognizeCarInvoiceRequest();
            recognizeCarInvoiceRequest.setUrl(url);
            RuntimeOptions runtime = new com.aliyun.teautil.models.RuntimeOptions();
            RecognizeCarInvoiceResponse resp = client.recognizeCarInvoiceWithOptions(recognizeCarInvoiceRequest, runtime);

            if (resp != null && resp.getBody() != null && resp.getBody().getData() != null){
                String resultJson = resp.getBody().getData();
                Map<String, Object> map = jsonUtil.parseJsonToMap(resultJson);

                Map<String, Object> dataMap = (Map<String, Object>) map.get("data");
                if (dataMap == null){
                    return vehicleInvoice;
                }
                vehicleInvoice.setBuyerName((String) dataMap.get("purchaserName"));
                vehicleInvoice.setBuyerIdNum((String) dataMap.get("purchaseCode"));
                vehicleInvoice.setVehicleType((String) dataMap.get("vehicleType"));
                vehicleInvoice.setBrandModel((String) dataMap.get("brandMode"));
                vehicleInvoice.setEngineCode((String) dataMap.get("engineNumber"));
                vehicleInvoice.setVehicleCode((String) dataMap.get("vinCode"));
                vehicleInvoice.setSeats(BaseTypeConvertUtil.safeParseInt(BaseTypeConvertUtil.extractFirstNumberString((String) dataMap.get("passengerLimitNumber"))));
                vehicleInvoice.setApprovedLoadCapacity(BaseTypeConvertUtil.safeParseInt(BaseTypeConvertUtil.extractFirstNumberString((String) dataMap.get("tonnage"))));
                vehicleInvoice.setInvoiceAmount(BaseTypeConvertUtil.safeParseAmount(dataMap.get("invoiceAmount")));
            }
        } catch (TeaException error) {
            // 错误 message
            System.out.println(error.getMessage());
            // 诊断地址
            System.out.println(error.getData().get("Recommend"));
        } catch (Exception _error) {
            TeaException error = new TeaException(_error.getMessage(), _error);
            // 错误 message
            System.out.println(error.getMessage());
            // 诊断地址
            System.out.println(error.getData().get("Recommend"));
        }
        return vehicleInvoice;
    }

}
