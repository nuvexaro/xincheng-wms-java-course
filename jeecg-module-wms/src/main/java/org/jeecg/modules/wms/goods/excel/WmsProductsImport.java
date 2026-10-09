package org.jeecg.modules.wms.goods.excel;

import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.io.Serializable;

/**
 * @Description: 商品导入导出模型类
 *               属性的先后顺序 = Excel 里列的先后顺序; @ExcelProperty 里的名称 = Excel 的列名
 *               导出和导入用的是同一套列, 所以导出的文件改一改就可以直接拿来导入
 * @Version: V1.0
 */
@Data
@ColumnWidth(15)
public class WmsProductsImport implements Serializable {
    private static final long serialVersionUID = 1L;

    /**商品名称*/
    @ExcelProperty("商品名称")
    @ColumnWidth(25)
    private String productName;
    /**sku编码*/
    @ExcelProperty("sku编码")
    private String productCode;
    /**商品条码*/
    @ExcelProperty("商品条码")
    @ColumnWidth(22)
    private String productBarcode;
    /**商品规格*/
    @ExcelProperty("商品规格")
    private String productSpec;
    /**商品批次*/
    @ExcelProperty("商品批次")
    private String productBatch;
    /**供应商条码*/
    @ExcelProperty("供应商条码")
    private String supplierBarcode;
    /**宽*/
    @ExcelProperty("宽")
    private Double width;
    /**长*/
    @ExcelProperty("长")
    private Double length;
    /**高*/
    @ExcelProperty("高")
    private Double height;
    /**体积*/
    @ExcelProperty("体积")
    private Double volume;
    /**毛重*/
    @ExcelProperty("毛重")
    private Double grossWeight;
    /**净重*/
    @ExcelProperty("净重")
    private Double netWeight;
    /**包装规格*/
    @ExcelProperty("包装规格")
    private String packagingSpec;
    /**养护周期(天)*/
    @ExcelProperty("养护周期(天)")
    private Integer maintenanceCycle;
    /**保质期(天)*/
    @ExcelProperty("保质期(天)")
    private Integer shelfLife;
    /**计量单位*/
    @ExcelProperty("计量单位")
    private String unit;
    /**是否保质期管控*/
    @ExcelProperty("是否保质期管控")
    private Integer isExpiryControlled;
    /**状态*/
    @ExcelProperty("状态")
    private String status;
    /**货主名称*/
    @ExcelProperty("货主名称")
    @ColumnWidth(20)
    private String ownerName;
    /**商品分类*/
    @ExcelProperty("商品分类")
    private String categoryName;
    /**商品品牌*/
    @ExcelProperty("商品品牌")
    private String productBrandName;

    /**Excel 里的行号(导入时由监听器填, 用来提示第几行有问题; 不是 Excel 的列)*/
    @ExcelIgnore
    private Integer rowNo;
}
