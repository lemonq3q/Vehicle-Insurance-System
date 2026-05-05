package com.example.insurancesystem.service;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.user.MerchantUserDTO;
import com.example.insurancesystem.domain.user.MerchantUserExcelDTO;
import com.example.insurancesystem.domain.user.MerchantUserSearchDTO;
import com.example.insurancesystem.domain.user.User;

import java.util.List;

public interface UserService {

    ResponseResult select(MerchantUserSearchDTO params);

    ResponseResult<User> selectByEmail(String email);

    List<MerchantUserExcelDTO> getExcel(MerchantUserSearchDTO params);

    ResponseResult insert(MerchantUserDTO params);

    ResponseResult update(MerchantUserDTO params);

    ResponseResult delete(Long id);

    ResponseResult deleteByMerchantId(Long merchantId);

    ResponseResult selectById(Long id);

    ResponseResult selectUserOptionsByMerchantId(Long merchantId);

    ResponseResult selectUserOptions(String blurParam);

    ResponseResult updatePassword(User params);

    ResponseResult updatePasswordByEmail(String phone, String password);

    ResponseResult selectSystemUser(MerchantUserSearchDTO params);

    ResponseResult selectNotApprovalUser(MerchantUserSearchDTO params);

    ResponseResult registerPersonal(User user);

    ResponseResult approvalUser(Long id);

    ResponseResult selectSystemUserOptions(String blurParam);
}
