package com.example.insurancesystem.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.user.MerchantUserDTO;
import com.example.insurancesystem.domain.user.MerchantUserExcelDTO;
import com.example.insurancesystem.domain.user.MerchantUserSearchDTO;
import com.example.insurancesystem.domain.user.User;
import com.example.insurancesystem.service.UserService;
import com.example.insurancesystem.utils.SystemCommonUtil;
import com.example.insurancesystem.utils.WebUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping
    @PreAuthorize("hasAuthority('user:select')")
    public ResponseResult select(MerchantUserSearchDTO params){
        return userService.select(params);
    }

    @GetMapping("/system")
    @PreAuthorize("hasAuthority('user:select')")
    public ResponseResult selectSystemUser(MerchantUserSearchDTO params){
        return userService.selectSystemUser(params);
    }

    @GetMapping("/approval/not")
    @PreAuthorize("hasAuthority('user:select')")
    public ResponseResult selectNotApprovalUser(MerchantUserSearchDTO params){
        return userService.selectNotApprovalUser(params);
    }

    @GetMapping("/personal")
    public ResponseResult selectPersonal(){
        Long userid = SystemCommonUtil.getNowUserId();
        return userService.selectById(userid);
    }

    @GetMapping("/option/merchantId")
    @PreAuthorize("hasAuthority('user:select')")
    public ResponseResult selectUserOptionsByMerchantId(Long id){
        return userService.selectUserOptionsByMerchantId(id);
    }

    @GetMapping("/option")
    @PreAuthorize("hasAuthority('user:select')")
    public ResponseResult selectUserOptions(String blurParam){
        return userService.selectUserOptions(blurParam);
    }

    @GetMapping("/option/system")
    @PreAuthorize("hasAuthority('user:select')")
    public ResponseResult selectSystemUserOptions(String blurParam){
        return userService.selectSystemUserOptions(blurParam);
    }

    @GetMapping("/excel")
    @PreAuthorize("hasAuthority('user:select')")
    public void getExcel(MerchantUserSearchDTO params, HttpServletResponse response) {
        List<MerchantUserExcelDTO> excelDTOList = userService.getExcel(params);
        WebUtils.renderExcel(response, excelDTOList, MerchantUserExcelDTO.class);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user:select')")
    public ResponseResult selectById(@PathVariable("id") Long id){
        return userService.selectById(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('user:update')")
    public ResponseResult insert(@RequestBody MerchantUserDTO params){
        return userService.insert(params);
    }

    @PutMapping
    public ResponseResult update(@RequestBody MerchantUserDTO params){
        return userService.update(params);
    }

    @PutMapping("/approval")
    @PreAuthorize("hasAuthority('user:update')")
    public ResponseResult approvalUser(Long id){
        return userService.approvalUser(id);
    }

    @PutMapping("/password")
    public ResponseResult updatePassword(@RequestBody User params){
        return userService.updatePassword(params);
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('user:update')")
    public ResponseResult delete(Long id){
        return userService.delete(id);
    }
}
