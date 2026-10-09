package org.jeecg.modules.wms.goods.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.modules.wms.goods.entity.WmsProductImages;
import org.jeecg.modules.wms.goods.entity.WmsProducts;
import org.jeecg.modules.wms.goods.mapper.WmsProductsMapper;
import org.jeecg.modules.wms.goods.service.IWmsCargoOwnersService;
import org.jeecg.modules.wms.goods.service.IWmsProductCategoriesService;
import org.jeecg.modules.wms.goods.service.IWmsProductImagesService;
import org.jeecg.modules.wms.goods.service.IWmsProductsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import org.jeecg.modules.wms.config.ExcelImportResult;
import org.jeecg.modules.wms.goods.excel.WmsProductsImport;
import org.jeecg.modules.wms.goods.entity.WmsCargoOwners;
import org.jeecg.modules.wms.goods.entity.WmsProductBrand;
import org.jeecg.modules.wms.goods.entity.WmsProductCategories;
import org.jeecg.modules.wms.goods.service.IWmsProductBrandService;
import org.jeecg.common.util.oConvertUtils;
import org.springframework.beans.BeanUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @Description: 商品信息表
 * @Author: jeecg-boot
 * @Date:   2025-04-14
 * @Version: V1.0
 */
@Service
public class WmsProductsServiceImpl extends ServiceImpl<WmsProductsMapper, WmsProducts> implements IWmsProductsService {

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private IWmsCargoOwnersService wmsCargoOwnersService;

    @Autowired
    private IWmsProductCategoriesService wmsProductCategoriesService;

    @Autowired
    private IWmsProductImagesService wmsProductImagesService;
    @Autowired
    private IWmsProductBrandService wmsProductBrandService;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void add(WmsProducts wmsProducts) {
        //校验货主加商品编码在商品表的唯一性
        if(checkProductCode(wmsProducts)){
            throw new RuntimeException("货主加商品编码在商品表中已存在");
        }
        //生成商品条码
        wmsProducts.setProductBarcode(generateProductBarcode(wmsProducts));
        //保存商品
        save(wmsProducts);
//        String productImgs= wmsProducts.getProductImgs();
//        if(productImgs!=null){
//            //图片
//            String[] productImgArr = productImgs.split(",");
//            for (String productImg : productImgArr) {
//                WmsProductImages wmsProductImages = new WmsProductImages();
//                wmsProductImages.setProductId(wmsProducts.getId());
//                wmsProductImages.setOriginal(productImg);
//                wmsProductImagesService.save(wmsProductImages);
//            }
//        }
    }

