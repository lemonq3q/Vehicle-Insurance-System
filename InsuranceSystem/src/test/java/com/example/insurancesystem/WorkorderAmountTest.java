package com.example.insurancesystem;

import com.example.insurancesystem.domain.workorder.Workorder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkorderAmountTest {

    @Test
    void computesTaxExclusivePercentageWithRepeatingDecimal() {
        Workorder workorder = new Workorder();
        workorder.setCommercialAmount(new BigDecimal("100"));
        workorder.setUpstreamCommercialPercentage(new BigDecimal("10"));
        workorder.setUpstreamComputeType(1);

        workorder.computeAmount();

        assertEquals(new BigDecimal("9.43"), workorder.getUpstreamCommercialAmount());
    }
}
