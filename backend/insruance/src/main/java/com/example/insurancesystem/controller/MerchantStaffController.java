package com.example.insurancesystem.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.merchant.MerchantStaffRoles;
import com.example.insurancesystem.domain.user.MerchantUserDTO;
import com.example.insurancesystem.domain.user.MerchantUserSearchDTO;
import com.example.insurancesystem.service.MerchantStaffService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/merchant-staff")
/**
 * 管理合作商户内部员工及其联系人、店员、收款人业务角色；这些角色不等同于系统菜单权限。
 */
public class MerchantStaffController {
    private final MerchantStaffService service;

    /**
     * 通过构造器注入商户员工服务，确保控制器创建时依赖完整。
     */
    public MerchantStaffController(MerchantStaffService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('user:select')")
    /**
     * 按账号、机构、角色和状态分页查询商户员工。
     */
    public ResponseResult select(MerchantUserSearchDTO params) { return service.select(params); }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user:select')")
    /**
     * 查询单个商户员工及其机构内角色详情。
     */
    public ResponseResult selectById(@PathVariable Long id) { return service.selectById(id); }

    @GetMapping("/merchant/{merchantId}")
    @PreAuthorize("hasAuthority('user:select')")
    /**
     * 查询指定合作机构下全部员工，供机构详情和角色维护使用。
     */
    public ResponseResult selectByMerchant(@PathVariable Long merchantId) { return service.selectByMerchantId(merchantId); }

    @GetMapping("/roles")
    /**
     * 返回固定商户业务角色选项，不涉及系统授权角色。
     */
    public ResponseResult roles() { return new ResponseResult(200, MerchantStaffRoles.options()); }

    @PostMapping
    @PreAuthorize("hasAuthority('user:update')")
    /**
     * 创建商户员工关联并分配机构内角色，重复联系人或收款人等约束由服务层校验。
     */
    public ResponseResult insert(@RequestBody MerchantUserDTO params) { return service.insert(params); }

    @PutMapping
    /**
     * 更新商户员工资料及角色；服务层合并缺失字段并维护默认收款人规则。
     */
    public ResponseResult update(@RequestBody MerchantUserDTO params) { return service.update(params); }

    @DeleteMapping
    @PreAuthorize("hasAuthority('user:update')")
    /**
     * 删除指定商户员工关联及其角色数据，并在需要时补选默认收款人。
     */
    public ResponseResult delete(Long id) { return service.delete(id); }
}
