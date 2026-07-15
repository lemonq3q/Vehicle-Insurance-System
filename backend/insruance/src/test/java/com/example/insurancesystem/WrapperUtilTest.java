package com.example.insurancesystem;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.insurancesystem.domain.merchant.Merchant;
import com.example.insurancesystem.mapper.MerchantMapper;
import com.example.insurancesystem.utils.WrapperUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;

@SpringBootTest
public class WrapperUtilTest {

    @Autowired
    private MerchantMapper upstreamMapper;

    @Test
    public void WrapperUtilUpdateTest() {
        Merchant upstream = new Merchant();
        upstream.setId(3L);
        upstream.setName("sdfwef");
        upstream.setContact("小王");
        UpdateWrapper<Merchant> wrapper = WrapperUtil.buildUpdateWrapper(upstream, Arrays.asList("id", "name"));
        System.out.println(upstreamMapper.update(null, wrapper));
    }

    @Test
    public void WrapperUtilDeleteTest() {
        Merchant upstream = new Merchant();
        QueryWrapper<Merchant> wrapper = WrapperUtil.buildDeleteWrapper(upstream);
    }

    @Test
    public void WrapperUtilBlurQueryTest() {
        Merchant upstream = new Merchant();
        upstream.setName("试");
        upstream.setCode("试");
        upstream.setAddress("青");
        upstream.setContact("青");
        upstream.setPhone("15762502276");
        QueryWrapper<Merchant> wrapper = WrapperUtil.buildQueryWrapperWithMultiBlurFields(
                upstream,
                Arrays.asList(Arrays.asList("name", "code"), Arrays.asList("address", "contact")));
        System.out.println(upstreamMapper.selectList(wrapper));
    }
}
