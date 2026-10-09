package org.jeecg.modules.wms.inventory.entity;

import java.io.Serializable;
import java.util.Date;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;
import org.jeecgframework.poi.excel.annotation.Excel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @Description: 库存变更记录表
 * @Author: jeecg-boot
 * @Date:   2026-10-06
 * @Version: V1.0
 */
@Data
@TableName("wms_inventory_trans")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description="库存变更记录表")
public class WmsInventoryTrans implements Serializable {
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
    /**商品id*/
    @Excel(name = "商品id", width = 15)
    @Schema(description = "商品id")
    private java.lang.String productId;
    /**储位编码*/
    @Excel(name = "储位编码", width = 15)
    @Schema(description = "储位编码")
    private java.lang.String locationCode;
    /**容器编码*/
    @Excel(name = "容器编码", width = 15)
    @Schema(description = "容器编码")
    private java.lang.String containerCode;
    /**变更数量(增加为正数, 减少为负数)*/
    @Excel(name = "变更数量", width = 15)
    @Schema(description = "变更数量")
    private java.lang.Integer changeQuantity;
    /**变更类型*/
    @Excel(name = "变更类型", width = 15)
    @Schema(description = "变更类型")
    private java.lang.String transactionType;
    /**关联单据号*/
    @Excel(name = "关联单据号", width = 15)
    @Schema(description = "关联单据号")
    private java.lang.String referenceNumber;
    /**备注*/
    @Excel(name = "备注", width = 15)
    @Schema(description = "备注")
    private java.lang.String remarks;
    /**变更时间*/
    @Excel(name = "变更时间", width = 20, format = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    @Schema(description = "变更时间")
    private java.util.Date transactionTime;
    /**批次号*/
    @Excel(name = "批次号", width = 15)
    @Schema(description = "批次号")
    private java.lang.String batchNumber;
}