    /**
     * 校验货主加商品编码在商品表的唯一性
     * @param wmsProducts
     * @return 不存在返回false，存在返回true
     */
    public boolean checkProductCode(WmsProducts wmsProducts) {
        String ownerId = wmsProducts.getOwnerId();
        String productCode = wmsProducts.getProductCode();
        //根据货主和商品编码查询
        List<WmsProducts> wmsProductsList = baseMapper.selectList(lambdaQuery().getWrapper()
                .eq(WmsProducts::getOwnerId,ownerId)
                .eq(WmsProducts::getProductCode,productCode));
        return wmsProductsList.size()>0;

    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void edit(WmsProducts wmsProducts) {
        String id = wmsProducts.getId();
        WmsProducts wmsProductsOld = getById(id);
        //如果商品编码有变化
        if(!wmsProductsOld.getProductCode().equals(wmsProducts.getProductCode())){
            //校验商品编码在商品表中的 uniqueness
            if(!checkProductCode(wmsProducts)){
                throw new RuntimeException("商品编码在商品表中已存在");
            }
        }
        //如果商品条码为空则生成商品条码
        if(wmsProducts.getProductBarcode().isEmpty()){
            wmsProducts.setProductBarcode(generateProductBarcode(wmsProducts));
        }
        updateById(wmsProducts);
        //如果商品图片有变化
//        String productImgsOld = wmsProductsOld.getProductImgs()==null?"":wmsProductsOld.getProductImgs();
//        String productImgsNew = wmsProducts.getProductImgs()==null?"":wmsProducts.getProductImgs();
//        if(!productImgsOld.equals(productImgsNew)){
//            LambdaQueryWrapper<WmsProductImages> queryWrapper = new LambdaQueryWrapper<WmsProductImages>().eq(WmsProductImages::getProductId, id);
//            wmsProductImagesService.remove(queryWrapper);
//            if(productImgsNew!=null){
//                //图片
//                String[] productImgArr = productImgsNew.split(",");
//                for (String productImg : productImgArr) {
//                    WmsProductImages wmsProductImages = new WmsProductImages();
//                    wmsProductImages.setProductId(wmsProducts.getId());
//                    wmsProductImages.setOriginal(productImg);
//                    wmsProductImagesService.save(wmsProductImages);
//                }
//            }
//        }
    }


    /**
     * 生成商品条码全局唯一
     * @return
     */
    public String generateProductBarcode(WmsProducts wmsProducts) {
        //编码规则：6位货主编码+4位商品类别+6位序号
        //货主编码
        String ownerCode = wmsCargoOwnersService.getById(wmsProducts.getOwnerId()).getOwnerCode();
        //商品类型编码
        String categoryCode = wmsProductCategoriesService.getById(wmsProducts.getCategoryId()).getCategoryCode();

        String code = null;
        try {
            code = String.format("%06d", redisUtil.incr("WMS_PRO_BARCODE", 1));
        } catch (Exception e) {
            e.printStackTrace();
        }
        code = ownerCode + categoryCode + code;
        return code;
    }

    /**
     * 导入商品
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void importProduct(List<WmsProductsImport> cachedDataList, ExcelImportResult result) {
        if (cachedDataList == null || cachedDataList.isEmpty()) {
            return;
        }
        // 问题: Excel 里是货主名称、品牌名称、分类名称, 数据库表里要的是它们的 id
        // 解决: 先把这一批用到的名称收集起来, 各查一次数据库, 转成 map<名称, id>
        //1. 收集名称
        Set<String> ownerNames = new HashSet<>();
        Set<String> brandNames = new HashSet<>();
        Set<String> categoryNames = new HashSet<>();
        for (WmsProductsImport imp : cachedDataList) {
            if (oConvertUtils.isNotEmpty(imp.getOwnerName())) {
                ownerNames.add(imp.getOwnerName().trim());
            }
            if (oConvertUtils.isNotEmpty(imp.getProductBrandName())) {
                brandNames.add(imp.getProductBrandName().trim());
            }
            if (oConvertUtils.isNotEmpty(imp.getCategoryName())) {
                categoryNames.add(imp.getCategoryName().trim());
            }
        }
        //2. 查询并转成 map: key=名称, value=id (名称重复时取第一条)
        Map<String, String> ownerMap = new HashMap<>();
        if (!ownerNames.isEmpty()) {
            List<WmsCargoOwners> owners = wmsCargoOwnersService.list(new LambdaQueryWrapper<WmsCargoOwners>()
                    .in(WmsCargoOwners::getOwnerName, new ArrayList<>(ownerNames)));
            for (WmsCargoOwners owner : owners) {
                ownerMap.putIfAbsent(owner.getOwnerName(), owner.getId());
            }
        }
        Map<String, String> brandMap = new HashMap<>();
        if (!brandNames.isEmpty()) {
            List<WmsProductBrand> brands = wmsProductBrandService.list(new LambdaQueryWrapper<WmsProductBrand>()
                    .in(WmsProductBrand::getName, new ArrayList<>(brandNames)));
            for (WmsProductBrand brand : brands) {
                brandMap.putIfAbsent(brand.getName(), brand.getId());
            }
        }
        Map<String, String> categoryMap = new HashMap<>();
        if (!categoryNames.isEmpty()) {
            List<WmsProductCategories> categories = wmsProductCategoriesService.list(new LambdaQueryWrapper<WmsProductCategories>()
                    .in(WmsProductCategories::getCategoryName, new ArrayList<>(categoryNames)));
            for (WmsProductCategories category : categories) {
                categoryMap.putIfAbsent(category.getCategoryName(), category.getId());
            }
        }

        //3. 逐条处理: 有问题的行记下原因后跳过, 不影响其它行
        for (WmsProductsImport imp : cachedDataList) {
            //3.1 必填校验
            if (oConvertUtils.isEmpty(imp.getProductName()) || oConvertUtils.isEmpty(imp.getOwnerName())
                    || oConvertUtils.isEmpty(imp.getCategoryName())) {
                result.fail(imp.getRowNo(), "商品名称、货主名称、商品分类不能为空");
                continue;
            }
            //3.2 名称转 id
            String ownerId = ownerMap.get(imp.getOwnerName().trim());
            if (ownerId == null) {
                result.fail(imp.getRowNo(), "货主名称" + imp.getOwnerName() + "不存在");
                continue;
            }
            String categoryId = categoryMap.get(imp.getCategoryName().trim());
            if (categoryId == null) {
                result.fail(imp.getRowNo(), "商品分类" + imp.getCategoryName() + "不存在");
                continue;
            }
            String brandId = null;
            if (oConvertUtils.isNotEmpty(imp.getProductBrandName())) {
                brandId = brandMap.get(imp.getProductBrandName().trim());
                if (brandId == null) {
                    result.fail(imp.getRowNo(), "商品品牌" + imp.getProductBrandName() + "不存在");
                    continue;
                }
            }
            //3.3 转成商品实体(同名属性直接拷贝), 再设置 3 个 id
            WmsProducts wmsProducts = new WmsProducts();
            BeanUtils.copyProperties(imp, wmsProducts);
            wmsProducts.setOwnerId(ownerId);
            wmsProducts.setCategoryId(categoryId);
            wmsProducts.setProductBrand(brandId);

            //3.4 判断商品是否已存在: 同一货主下商品编码唯一; 没填商品编码时按 货主 + 商品名称 判断
            LambdaQueryWrapper<WmsProducts> existQuery = new LambdaQueryWrapper<WmsProducts>()
                    .eq(WmsProducts::getOwnerId, ownerId);
            if (oConvertUtils.isNotEmpty(imp.getProductCode())) {
                existQuery.eq(WmsProducts::getProductCode, imp.getProductCode());
            } else {
                existQuery.eq(WmsProducts::getProductName, imp.getProductName());
            }
            List<WmsProducts> existList = list(existQuery);
            if (existList != null && !existList.isEmpty()) {
                //已存在: 更新。商品条码是系统生成的, 更新时不改它(置为 null 的属性不会参与更新)
                wmsProducts.setId(existList.get(0).getId());
                wmsProducts.setProductBarcode(null);
                updateById(wmsProducts);
            } else {
                //不存在: 新增(add 方法里会生成商品条码)
                wmsProducts.setId(null);
                add(wmsProducts);
            }
            result.success();
        }
    }
}
