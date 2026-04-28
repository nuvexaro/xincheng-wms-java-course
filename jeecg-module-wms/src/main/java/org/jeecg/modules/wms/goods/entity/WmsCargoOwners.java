package org.jeecg.modules.wms.goods.entity;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import org.jeecg.common.constant.ProvinceCityArea;
import org.jeecg.common.util.SpringContextUtils;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;
import org.jeecgframework.poi.excel.annotation.Excel;
import org.jeecg.common.aspect.annotation.Dict;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @Description: 货主表
 * @Author: jeecg-boot
 * @Date:   2026-04-22
 * @Version: V1.0
 */
@Data
@TableName("wms_cargo_owners")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description="货主表")
public class WmsCargoOwners implements Serializable {
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
	/**货主编码*/
	@Excel(name = "货主编码", width = 15)
    @Schema(description = "货主编码")
    private java.lang.String ownerCode;
	/**货主名称*/
	@Excel(name = "货主名称", width = 15)
    @Schema(description = "货主名称")
    private java.lang.String ownerName;
	/**国家*/
	@Excel(name = "国家", width = 15)
    @Schema(description = "国家")
    private java.lang.String country;
	/**城市*/
	@Excel(name = "城市", width = 15)
    @Schema(description = "城市")
    private java.lang.String city;
	/**地址*/
	@Excel(name = "地址", width = 15)
    @Schema(description = "地址")
    private java.lang.String address;
	/**邮编*/
	@Excel(name = "邮编", width = 15)
    @Schema(description = "邮编")
    private java.lang.String postalCode;
	/**邮箱*/
	@Excel(name = "邮箱", width = 15)
    @Schema(description = "邮箱")
    private java.lang.String email;
	/**电话*/
	@Excel(name = "电话", width = 15)
    @Schema(description = "电话")
    private java.lang.String phone;
	/**法人代表*/
	@Excel(name = "法人代表", width = 15)
    @Schema(description = "法人代表")
    private java.lang.String legalPerson;
	/**联系人*/
	@Excel(name = "联系人", width = 15)
    @Schema(description = "联系人")
    private java.lang.String contactPerson;
	/**联系人电话*/
	@Excel(name = "联系人电话", width = 15)
    @Schema(description = "联系人电话")
    private java.lang.String contactPhone;
	/**许可证号码*/
	@Excel(name = "许可证号码", width = 15)
    @Schema(description = "许可证号码")
    private java.lang.String licenseNumber;
	/**许可证有效期*/
	@Excel(name = "许可证有效期", width = 15, format = "yyyy-MM-dd")
	@JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern="yyyy-MM-dd")
    @Schema(description = "许可证有效期")
    private java.util.Date licenseValidDate;
	/**许可证附件*/
	@Excel(name = "许可证附件", width = 15)
    @Schema(description = "许可证附件")
    private java.lang.String licenseAttachment;
	/**统一社会信用代码*/
	@Excel(name = "统一社会信用代码", width = 15)
    @Schema(description = "统一社会信用代码")
    private java.lang.String usci;
	/**营业执照附件路径*/
	@Excel(name = "营业执照附件路径", width = 15)
    @Schema(description = "营业执照附件路径")
    private java.lang.String businessLicenseAttachment;
	/**结算币种*/
	@Excel(name = "结算币种", width = 15, dicCode = "settlement_currency")
	@Dict(dicCode = "settlement_currency")
    @Schema(description = "结算币种")
    private java.lang.String settlementCurrency;
	/**状态*/
	@Excel(name = "状态", width = 15, dicCode = "dict_item_status")
	@Dict(dicCode = "dict_item_status")
    @Schema(description = "状态")
    private java.lang.String status;
	/**备注*/
	@Excel(name = "备注", width = 15)
    @Schema(description = "备注")
    private java.lang.String remarks;
}
