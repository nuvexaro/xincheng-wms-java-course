package org.jeecg.modules.wms.waybill.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.modules.wms.shipment.service.IWmsShipmentService;
import org.jeecg.modules.wms.shipment.vo.PrintWaybillResult;
import org.jeecg.modules.wms.waybill.sf.SfExpressUtil;
import org.jeecg.modules.wms.waybill.service.IWmsWaybillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.UnsupportedEncodingException;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import java.util.ArrayList;
import java.util.Map;


/**
* @Description: 运单接口类
* @Author: jeecg-boot
* @Date:   2025-06-18
* @Version: V1.0
*/
@Tag(name="运单接口类")
@RestController
@RequestMapping("/shipment/waybill")
@Slf4j
public class WmsWaybillController {
   @Autowired
   private IWmsShipmentService wmsShipmentService;
   @Autowired
   private IWmsWaybillService wmsWaybillService;


    /**
     * 根据波次打印电子面单
     * @param waveId 波次id
     *
     */
    @AutoLog(value = "波次-打印电子面单")
    @Operation(summary="波次-打印电子面单")
    @GetMapping (value = "/printWaybill")
    public Result<PrintWaybillResult> printWaybill(@RequestParam(name="waveId",required=true) String waveId) throws UnsupportedEncodingException {
        // 查询打印数据: 按出库单分好主单、子单(运单号从这里取, 不再单独查一次)
        List<Map<String, Object>> documents = wmsWaybillService.selectPrintDocumentsByWaveId(waveId);
        List<String> waybillNos = new ArrayList<>();
        for (Map<String, Object> document : documents) {
            Object no = document.containsKey("branchWaybillNo") ? document.get("branchWaybillNo") : document.get("masterWaybillNo");
            waybillNos.add(String.valueOf(no));
        }
        // 请求顺丰拿token(前端用 token 调顺丰云打印)
        String token = SfExpressUtil.getToken();
        if (StringUtils.isBlank(token)) {
            throw new JeecgBootException("获取顺丰token失败, 请检查顺丰参数配置");
        }
        PrintWaybillResult printWaybillResult = new PrintWaybillResult();
        printWaybillResult.setToken(token);
        printWaybillResult.setWaybillNos(waybillNos);
        // 按出库单分好主单、子单的打印数据, 前端直接用
        printWaybillResult.setDocuments(documents);
        return Result.OK(printWaybillResult);
    }
    /**
     * 根据波次进行发货
     */
    @AutoLog(value = "波次-发货")
    @Operation(summary="波次-发货")
    @GetMapping(value = "/send")
    public Result<String> send(@RequestParam(name="waveId",required=true) String waveId) {
        wmsWaybillService.send(waveId);
        return Result.OK("发货成功！");
    }



}
