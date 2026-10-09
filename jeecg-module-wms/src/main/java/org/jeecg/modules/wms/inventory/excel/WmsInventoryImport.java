package org.jeecg.modules.wms.inventory.excel;

import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.DateTimeFormat;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @Description: 库存导入模型类(导入字段见需求说明书 3.4.4 库存导入)
 *               必填: 仓库名称、货主编码、货主、商品编码、商品名称、储位编码、数量
 * @Version: V1.0
 */
@Data
@ColumnWidth(15)
public class WmsInventoryImport implements Serializable {
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
    @ExcelProperty("商品名称")
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
    @ExcelProperty("数量")
    private Integer quantity;

    /**Excel 里的行号(导入时由监听器填, 用来提示第几行有问题; 不是 Excel 的列)*/
    @ExcelIgnore
    private Integer rowNo;
}
