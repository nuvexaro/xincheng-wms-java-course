package org.jeecg.modules.wms.wmstask.mapper;

import java.util.Date;

import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.wms.wmstask.entity.WmsTasks;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * @Description: 任务表
 * @Author: jeecg-boot
 * @Date:   2026-10-06
 * @Version: V1.0
 */
public interface WmsTasksMapper extends BaseMapper<WmsTasks> {

    /**
     * 分页查询任务列表(关联查询仓库名称、商品名称、货主名称、入库单号)
     *
     * @param page 分页参数
     * @param task 查询条件
     * @return 任务分页数据
     */
    IPage<WmsTasks> selectPageList(Page<WmsTasks> page, @Param("task") WmsTasks task);

    /**
     * 累加任务的已完成数量, 并同步更新任务状态
     * 在一条 SQL 里完成"判断不超量 + 累加 + 改状态", 并发执行也不会超量、不会丢数据
     *
     * @param id           任务id
     * @param execQuantity 本次执行数量
     * @param now          当前时间
     * @return 影响行数, 0 表示没更新成功(超过了计划数量, 或者任务已完成/已作废)
     */
    int increaseCompletedQuantity(@Param("id") String id, @Param("execQuantity") Integer execQuantity, @Param("now") Date now);
}
