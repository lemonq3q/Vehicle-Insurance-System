package com.example.insurancesystem.service;

import com.example.insurancesystem.domain.SystemFile;
import com.example.insurancesystem.domain.encapsulate.ResponseResult;

public interface OCRService {
    ResponseResult idCardRecognition(SystemFile systemFile);

    ResponseResult vehicleCertificateRecognition(SystemFile systemFile);

    ResponseResult vehicleInvoiceRecognition(SystemFile systemFile);

    ResponseResult vehicleLicenseRecognition(SystemFile systemFile);

    ResponseResult businessLicenseRecognition(SystemFile systemFile);
}
