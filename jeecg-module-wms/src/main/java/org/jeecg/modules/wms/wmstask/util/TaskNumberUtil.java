package org.jeecg.modules.wms.wmstask.util;

import org.jeecg.common.util.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * @Description: 任务号生成工具
 *               规则: 前缀 + 年月日 + 5位序号, 例如 TSK2026100600001
 *               序号使用 redis 的 INCR 生成, 原子自增, 保证并发下不重复
 *               注意: 所有任务类型(收货/上架/拣货)共用同一套当日流水, 都必须从这里取号
 * @Version: V1.0
 */
@Component
public class TaskNumberUtil {

    /** 日期部分格式: 年月日 */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 序号位数 */
    private static final int SEQ_LENGTH = 5;
    /** redis key 前缀, 按天区分, 一天一个 key */
    private static final String KEY_PREFIX = "wms:task:number:";
    /** key 过期时间(秒): 2天, 避免 redis 里 key 无限堆积 */
    private static final long EXPIRE_SECONDS = 2 * 24 * 60 * 60L;

    @Autowired
    private RedisUtil redisUtil;

    /**
     * 生成任务号
     *
     * @param prefix 任务号前缀(收货任务传 "TSK")
     * @return 任务号, 如 TSK2026100600001
     */
    public String generate(String prefix) {
        String date = LocalDate.now().format(DATE_FORMATTER);
        String key = KEY_PREFIX + date;

        // redis INCR 是原子操作, 多线程/多节点并发时序号也不会重复
        long seq = redisUtil.incr(key, 1);
        if (seq == 1) {
            // 当天第一次生成, 设置过期时间(第二天换新 key, 序号重新从 1 开始)
            redisUtil.expire(key, EXPIRE_SECONDS);
        }

        // 不足 5 位补零; 超过 5 位(一天超过 99999 个任务)时不截断, 直接变 6 位
        return prefix + date + String.format("%0" + SEQ_LENGTH + "d", seq);
    }
}
