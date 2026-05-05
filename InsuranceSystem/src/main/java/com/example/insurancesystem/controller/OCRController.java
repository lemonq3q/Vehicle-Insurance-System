package com.example.insurancesystem.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.service.OCRService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/ocr")
public class OCRController {

    @Autowired
    private OCRService ocrService;

    @PostMapping("/idCard")
    public ResponseResult idCardRecognition(@RequestParam("file") MultipartFile file){
        return ocrService.idCardRecognition(file);
    }

    @PostMapping("/vehicleCertificate")
    public ResponseResult vehicleCertificateRecognition(@RequestParam("file") MultipartFile file){
        return ocrService.vehicleCertificateRecognition(file);
    }

    @PostMapping("/vehicleInvoice")
    public ResponseResult vehicleInvoiceRecognition(@RequestParam("file") MultipartFile file){
        return ocrService.vehicleInvoiceRecognition(file);
    }

    @PostMapping("/vehicleLicense")
    public ResponseResult vehicleLicenseRecognition(@RequestParam("file") MultipartFile file){
        return ocrService.vehicleLicenseRecognition(file);
    }

    @PostMapping("/businessLicense")
    public ResponseResult businessLicenseRecognition(@RequestParam("file") MultipartFile file){
        return ocrService.businessLicenseRecognition(file);
    }
}
