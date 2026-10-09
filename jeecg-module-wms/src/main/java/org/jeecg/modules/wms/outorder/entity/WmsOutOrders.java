package org.jeecg.modules.wms.outorder.entity;

import java.io.Serializable;
import java.util.Date;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.jeecg.common.aspect.annotation.Dict;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * @Description: 出库单主表
 * @Version: V1.0
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@TableName("wms_out_orders")
@Schema(description="出库单主表")
public class WmsOutOrders implements Serializable {
    private static final long serialVersionUID = 1L;

    /**主键*/
    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;
    /**创建人*/
    @Schema(description = "创建人")
    private String createBy;
    /**创建日期*/
    @JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建日期")
    private Date createTime;
    /**更新人*/
    @Schema(description = "更新人")
    private String updateBy;
    /**更新日期*/
    @JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新日期")
    private Date updateTime;
    /**所属部门*/
    @Schema(description = "所属部门")
    private String sysOrgCode;
    /**出库单号*/
    @Schema(description = "出库单号")
    private String orderNo;
    /**出库类型*/
    @Dict(dicCode = "out_order_type")
    @Schema(description = "出库类型")
    private String orderType;
    /**订单来源*/
    @Schema(description = "订单来源")
    private String orderSource;
    /**来源单号*/
    @Schema(description = "来源单号")
    private String orderSourceNo;
    /**仓库id*/
    @Schema(description = "仓库id")
    private String warehouseId;
    /**货主id*/
    @Schema(description = "货主id")
    private String ownerId;
    /**客户id*/
    @Schema(description = "客户id")
    private String customerId;
    /**预计发货时间*/
    @JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    @Schema(description = "预计发货时间")
    private Date expectedShipTime;
    /**实际发货时间*/
    @JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    @Schema(description = "实际发货时间")
    private Date actualShipTime;
    /**总商品数量*/
    @Schema(description = "总商品数量")
    private Integer totalQuantity;
    /**总sku种类数*/
    @Schema(description = "总sku种类数")
    private Integer totalSku;
    /**总重量*/
    @Schema(description = "总重量")
    private Integer totalWeight;
    /**总体积*/
    @Schema(description = "总体积")
    private Integer totalVolume;
    /**承运商编码*/
    @Schema(description = "承运商编码")
    private String carrierCode;
    /**收货人*/
    @Schema(description = "收货人")
    private String consignee;
    /**收货时间*/
    @JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    @Schema(description = "收货时间")
    private Date shippingTime;
    /**收货地址*/
    @Schema(description = "收货地址")
    private String shippingAddress;
    /**联系方式*/
    @Schema(description = "联系方式")
    private String contact;
    /**状态*/
    @Dict(dicCode = "out_order_status")
    @Schema(description = "状态")
    private String status;
    /**备注*/
    @Schema(description = "备注")
    private String remark;
    /**波次id*/
    @Schema(description = "波次id")
    private String waveId;
    /**是否创建运单*/
    @Schema(description = "是否创建运单")
    private String createdWaybill;
    /**包裹策略*/
    @Schema(description = "包裹策略")
    private String shipmentStrategy;
    /**收件人省*/
    @Schema(description = "收件人省")
    private String shippingProvince;
    /**收件人市*/
    @Schema(description = "收件人市")
    private String shippingCity;
    /**收件人县区*/
    @Schema(description = "收件人县区")
    private String shippingCounty;
    /**省市区编码*/
    @Schema(description = "省市区编码")
    private String region;
    /**货主名称(非数据库字段, 列表显示用)*/
    @TableField(exist = false)
    @Schema(description = "货主名称(非数据库字段, 列表显示用)")
    private String ownerName;
    /**波次号(非数据库字段, 列表显示用)*/
    @TableField(exist = false)
    @Schema(description = "波次号(非数据库字段, 列表显示用)")
    private String waveNo;
}
