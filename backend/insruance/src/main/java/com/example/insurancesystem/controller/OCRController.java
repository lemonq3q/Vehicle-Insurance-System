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
public class OCRController {

    @Autowired
    private OCRService ocrService;

    @PostMapping("/idCard")
    public ResponseResult idCardRecognition(@RequestBody SystemFile systemFile){
        return ocrService.idCardRecognition(systemFile);
    }

    @PostMapping("/vehicleCertificate")
    public ResponseResult vehicleCertificateRecognition(@RequestBody SystemFile systemFile){
        return ocrService.vehicleCertificateRecognition(systemFile);
    }

    @PostMapping("/vehicleInvoice")
    public ResponseResult vehicleInvoiceRecognition(@RequestBody SystemFile systemFile){
        return ocrService.vehicleInvoiceRecognition(systemFile);
    }

    @PostMapping("/vehicleLicense")
    public ResponseResult vehicleLicenseRecognition(@RequestBody SystemFile systemFile){
        return ocrService.vehicleLicenseRecognition(systemFile);
    }

    @PostMapping("/businessLicense")
    public ResponseResult businessLicenseRecognition(@RequestBody SystemFile systemFile){
        return ocrService.businessLicenseRecognition(systemFile);
    }
}
