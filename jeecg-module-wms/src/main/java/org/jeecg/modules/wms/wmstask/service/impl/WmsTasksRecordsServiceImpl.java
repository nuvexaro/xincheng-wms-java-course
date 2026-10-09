package org.jeecg.modules.wms.wmstask.service.impl;

import org.jeecg.modules.wms.wmstask.entity.WmsTasksRecords;
import org.jeecg.modules.wms.wmstask.mapper.WmsTasksRecordsMapper;
import org.jeecg.modules.wms.wmstask.service.IWmsTasksRecordsService;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * @Description: 任务执行记录表
 * @Author: jeecg-boot
 * @Date:   2026-10-06
 * @Version: V1.0
 */
@Service
public class WmsTasksRecordsServiceImpl extends ServiceImpl<WmsTasksRecordsMapper, WmsTasksRecords> implements IWmsTasksRecordsService {

    /**
     * 分页查询任务执行记录
     */
    @Override
    public IPage<WmsTasksRecords> pageList(WmsTasksRecords wmsTasksRecords, Integer pageNo, Integer pageSize) {
        // 查询条件对象不能为 null, 否则 xml 里的 record.xxx 会报错
        if (wmsTasksRecords == null) {
            wmsTasksRecords = new WmsTasksRecords();
        }
        int current = (pageNo == null || pageNo < 1) ? 1 : pageNo;
        int size = (pageSize == null || pageSize < 1) ? 10 : pageSize;
        Page<WmsTasksRecords> page = new Page<>(current, size);
        return baseMapper.queryList(page, wmsTasksRecords);
    }
}
