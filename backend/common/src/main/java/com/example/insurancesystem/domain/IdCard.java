package com.example.insurancesystem.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * 承载身份证 OCR 识别结果，供投保人、车主及企业联系人资料录入复用。
 */
public class IdCard {

    private String name;

    private String idNum;
}
