package org.jeecg.modules.wms.ai.chat.dto;

import lombok.Data;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * AI 库存查询条件
 * <p>
 * 字段名与 WmsInventory 中同名的非表字段保持一致，供 BeanUtils.copyProperties 使用。
 */
@Data
public class WmsInventoryQuery {

    @ToolParam(description = "商品名称(必填)", required = true)
    private String productName;

    @ToolParam(description = "商品编码", required = false)
    private String productCode;

    @ToolParam(description = "货主名称(必填)", required = true)
    private String ownerName;

    @ToolParam(description = "货主编码", required = false)
    private String ownerCode;

    @ToolParam(description = "仓库名称(必填)", required = true)
    private String warehouseName;

    @ToolParam(description = "仓库编码", required = false)
    private String warehouseCode;

    @ToolParam(description = "储位编码", required = false)
    private String locationCode;
}
