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
public class MerchantStaffController {
    private final MerchantStaffService service;

    public MerchantStaffController(MerchantStaffService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('user:select')")
    public ResponseResult select(MerchantUserSearchDTO params) { return service.select(params); }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user:select')")
    public ResponseResult selectById(@PathVariable Long id) { return service.selectById(id); }

    @GetMapping("/merchant/{merchantId}")
    @PreAuthorize("hasAuthority('user:select')")
    public ResponseResult selectByMerchant(@PathVariable Long merchantId) { return service.selectByMerchantId(merchantId); }

    @GetMapping("/roles")
    public ResponseResult roles() { return new ResponseResult(200, MerchantStaffRoles.options()); }

    @PostMapping
    @PreAuthorize("hasAuthority('user:update')")
    public ResponseResult insert(@RequestBody MerchantUserDTO params) { return service.insert(params); }

    @PutMapping
    public ResponseResult update(@RequestBody MerchantUserDTO params) { return service.update(params); }

    @DeleteMapping
    @PreAuthorize("hasAuthority('user:update')")
    public ResponseResult delete(Long id) { return service.delete(id); }
}
