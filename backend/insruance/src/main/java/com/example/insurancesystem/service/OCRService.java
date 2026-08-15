package com.example.insurancesystem.service;

import com.example.insurancesystem.domain.SystemFile;
import com.example.insurancesystem.domain.encapsulate.ResponseResult;

/**
 * 定义私有系统文件到各类车险证照领域对象的 OCR 识别能力。
 */
public interface OCRService {
    /**
     * 识别身份证信息。
     */
    ResponseResult idCardRecognition(SystemFile systemFile);

    /**
     * 识别机动车合格证信息。
     */
    ResponseResult vehicleCertificateRecognition(SystemFile systemFile);

    /**
     * 识别机动车销售发票信息。
     */
    ResponseResult vehicleInvoiceRecognition(SystemFile systemFile);

    /**
     * 识别机动车行驶证信息。
     */
    ResponseResult vehicleLicenseRecognition(SystemFile systemFile);

    /**
     * 识别营业执照信息。
     */
    ResponseResult businessLicenseRecognition(SystemFile systemFile);
}
