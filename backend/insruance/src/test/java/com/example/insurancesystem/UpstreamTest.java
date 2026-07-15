package com.example.insurancesystem;

import com.example.insurancesystem.domain.merchant.Merchant;
import com.example.insurancesystem.domain.merchant.MerchantArea;
import com.example.insurancesystem.domain.merchant.UpstreamDTO;
import com.example.insurancesystem.domain.merchant.UpstreamSearchDTO;
import com.example.insurancesystem.mapper.MerchantAreaMapper;
import com.example.insurancesystem.service.UpstreamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


@SpringBootTest
public class UpstreamTest {

    @Autowired
    private UpstreamService upstreamService;

    @Autowired
    private MerchantAreaMapper upstreamAreaMapper;

    @Test
    public void select() {
        UpstreamSearchDTO upstream = new UpstreamSearchDTO();
        upstream.setBlurParam("0");
        upstream.setPageSize(10);
        upstream.setPageNum(1);
        System.out.println(upstreamService.select(upstream));
    }

    @Test
    public void insert() {
        UpstreamDTO upstream = new UpstreamDTO();
        upstream.setName("批量插入临时测试");
        upstream.setContact("小李");
        upstream.setBusinessArea(Arrays.asList("200","300","400"));
        upstreamService.insert(upstream);
    }

    @Test
    public void update() {
        UpstreamDTO upstream = new UpstreamDTO();
        upstream.setId(3L);
        upstream.setContact("小华");
        upstreamService.update(upstream);
    }


    @Test
    public void delete() {
        upstreamService.delete(1L);
    }

    @Test
    public void batchInsert() {
        List<MerchantArea> list = new ArrayList<>();
        for (int i = 0; i < 1; i++) {
            MerchantArea upstreamArea = new MerchantArea();
            upstreamArea.setMerchantId(1000L);
            upstreamArea.setAreaCode(Integer.toString(i));
            upstreamArea.setIsDelete(0);
            list.add(upstreamArea);
        }
        upstreamAreaMapper.insertBatchSomeColumn(list);
        System.out.println(list);
    }

}
