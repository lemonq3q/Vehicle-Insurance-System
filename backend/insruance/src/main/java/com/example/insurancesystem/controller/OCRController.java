package com.example.insurancesystem.controller;

import com.example.insurancesystem.domain.SystemFile;
import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.service.OCRService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ocr")
/**
 * 车险证照 OCR 接口，根据已上传的私有 SystemFile 生成临时访问地址并返回对应领域对象。
 */
public class OCRController {

    @Autowired
    private OCRService ocrService;

    @PostMapping("/idCard")
    /**
     * 识别身份证并返回姓名、证件号码等投保人资料。
     */
    public ResponseResult idCardRecognition(@RequestBody SystemFile systemFile){
        return ocrService.idCardRecognition(systemFile);
    }

    @PostMapping("/vehicleCertificate")
    /**
     * 识别车辆合格证并返回车架号、发动机号、车型和车辆规格。
     */
    public ResponseResult vehicleCertificateRecognition(@RequestBody SystemFile systemFile){
        return ocrService.vehicleCertificateRecognition(systemFile);
    }

    @PostMapping("/vehicleInvoice")
    /**
     * 识别机动车销售发票并返回购方、车辆及开票金额信息。
     */
    public ResponseResult vehicleInvoiceRecognition(@RequestBody SystemFile systemFile){
        return ocrService.vehicleInvoiceRecognition(systemFile);
    }

    @PostMapping("/vehicleLicense")
    /**
     * 识别行驶证并返回车主、车牌、登记日期及车辆参数。
     */
    public ResponseResult vehicleLicenseRecognition(@RequestBody SystemFile systemFile){
        return ocrService.vehicleLicenseRecognition(systemFile);
    }

    @PostMapping("/businessLicense")
    /**
     * 识别营业执照并返回组织名称和统一社会信用代码，用于企业车主或机构建档。
     */
    public ResponseResult businessLicenseRecognition(@RequestBody SystemFile systemFile){
        return ocrService.businessLicenseRecognition(systemFile);
    }
}
