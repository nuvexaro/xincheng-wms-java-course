package org.jeecg.modules.wms.inventory.entity;

import java.io.Serializable;
import java.util.Date;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;
import org.jeecgframework.poi.excel.annotation.Excel;
import org.jeecg.common.aspect.annotation.Dict;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @Description: 库存表
 *               库存唯一标识: 商品id + 储位编码 + 批号
 * @Author: jeecg-boot
 * @Date:   2026-10-06
 * @Version: V1.0
 */
@Data
@TableName("wms_inventory")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description="库存表")
public class WmsInventory implements Serializable {
    private static final long serialVersionUID = 1L;

    /**主键*/
    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private java.lang.String id;
    /**创建人*/
    @Schema(description = "创建人")
    private java.lang.String createBy;
    /**创建日期*/
    @JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建日期")
    private java.util.Date createTime;
    /**更新人*/
    @Schema(description = "更新人")
    private java.lang.String updateBy;
    /**更新日期*/
    @JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新日期")
    private java.util.Date updateTime;
    /**所属部门*/
    @Schema(description = "所属部门")
    private java.lang.String sysOrgCode;
    // ========== 下面字段的先后顺序就是导出 Excel 时列的顺序; 标了 exist = false 的是关联查询出来的显示字段, 不是库存表的列 ==========
    /**仓库名称*/
    @TableField(exist = false)
    @Excel(name = "仓库名称", width = 20)
    @Schema(description = "仓库名称")
    private java.lang.String warehouseName;
    /**货主编码*/
    @TableField(exist = false)
    @Excel(name = "货主编码", width = 15)
    @Schema(description = "货主编码")
    private java.lang.String ownerCode;
    /**货主名称*/
    @TableField(exist = false)
    @Excel(name = "货主", width = 20)
    @Schema(description = "货主名称")
    private java.lang.String ownerName;
    /**商品编码*/
    @TableField(exist = false)
    @Excel(name = "商品编码", width = 20)
    @Schema(description = "商品编码")
    private java.lang.String productCode;
    /**商品名称*/
    @TableField(exist = false)
    @Excel(name = "商品", width = 25)
    @Schema(description = "商品名称")
    private java.lang.String productName;
    /**储位编码*/
    @Excel(name = "储位编码", width = 20)
    @Schema(description = "储位编码")
    private java.lang.String locationCode;
    /**批号(没有批号时存空字符串, 不存 null)*/
    @Excel(name = "批号", width = 15)
    @Schema(description = "批号")
    private java.lang.String batchNumber;
    /**保质期到期日*/
    @Excel(name = "保质期到期日", width = 15, format = "yyyy-MM-dd")
    @JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern="yyyy-MM-dd")
    @Schema(description = "保质期到期日")
    private java.util.Date expiryDate;
    /**在库数量*/
    @Excel(name = "在库数量", width = 15)
    @Schema(description = "在库数量")
    private java.lang.Integer stockQuantity;
    /**分配数量*/
    @Excel(name = "分配数量", width = 15)
    @Schema(description = "分配数量")
    private java.lang.Integer allocatedQuantity;
    /**可用数量*/
    @Excel(name = "可用数量", width = 15)
    @Schema(description = "可用数量")
    private java.lang.Integer availableQuantity;
    /**是否可售*/
    @Excel(name = "是否可售", width = 15, dicCode = "yn")
    @Dict(dicCode = "yn")
    @Schema(description = "是否可售")
    private java.lang.String isSellable;
    /**储位类型*/
    @TableField(exist = false)
    @Excel(name = "储位类型", width = 15, dicCode = "location_type")
    @Schema(description = "储位类型")
    private java.lang.String locationType;
    /**储区类型*/
    @TableField(exist = false)
    @Excel(name = "储区类型", width = 15, dicCode = "zone_type")
    @Schema(description = "储区类型")
    private java.lang.String zoneType;
    /**入库时间*/
    @Excel(name = "入库时间", width = 20, format = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    @Schema(description = "入库时间")
    private java.util.Date stockInTime;

    // ========== 下面是不导出的字段 ==========
    /**商品id*/
    @Schema(description = "商品id")
    private java.lang.String productId;
    /**容器编码*/
    @Schema(description = "容器编码")
    private java.lang.String containerCode;
    /**货主id*/
    @Schema(description = "货主")
    private java.lang.String ownerId;
    /**仓库id*/
    @Schema(description = "仓库id")
    private java.lang.String warehouseId;
}
