package org.jeecg.modules.wms.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.modules.wms.waybill.sf.SfExpressUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 顺丰接口参数配置(课件 9.5.1)
 * 从 application-dev.yml 的 wms.express_api 读取顺丰参数, 设置到 SfExpressUtil 里
 * 课件说从课程资料拷贝这个类, 我没有这个文件, 按 SfExpressUtil 的用法写的
 *
 * application-dev.yml 里的配置:
 * wms:
 *   express_api:
 *     client_code: 顾客编码
 *     check_word: 校验码
 *     call_url: https://sfapi-sbox.sf-express.com/std/service
 *     monthly_card: 月结卡号
 */
@Slf4j
@Configuration
public class SfExpressConfig {

    /** 顾客编码(丰桥上应用的 clientCode); 冒号后面是默认值, 没配置时为空, 项目也能启动 */
    @Value("${wms.express_api.client_code:}")
    private String clientCode;

    /** 校验码 */
    @Value("${wms.express_api.check_word:}")
    private String checkWord;

    /** 顺丰接口地址, 默认是沙箱(测试)环境 */
    @Value("${wms.express_api.call_url:https://sfapi-sbox.sf-express.com/std/service}")
    private String callUrl;

    /** 月结卡号 */
    @Value("${wms.express_api.monthly_card:}")
    private String monthlyCard;

    /**
     * 项目启动、读到配置后, 把参数设置到顺丰工具类里
     */
    @PostConstruct
    public void init() {
        SfExpressUtil.setClient_code(clientCode);
        SfExpressUtil.setCheck_word(checkWord);
        SfExpressUtil.setCall_url(callUrl);
        SfExpressUtil.setMonthly_card(monthlyCard);
        if (!isConfigured()) {
            log.warn("没有配置顺丰接口参数(wms.express_api), 生成运单的定时任务不会请求顺丰");
        }
    }

    /**
     * 顺丰参数是否配置齐全
     */
    public boolean isConfigured() {
        return StringUtils.isNotBlank(clientCode) && StringUtils.isNotBlank(checkWord) && StringUtils.isNotBlank(monthlyCard);
    }
}
