package org.jeecg.modules.wms.wmstask.service.impl;

import java.util.Date;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.wms.config.WarehouseDictEnum;
import org.jeecg.modules.wms.wmstask.entity.WmsTasks;
import org.jeecg.modules.wms.wmstask.entity.WmsTasksRecords;
import org.jeecg.modules.wms.wmstask.mapper.WmsTasksMapper;
import org.jeecg.modules.wms.wmstask.service.IWmsTasksRecordsService;
import org.jeecg.modules.wms.wmstask.service.IWmsTasksService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * @Description: 任务表
 * @Author: jeecg-boot
 * @Date:   2026-10-06
 * @Version: V1.0
 */
@Service
public class WmsTasksServiceImpl extends ServiceImpl<WmsTasksMapper, WmsTasks> implements IWmsTasksService {

    @Autowired
    private IWmsTasksRecordsService wmsTasksRecordsService;

    /**
     * 分页查询任务列表
     */
    @Override
    public IPage<WmsTasks> pageList(Page<WmsTasks> page, WmsTasks wmsTasks) {
        // 查询条件对象不能为 null, 否则 xml 里的 task.xxx 会报错
        if (wmsTasks == null) {
            wmsTasks = new WmsTasks();
        }
        return baseMapper.selectPageList(page, wmsTasks);
    }

    /**
     * 查询任务并加行锁
     */
    @Override
    public WmsTasks getByIdForUpdate(String id) {
        if (oConvertUtils.isEmpty(id)) {
            return null;
        }
        return baseMapper.selectOne(new LambdaQueryWrapper<WmsTasks>()
                .eq(WmsTasks::getId, id)
                .last("FOR UPDATE"));
    }

    /**
     * 执行任务通用方法
     *
     * @param wmsTasksRecords 前端传过来的任务执行记录, 将要添加到任务记录表
     * @return 执行后的最新任务信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public WmsTasks execute(WmsTasksRecords wmsTasksRecords) {
        //0.参数校验
        if (wmsTasksRecords == null || oConvertUtils.isEmpty(wmsTasksRecords.getTaskId())) {
            throw new JeecgBootException("任务id不能为空");
        }
        Integer execQuantity = wmsTasksRecords.getExecQuantity();
        if (execQuantity == null || execQuantity <= 0) {
            throw new JeecgBootException("执行数量必须大于0");
        }

        //1.查询任务(加行锁, 同一个任务同一时刻只允许一个人执行), 总执行数量不能大于计划数量
        WmsTasks wmsTasks = getByIdForUpdate(wmsTasksRecords.getTaskId());
        if (wmsTasks == null) {
            throw new JeecgBootException("任务不存在");
        }
        if (WarehouseDictEnum.TASK_STATUS_COMPLETED.getCode().equals(wmsTasks.getTaskStatus())) {
            throw new JeecgBootException("任务" + wmsTasks.getTaskNumber() + "已完成, 不能重复执行");
        }
        if (WarehouseDictEnum.TASK_STATUS_CANCELED.getCode().equals(wmsTasks.getTaskStatus())) {
            throw new JeecgBootException("任务" + wmsTasks.getTaskNumber() + "已作废, 不能执行");
        }
        // 计划数量
        int quantity = wmsTasks.getQuantity() == null ? 0 : wmsTasks.getQuantity();
        // 已完成数量
        int completedQuantity = wmsTasks.getCompletedQuantity() == null ? 0 : wmsTasks.getCompletedQuantity();
        if (completedQuantity + execQuantity > quantity) {
            throw new JeecgBootException("总执行数量不能大于计划数量, 本次最多还能执行" + (quantity - completedQuantity));
        }

        //2.添加任务执行记录
        Date now = new Date();
        setTaskRecordProperties(wmsTasksRecords, wmsTasks, now);
        boolean saved = wmsTasksRecordsService.save(wmsTasksRecords);
        if (!saved) {
            throw new JeecgBootException("添加任务记录失败");
        }

        //3.在任务表累加已完成数量, 并更新任务状态(执行中/已完成)
        int rows = baseMapper.increaseCompletedQuantity(wmsTasks.getId(), execQuantity, now);
        if (rows == 0) {
            throw new JeecgBootException("更新任务失败, 任务的数量或状态已发生变化, 请刷新后重试");
        }

        //4.查询最新任务信息返回
        return getById(wmsTasks.getId());
    }

    /**
     * 设置任务执行记录的属性: 和任务相关的信息一律以任务表为准, 不信任前端传过来的值
     */
    private void setTaskRecordProperties(WmsTasksRecords wmsTasksRecords, WmsTasks wmsTasks, Date now) {
        //主键置空, 由 mybatis-plus 自动生成
        wmsTasksRecords.setId(null);
        //任务id
        wmsTasksRecords.setTaskId(wmsTasks.getId());
        //任务号
        wmsTasksRecords.setTaskNumber(wmsTasks.getTaskNumber());
        //任务类型
        wmsTasksRecords.setTaskType(wmsTasks.getTaskType());
        //商品id
        wmsTasksRecords.setProductId(wmsTasks.getProductId());
        //目的仓库、来源仓库
        wmsTasksRecords.setTargetWarehouseId(wmsTasks.getTargetWarehouseId());
        wmsTasksRecords.setSourceWarehouseId(wmsTasks.getSourceWarehouseId());
        //入库单id、入库明细id
        wmsTasksRecords.setStockInOrderId(wmsTasks.getStockInOrderId());
        wmsTasksRecords.setStockInOrderItemId(wmsTasks.getStockInOrderItemId());
        //出库单id、波次单id、波次拣货明细id(拣货任务时用到)
        wmsTasksRecords.setOutOrderId(wmsTasks.getOutOrderId());
        wmsTasksRecords.setWaveOrderId(wmsTasks.getWaveOrderId());
        wmsTasksRecords.setWaveSkuSummaryId(wmsTasks.getWaveSkuSummaryId());
        //执行人
        wmsTasksRecords.setOperator(wmsTasks.getOperator());
        //执行时间: 调用方没指定就用当前时间
        if (wmsTasksRecords.getOperationTime() == null) {
            wmsTasksRecords.setOperationTime(now);
        }
        //批次号、保质期、储位: 由调用方(收货/上架业务)在调用前设置好, 这里不处理
    }
}
