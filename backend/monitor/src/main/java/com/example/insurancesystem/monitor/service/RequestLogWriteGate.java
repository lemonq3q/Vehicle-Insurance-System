package com.example.insurancesystem.monitor.service;

import java.time.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;

/** 日志入库协作闸门：仅暂停新入库事务，Redis消息仍保留；租约到期自动恢复以覆盖协调器退出。 */
@Component
public class RequestLogWriteGate {
    private final ReentrantLock lock=new ReentrantLock();
    private volatile Instant until=Instant.EPOCH;
    private String owner;
    /** 等待在途事务退出后设置有限暂停租约；不同备份不能互相覆盖。 */
    public void pause(String runId,int seconds) throws InterruptedException {
        if(runId==null||runId.isBlank()||seconds<1||seconds>7200)throw new IllegalArgumentException("暂停参数不合法");
        if(!lock.tryLock(30,TimeUnit.SECONDS))throw new IllegalStateException("日志事务尚未排空");
        try {if(paused()&&!runId.equals(owner))throw new IllegalStateException("其他备份正在暂停日志");owner=runId;until=Instant.now().plusSeconds(seconds);}finally{lock.unlock();}
    }
    /** 仅本次备份可提前解除暂停，迟到的恢复消息不影响后续备份。 */
    public void resume(String runId){lock.lock();try{if(runId.equals(owner)){until=Instant.EPOCH;owner=null;}}finally{lock.unlock();}}
    /** 消费者在事务开始前持有闸门，暂停请求须等事务及确认结束后才能成功。 */
    public boolean enter(){lock.lock();if(paused()){lock.unlock();return false;}return true;}
    /** 完成事务及消息确认后释放闸门。 */
    public void leave(){lock.unlock();}
    /** 过期租约不再阻止入库，不需要另一个数据库或调度线程恢复。 */
    public boolean paused(){return Instant.now().isBefore(until);}
}
