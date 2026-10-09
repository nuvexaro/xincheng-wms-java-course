package org.jeecg.modules.wms.wmstask.service;

import org.jeecg.modules.wms.wmstask.entity.WmsTasks;
import org.jeecg.modules.wms.wmstask.entity.WmsTasksRecords;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @Description: 任务表
 *               任务表是收货、上架、拣货共用的, 这里只放和具体业务无关的通用方法;
 *               收货相关的业务(创建收货任务、收货)在 inorder 包的 IReceiveTasksService 里
 * @Author: jeecg-boot
 * @Date:   2026-10-06
 * @Version: V1.0
 */
public interface IWmsTasksService extends IService<WmsTasks> {

    /**
     * 分页查询任务列表(带仓库名称、商品名称、货主名称、入库单号)
     *
     * @param page     分页参数
     * @param wmsTasks 查询条件
     * @return 任务分页数据
     */
    IPage<WmsTasks> pageList(Page<WmsTasks> page, WmsTasks wmsTasks);

    /**
     * 根据id查询任务并加行锁(select ... for update), 必须在事务中调用
     *
     * @param id 任务id
     * @return 任务, 不存在时返回 null
     */
    WmsTasks getByIdForUpdate(String id);

    /**
     * 执行任务(通用方法, 收货/上架/拣货都可以用)
     * 1.校验总执行数量不能大于计划数量 2.添加任务执行记录 3.累加任务的已完成数量并更新任务状态
     *
     * @param wmsTasksRecords 任务执行记录(taskId、execQuantity 必填)
     * @return 执行后的最新任务信息
     */
    WmsTasks execute(WmsTasksRecords wmsTasksRecords);
}
