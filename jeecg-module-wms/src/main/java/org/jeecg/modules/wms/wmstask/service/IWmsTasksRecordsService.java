package org.jeecg.modules.wms.wmstask.service;

import org.jeecg.modules.wms.wmstask.entity.WmsTasksRecords;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @Description: 任务执行记录表
 * @Author: jeecg-boot
 * @Date:   2026-10-06
 * @Version: V1.0
 */
public interface IWmsTasksRecordsService extends IService<WmsTasksRecords> {

    /**
     * 分页查询任务执行记录(带仓库名称、商品名称、货主名称、入库单号)
     *
     * @param wmsTasksRecords 查询条件
     * @param pageNo          页码
     * @param pageSize        每页条数
     * @return 任务执行记录分页数据
     */
    IPage<WmsTasksRecords> pageList(WmsTasksRecords wmsTasksRecords, Integer pageNo, Integer pageSize);
}
