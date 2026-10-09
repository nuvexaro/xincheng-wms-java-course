package org.jeecg.modules.wms.outorder.controller;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.modules.wms.outorder.entity.WmsOutOrders;
import org.jeecg.modules.wms.outorder.entity.WmsOutOrdersAllocation;
import org.jeecg.modules.wms.outorder.entity.WmsOutOrdersItems;
import org.jeecg.modules.wms.outorder.service.IWmsOutOrdersService;
import org.jeecg.modules.wms.outorder.vo.WmsOutOrdersPage;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Description: 出库单主表
 * @Version: V1.0
 */
@Tag(name="出库单主表")
@RestController
@RequestMapping("/outorder/wmsOutOrders")
@Slf4j
public class WmsOutOrdersController {

    @Autowired
    private IWmsOutOrdersService wmsOutOrdersService;

    /**
     * 分页列表查询
     */
    @Operation(summary="出库单主表-分页列表查询")
    @GetMapping(value = "/list")
    public Result<IPage<WmsOutOrders>> queryPageList(WmsOutOrders wmsOutOrders,
                                   @RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
                                   @RequestParam(name="pageSize", defaultValue="10") Integer pageSize,
                                   HttpServletRequest req) {
        QueryWrapper<WmsOutOrders> queryWrapper = QueryGenerator.initQueryWrapper(wmsOutOrders, req.getParameterMap());
        Page<WmsOutOrders> page = new Page<WmsOutOrders>(pageNo, pageSize);
        IPage<WmsOutOrders> pageList = wmsOutOrdersService.pageList(page, queryWrapper);
        return Result.OK(pageList);
    }

    /**
     * 添加: 只添加出库单主表, 自动生成出库单号; 出库明细在"编辑"里添加
     */
    @AutoLog(value = "出库单主表-添加")
    @Operation(summary="出库单主表-添加")
    @PostMapping(value = "/add")
    public Result<String> add(@RequestBody WmsOutOrdersPage wmsOutOrdersPage) {
        WmsOutOrders wmsOutOrders = new WmsOutOrders();
        BeanUtils.copyProperties(wmsOutOrdersPage, wmsOutOrders);
        wmsOutOrdersService.add(wmsOutOrders);
        return Result.OK("添加成功！");
    }

    /**
     * 编辑: 修改出库单和出库单明细
     */
    @AutoLog(value = "出库单主表-编辑")
    @Operation(summary="出库单主表-编辑")
    @RequestMapping(value = "/edit", method = {RequestMethod.PUT,RequestMethod.POST})
    public Result<String> edit(@RequestBody WmsOutOrdersPage wmsOutOrdersPage) {
        wmsOutOrdersService.updateMain(wmsOutOrdersPage, wmsOutOrdersPage.getWmsOutOrdersItemsList());
        return Result.OK("编辑成功!");
    }

    /**
     * 通过id删除
     */
    @AutoLog(value = "出库单主表-通过id删除")
    @Operation(summary="出库单主表-通过id删除")
    @DeleteMapping(value = "/delete")
    public Result<String> delete(@RequestParam(name="id",required=true) String id) {
        wmsOutOrdersService.delMain(id);
        return Result.OK("删除成功!");
    }

    /**
     * 批量删除
     */
    @AutoLog(value = "出库单主表-批量删除")
    @Operation(summary="出库单主表-批量删除")
    @DeleteMapping(value = "/deleteBatch")
    public Result<String> deleteBatch(@RequestParam(name="ids",required=true) String ids) {
        wmsOutOrdersService.delBatchMain(ids);
        return Result.OK("批量删除成功！");
    }

    /**
     * 通过id查询
     */
    @Operation(summary="出库单主表-通过id查询")
    @GetMapping(value = "/queryById")
    public Result<WmsOutOrders> queryById(@RequestParam(name="id",required=true) String id) {
        WmsOutOrders wmsOutOrders = wmsOutOrdersService.getById(id);
        if(wmsOutOrders==null) {
            return Result.error("未找到对应数据");
        }
        return Result.OK(wmsOutOrders);
    }

    /**
     * 通过出库单id查询出库单明细
     */
    @Operation(summary="出库单明细-通过主表ID查询")
    @GetMapping(value = "/queryWmsOutOrdersItemsByMainId")
    public Result<List<WmsOutOrdersItems>> queryWmsOutOrdersItemsListByMainId(@RequestParam(name="id",required=true) String id) {
        return Result.OK(wmsOutOrdersService.selectItemsByMainId(id));
    }

    /**
     * 通过出库单id查询库存分配明细
     */
    @Operation(summary="出库分配明细-通过主表ID查询")
    @GetMapping(value = "/queryWmsOutOrdersAllocationByMainId")
    public Result<List<WmsOutOrdersAllocation>> queryWmsOutOrdersAllocationListByMainId(@RequestParam(name="id",required=true) String id) {
        return Result.OK(wmsOutOrdersService.selectAllocationByMainId(id));
    }

    /**
     * 提交审核: 出库单状态由 已创建、审核失败 更新为 提交审核
     */
    @AutoLog(value = "出库单主表-提交审核")
    @Operation(summary="出库单主表-提交审核")
    @PostMapping(value = "/submitAudit")
    public Result<String> submitAudit(@RequestParam(name="id",required=false) String id) {
        wmsOutOrdersService.submitAudit(id);
        return Result.OK("提交审核成功！");
    }

    /**
     * 审核: 出库单状态由 提交审核 更新为 审核通过 或 审核失败
     *
     * @param wmsOutOrders 只用到 id 和 status(审核结果: APPROVED 审核通过, REJECTED 审核失败)
     */
    @AutoLog(value = "出库单主表-审核")
    @Operation(summary="出库单主表-审核")
    @PostMapping(value = "/audit")
    public Result<String> audit(@RequestBody WmsOutOrders wmsOutOrders) {
        wmsOutOrdersService.audit(wmsOutOrders.getId(), wmsOutOrders.getStatus());
        return Result.OK("审核成功！");
    }

    /**
     * 分配库存: 按先到期先出、先进先出的策略给出库单分配库存并锁定; 明细指定了批号的只分配该批号的库存
     *
     * @param ids 出库单id, 多个以逗号分割
     */
    @AutoLog(value = "出库单主表-分配库存")
    @Operation(summary="出库单主表-分配库存")
    @PostMapping(value = "/allocateStock")
    public Result<String> allocateStock(@RequestParam(name="ids",required=false) String ids) {
        String message = wmsOutOrdersService.allocateStock(ids);
        return Result.OK(message);
    }
}
