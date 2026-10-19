package org.jeecg.modules.wms.ai.chat.tools;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.jeecg.modules.wms.ai.chat.dto.WmsInventoryQuery;
import org.jeecg.modules.wms.goods.entity.WmsCargoOwners;
import org.jeecg.modules.wms.goods.entity.WmsProducts;
import org.jeecg.modules.wms.goods.service.IWmsCargoOwnersService;
import org.jeecg.modules.wms.goods.service.IWmsProductsService;
import org.jeecg.modules.wms.inventory.entity.WmsInventory;
import org.jeecg.modules.wms.inventory.service.IWmsInventoryService;
import org.jeecg.modules.wms.warehouse.entity.WmsWarehouses;
import org.jeecg.modules.wms.warehouse.service.IWmsWarehousesService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * AI 库存查询工具
 */
@Component
public class WmsInventoryTools {

    /** 库存分页每页条数 */
    private static final int PAGE_SIZE = 10;

    /** 页码上限，避免大模型传入过大的页码 */
    private static final int MAX_PAGE_NO = 1000;

    /** 货主、仓库、商品列表最多返回条数 */
    private static final int MAX_LIST_SIZE = 50;

    @Autowired
    private IWmsInventoryService wmsInventoryService;

    @Autowired
    private IWmsCargoOwnersService wmsCargoOwnersService;

    @Autowired
    private IWmsWarehousesService wmsWarehousesService;

    @Autowired
    private IWmsProductsService wmsProductsService;

    /**
     * 分页查询库存
     */
    @Tool(description = "根据条件查询库存信息。仓库名称、商品名称、货主名称必须提供；结果分页，每页10条")
    public List<WmsInventory> queryInventory(
            @ToolParam(description = "库存查询条件", required = true) WmsInventoryQuery query,
            @ToolParam(description = "页码，从1开始，默认1，最大1000", required = false) Integer pageNo) {
        if (query == null) {
            return Collections.emptyList();
        }
        int pageNum = pageNo == null ? 1 : pageNo;
        if (pageNum < 1) {
            pageNum = 1;
        }
        if (pageNum > MAX_PAGE_NO) {
            pageNum = MAX_PAGE_NO;
        }
        WmsInventory wmsInventory = new WmsInventory();
        BeanUtils.copyProperties(query, wmsInventory);

        IPage<WmsInventory> page = wmsInventoryService.queryList(wmsInventory, pageNum, PAGE_SIZE);
        if (page == null || page.getRecords() == null) {
            return Collections.emptyList();
        }
        return page.getRecords();
    }

    /**
     * 查询货主信息，用于纠正用户输入的货主名称
     */
    @Tool(description = "查询货主信息（最多50条）。库存查询结果为空、怀疑用户输入的货主名称有误时调用，用来找到准确的货主名称")
    public List<WmsCargoOwners> queryOwnerInfo() {
        return wmsCargoOwnersService.page(new Page<>(1, MAX_LIST_SIZE)).getRecords();
    }

    /**
     * 查询仓库信息，用于纠正用户输入的仓库名称
     */
    @Tool(description = "查询仓库信息（最多50条）。库存查询结果为空、怀疑用户输入的仓库名称有误时调用，用来找到准确的仓库名称")
    public List<WmsWarehouses> queryWarehouses() {
        return wmsWarehousesService.page(new Page<>(1, MAX_LIST_SIZE)).getRecords();
    }

    /**
     * 查询商品信息，用于纠正用户输入的商品名称
     */
    @Tool(description = "查询商品信息（最多50条）。库存查询结果为空、怀疑用户输入的商品名称有误时调用，用来找到准确的商品名称")
    public List<WmsProducts> queryProducts() {
        return wmsProductsService.page(new Page<>(1, MAX_LIST_SIZE)).getRecords();
    }
}
