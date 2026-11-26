package com.example.insurancesystem.monitor.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 验证暂停租约的归属与消费者恢复，不连接数据库和Redis。 */
class RequestLogWriteGateTest {
    /** 其他备份不可解除当前暂停，恢复后允许新事务进入。 */
    @Test void pauseOwnershipAndResume()throws Exception{RequestLogWriteGate gate=new RequestLogWriteGate();gate.pause("run-1",30);assertFalse(gate.enter());assertThrows(IllegalStateException.class,()->gate.pause("run-2",30));gate.resume("run-2");assertTrue(gate.paused());gate.resume("run-1");assertTrue(gate.enter());gate.leave();}
    /** 拒绝无限暂停或空运行标识，不改变原有可消费状态。 */
    @Test void rejectsInvalidLease(){RequestLogWriteGate gate=new RequestLogWriteGate();assertThrows(IllegalArgumentException.class,()->gate.pause("",30));assertThrows(IllegalArgumentException.class,()->gate.pause("run",7201));assertFalse(gate.paused());}
}
