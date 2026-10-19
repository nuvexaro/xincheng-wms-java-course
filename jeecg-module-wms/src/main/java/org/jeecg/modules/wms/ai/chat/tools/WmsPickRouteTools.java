package org.jeecg.modules.wms.ai.chat.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootBizTipException;
import org.jeecg.modules.wms.wave.entity.WmsWaveMaster;
import org.jeecg.modules.wms.wave.entity.WmsWaveSkuSummary;
import org.jeecg.modules.wms.wave.service.IPickingTasksService;
import org.jeecg.modules.wms.wave.service.IWmsWaveMasterService;
import org.jeecg.modules.wms.wave.service.IWmsWaveSkuSummaryService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 拣货路径查询工具
 */
@Component
public class WmsPickRouteTools {

    @Autowired
    private IWmsWaveMasterService wmsWaveMasterService;

    @Autowired
    private IWmsWaveSkuSummaryService wmsWaveSkuSummaryService;

    @Autowired
    private IPickingTasksService pickingTasksService;

    /**
     * 根据波次号查询拣货路径文本
     */
    @Tool(description = "根据波次号查询拣货路径，返回文本形式的拣货路径")
    public String queryPickRoute(@ToolParam(description = "波次号", required = true) String waveNo) {
        if (StringUtils.isBlank(waveNo)) {
            return "请输入波次号";
        }
        String waveNoTrim = waveNo.trim();
        WmsWaveMaster wave = wmsWaveMasterService.getOne(
                new LambdaQueryWrapper<WmsWaveMaster>().eq(WmsWaveMaster::getWaveNo, waveNoTrim), false);
        if (wave == null) {
            return "未找到波次 " + waveNoTrim;
        }
        // 已经生成过路径文本时直接返回
        if (StringUtils.isNotBlank(wave.getPickPath())) {
            return wave.getPickPath();
        }
        List<WmsWaveSkuSummary> waveSkuSummaries = wmsWaveSkuSummaryService.selectByWaveId(wave.getId());
        if (waveSkuSummaries == null || waveSkuSummaries.isEmpty()) {
            return "该波次没有拣货明细";
        }
        try {
            // 复用现有的拣货路径计算逻辑：计算完成会把路径文本、svg 图片保存到波次表
            pickingTasksService.viewPickPath(wave.getId());
        } catch (JeecgBootBizTipException e) {
            return StringUtils.isNotBlank(e.getMessage()) ? e.getMessage() : "无法规划拣货路径";
        }
        // viewPickPath 返回的是 svg，这里重新查询波次表，只返回文本形式的路径
        WmsWaveMaster updatedWave = wmsWaveMasterService.getById(wave.getId());
        if (updatedWave != null && StringUtils.isNotBlank(updatedWave.getPickPath())) {
            return updatedWave.getPickPath();
        }
        return "该波次暂未生成拣货路径";
    }
}
