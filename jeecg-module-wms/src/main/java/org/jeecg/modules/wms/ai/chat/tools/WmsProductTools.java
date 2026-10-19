package org.jeecg.modules.wms.ai.chat.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.modules.wms.goods.entity.WmsProducts;
import org.jeecg.modules.wms.goods.service.IWmsProductsService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * AI 商品查询工具
 */
@Component
public class WmsProductTools {

    /** 商品查询最多返回条数 */
    private static final int MAX_RESULT_SIZE = 10;

    @Autowired
    private IWmsProductsService wmsProductsService;

    /**
     * 按商品名称或商品编码查询商品详情
     */
    @Tool(description = "根据商品名称或商品编码查询商品详细信息。名称支持模糊匹配，编码精确匹配，至少提供一个")
    public List<WmsProducts> queryProductDetail(
            @ToolParam(description = "商品名称，可选", required = false) String productName,
            @ToolParam(description = "商品编码，可选", required = false) String productCode) {
        if (StringUtils.isBlank(productName) && StringUtils.isBlank(productCode)) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<WmsProducts> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(productName)) {
            queryWrapper.like(WmsProducts::getProductName, productName.trim());
        }
        if (StringUtils.isNotBlank(productCode)) {
            queryWrapper.eq(WmsProducts::getProductCode, productCode.trim());
        }
        return wmsProductsService.page(new Page<>(1, MAX_RESULT_SIZE), queryWrapper).getRecords();
    }
}
