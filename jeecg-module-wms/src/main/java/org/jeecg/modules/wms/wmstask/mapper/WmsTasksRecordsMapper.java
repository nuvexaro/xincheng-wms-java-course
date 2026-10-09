package org.jeecg.modules.wms.wmstask.mapper;

import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.wms.wmstask.entity.WmsTasksRecords;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * @Description: 任务执行记录表
 * @Author: jeecg-boot
 * @Date:   2026-10-06
 * @Version: V1.0
 */
public interface WmsTasksRecordsMapper extends BaseMapper<WmsTasksRecords> {

    /**
     * 分页查询任务执行记录(关联查询仓库名称、商品名称、货主名称、入库单号)
     *
     * @param page   分页参数
     * @param record 查询条件
     * @return 任务执行记录分页数据
     */
    IPage<WmsTasksRecords> queryList(Page<WmsTasksRecords> page, @Param("record") WmsTasksRecords record);
}
