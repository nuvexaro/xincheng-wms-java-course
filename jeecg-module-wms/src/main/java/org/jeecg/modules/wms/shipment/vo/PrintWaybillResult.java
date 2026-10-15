package org.jeecg.modules.wms.shipment.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * @author Mr.M
 * @version 1.0
 * @description 打印电子面单结果
 * @date 2025/7/16 16:32
 */
@Data
public class PrintWaybillResult {
 //运单号
 private List<String> waybillNos;
 //token
 private String token;
 //顺丰云打印需要的面单数据: 每个出库单一组, 第一个包裹是主单, 其余是子单
 //每条: masterWaybillNo(主单号)、branchWaybillNo(子单号, 主单没有)、seq(第几个)、sum(这个出库单共几个)
 private List<Map<String, Object>> documents;
}
