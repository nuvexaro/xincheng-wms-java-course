package org.jeecg.modules.wms.inventory.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.DateTimeFormat;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @Description: 库存导出模型类(导出字段见需求说明书 3.4.3 库存导出)
 * @Version: V1.0
 */
@Data
@ColumnWidth(15)
public class WmsInventoryExport implements Serializable {
    private static final long serialVersionUID = 1L;

    @ExcelProperty("仓库名称")
    @ColumnWidth(20)
    private String warehouseName;
    @ExcelProperty("货主编码")
    private String ownerCode;
    @ExcelProperty("货主")
    @ColumnWidth(20)
    private String ownerName;
    @ExcelProperty("商品编码")
    @ColumnWidth(20)
    private String productCode;
    @ExcelProperty("商品")
    @ColumnWidth(25)
    private String productName;
    @ExcelProperty("储位编码")
    @ColumnWidth(20)
    private String locationCode;
    @ExcelProperty("批号")
    private String batchNumber;
    @ExcelProperty("保质期到期日")
    @DateTimeFormat("yyyy-MM-dd")
    private Date expiryDate;
    @ExcelProperty("在库数量")
    private Integer stockQuantity;
    @ExcelProperty("分配数量")
    private Integer allocatedQuantity;
    @ExcelProperty("可用数量")
    private Integer availableQuantity;
    @ExcelProperty("入库时间")
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    @ColumnWidth(22)
    private Date stockInTime;
}
