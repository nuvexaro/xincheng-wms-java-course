package org.jeecg.modules.wms.inventory.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.wms.inventory.entity.WmsInventory;
import org.jeecg.modules.wms.inventory.excel.ImportInventoryListener;
import org.jeecg.modules.wms.inventory.excel.WmsInventoryExport;
import org.jeecg.modules.wms.inventory.excel.WmsInventoryImport;
import org.jeecg.modules.wms.inventory.service.IWmsInventoryService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * @Description: 库存表
 *               库存只能通过收货、上架等业务产生和变化, 所以这里只提供查询、导出和用于初始化的导入, 不提供新增、修改、删除
 * @Author: jeecg-boot
 * @Date:   2026-10-06
 * @Version: V1.0
 */
@Tag(name="库存表")
@RestController
@RequestMapping("/inventory/wmsInventory")
@Slf4j
public class WmsInventoryController {

    @Autowired
    private IWmsInventoryService wmsInventoryService;

    /**
     * 分页列表查询
     *
     * @param wmsInventory 查询条件: 货主编码、货主名称、商品编码、商品名称、储位编码、储区类型
     * @param pageNo
     * @param pageSize
     * @return
     */
    @Operation(summary="库存表-分页列表查询")
    @GetMapping(value = "/list")
    public Result<IPage<WmsInventory>> queryPageList(WmsInventory wmsInventory,
                                   @RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
                                   @RequestParam(name="pageSize", defaultValue="10") Integer pageSize) {
        Page<WmsInventory> page = new Page<WmsInventory>(pageNo, pageSize);
        IPage<WmsInventory> pageList = wmsInventoryService.pageList(page, wmsInventory);
        return Result.OK(pageList);
    }

    /**
     * 通过id查询
     *
     * @param id
     * @return
     */
    @Operation(summary="库存表-通过id查询")
    @GetMapping(value = "/queryById")
    public Result<WmsInventory> queryById(@RequestParam(name="id",required=true) String id) {
        WmsInventory wmsInventory = wmsInventoryService.getById(id);
        if(wmsInventory==null) {
            return Result.error("未找到对应数据");
        }
        return Result.OK(wmsInventory);
    }

    /**
     * 导出excel(EasyExcel): 按页面上的查询条件导出; 如果勾选了行, 只导出勾选的行
     * 导出字段见需求说明书 3.4.3: 仓库名称、货主编码、货主、商品编码、商品、储位编码、批号、保质期到期日、在库数量、分配数量、可用数量、入库时间
     *
     * @param wmsInventory 查询条件
     * @param selections   勾选的库存id, 多个以逗号分割
     */
    @RequestMapping(value = "/exportXls")
    public void exportXls(WmsInventory wmsInventory,
                          @RequestParam(name = "selections", required = false) String selections,
                          HttpServletResponse response) throws IOException {
        //1. 勾选的行
        List<String> ids = new ArrayList<>();
        if (oConvertUtils.isNotEmpty(selections)) {
            for (String id : selections.split(",")) {
                if (oConvertUtils.isNotEmpty(id.trim())) {
                    ids.add(id.trim());
                }
            }
        }
        //2. 查询要导出的库存, 转成导出模型: 导出哪些列、列的顺序, 由 WmsInventoryExport 里的 @ExcelProperty 注解决定
        List<WmsInventory> inventoryList = wmsInventoryService.exportList(wmsInventory, ids);
        List<WmsInventoryExport> exportList = new ArrayList<>();
        for (WmsInventory inventory : inventoryList) {
            WmsInventoryExport row = new WmsInventoryExport();
            BeanUtils.copyProperties(inventory, row);
            exportList.add(row);
        }
        //3. 用 EasyExcel 把数据写到响应流里, 浏览器收到后下载
        //   前端页面是按 .xls 保存文件的, 所以这里指定生成 xls 格式
        response.setContentType("application/vnd.ms-excel");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("库存表", "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xls");
        EasyExcel.write(response.getOutputStream(), WmsInventoryExport.class)
                .excelType(ExcelTypeEnum.XLS)
                .sheet("库存表")
                .doWrite(exportList);
    }

    /**
     * 通过excel导入库存(EasyExcel), 用于初始化库存
     * Excel 的列: 仓库名称、货主编码、货主、商品编码、商品名称、储位编码、批号、保质期到期日、数量
     *
     * @param file 上传的 Excel 文件
     * @return
     */
    @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
    public Result<?> importExcel(@RequestParam("file") MultipartFile file) throws IOException {
        // 监听器不能交给 spring 管理, 每次导入都 new 一个, 把 service 通过构造方法传进去
        ImportInventoryListener listener = new ImportInventoryListener(wmsInventoryService);
        EasyExcel.read(file.getInputStream(), WmsInventoryImport.class, listener).sheet().doRead();
        return Result.OK(listener.getResult().toMessage());
    }
}
