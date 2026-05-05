package com.example.insurancesystem.service;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import org.springframework.web.multipart.MultipartFile;

public interface OCRService {
    ResponseResult idCardRecognition(MultipartFile file);

    ResponseResult vehicleCertificateRecognition(MultipartFile file);

    ResponseResult vehicleInvoiceRecognition(MultipartFile file);

    ResponseResult vehicleLicenseRecognition(MultipartFile file);

    ResponseResult businessLicenseRecognition(MultipartFile file);
}
