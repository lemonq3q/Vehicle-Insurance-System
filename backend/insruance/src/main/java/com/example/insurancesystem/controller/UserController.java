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
/**
 * 车险账号与商户用户管理入口，覆盖企业用户、系统用户、审核、选项、导出、个人资料和密码维护。
 */
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping
    @PreAuthorize("hasAuthority('user:select')")
    /**
     * 按机构、角色、状态和关键字分页查询商户业务用户。
     */
    public ResponseResult select(MerchantUserSearchDTO params){
        return userService.select(params);
    }

    @GetMapping("/system")
    @PreAuthorize("hasAuthority('user:select')")
    /**
     * 分页查询不隶属于商户员工关系的系统管理用户。
     */
    public ResponseResult selectSystemUser(MerchantUserSearchDTO params){
        return userService.selectSystemUser(params);
    }

    @GetMapping("/approval/not")
    @PreAuthorize("hasAuthority('user:select')")
    /**
     * 查询注册后仍待审核的账号，供管理员审批列表使用。
     */
    public ResponseResult selectNotApprovalUser(MerchantUserSearchDTO params){
        return userService.selectNotApprovalUser(params);
    }

    @GetMapping("/personal")
    /**
     * 从安全上下文获取当前用户 ID 并返回本人资料，不允许客户端指定其他用户。
     */
    public ResponseResult selectPersonal(){
        Long userid = SystemCommonUtil.getNowUserId();
        return userService.selectById(userid);
    }

    @GetMapping("/option/merchantId")
    @PreAuthorize("hasAuthority('user:select')")
    /**
     * 返回指定商户下可选用户，用于联系人、经办人或收款人选择。
     */
    public ResponseResult selectUserOptionsByMerchantId(Long id){
        return userService.selectUserOptionsByMerchantId(id);
    }

    @GetMapping("/option")
    @PreAuthorize("hasAuthority('user:select')")
    /**
     * 按模糊关键字返回普通用户精简选项。
     */
    public ResponseResult selectUserOptions(String blurParam){
        return userService.selectUserOptions(blurParam);
    }

    @GetMapping("/option/system")
    @PreAuthorize("hasAuthority('user:select')")
    /**
     * 按模糊关键字返回系统用户精简选项。
     */
    public ResponseResult selectSystemUserOptions(String blurParam){
        return userService.selectSystemUserOptions(blurParam);
    }

    @GetMapping("/excel")
    @PreAuthorize("hasAuthority('user:select')")
    /**
     * 按当前筛选导出商户用户 Excel，并直接写入 HTTP 响应流。
     */
    public void getExcel(MerchantUserSearchDTO params, HttpServletResponse response) {
        List<MerchantUserExcelDTO> excelDTOList = userService.getExcel(params);
        WebUtils.renderExcel(response, excelDTOList, MerchantUserExcelDTO.class);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user:select')")
    /**
     * 查询指定用户及其系统角色、商户关系等完整资料。
     */
    public ResponseResult selectById(@PathVariable("id") Long id){
        return userService.selectById(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('user:update')")
    /**
     * 由管理员创建用户并分配系统或商户角色，手机号邮箱重复和租户关系由服务层校验。
     */
    public ResponseResult insert(@RequestBody MerchantUserDTO params){
        return userService.insert(params);
    }

    @PutMapping
    /**
     * 更新用户资料；服务层结合当前用户权限限制可修改范围并保持关联数据一致。
     */
    public ResponseResult update(@RequestBody MerchantUserDTO params){
        return userService.update(params);
    }

    @PutMapping("/approval")
    @PreAuthorize("hasAuthority('user:update')")
    /**
     * 审批待审核账号并启用相应企业成员关系。
     */
    public ResponseResult approvalUser(Long id){
        return userService.approvalUser(id);
    }

    @PutMapping("/password")
    /**
     * 修改当前用户密码，服务层校验原密码并使用 BCrypt 保存新哈希。
     */
    public ResponseResult updatePassword(@RequestBody User params){
        return userService.updatePassword(params);
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('user:update')")
    /**
     * 删除指定用户及可清理的角色、商户和租户关联，引用冲突由服务层返回业务错误。
     */
    public ResponseResult delete(Long id){
        return userService.delete(id);
    }
}
