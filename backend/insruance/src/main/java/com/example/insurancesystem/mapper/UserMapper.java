package com.example.insurancesystem.mapper;

import com.example.insurancesystem.config.mybatisplus.BatchBaseMapper;
import com.example.insurancesystem.domain.user.MerchantUserDTO;
import com.example.insurancesystem.domain.user.MerchantUserSearchDTO;
import com.example.insurancesystem.domain.user.User;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;


@Mapper
public interface UserMapper extends BatchBaseMapper<User> {
    User selectLoginUser(String username);

    List<MerchantUserDTO> selectByMerchantUserSearchDTO(MerchantUserSearchDTO merchantUserSearchDTO);

    MerchantUserDTO selectMerchantUserDTOById(Long id);

    Long selectUserByRoleAndMerchantId(Long roleId, Long merchantId);

    List<User> selectPayeeByMerchantId(Long merchantId);

    List<MerchantUserDTO> selectSystemUserBySearchDTO(MerchantUserSearchDTO searchDTO);

    List<MerchantUserDTO> selectNotApprovalUser(MerchantUserSearchDTO searchDTO);

    List<User> selectSystemUserOptions(String blurParam);

    List<User> selectUserByMerchantId(Long merchantId);
}
