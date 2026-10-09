package org.jeecg.modules.wms.goods.controller;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.system.query.QueryRuleEnum;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.wms.goods.entity.*;
import org.jeecg.modules.wms.goods.service.*;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;

import org.jeecgframework.poi.excel.ExcelImportUtil;
import org.jeecgframework.poi.excel.def.NormalExcelConstants;
import org.jeecgframework.poi.excel.entity.ExportParams;
import org.jeecgframework.poi.excel.entity.ImportParams;
import org.jeecgframework.poi.excel.view.JeecgEntityExcelView;
import org.jeecg.common.system.base.controller.JeecgController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;
import com.alibaba.fastjson.JSON;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.support.ExcelTypeEnum;
import java.net.URLEncoder;
import java.util.ArrayList;
import org.jeecg.modules.wms.goods.excel.ImportGoodsListener;
import org.jeecg.modules.wms.goods.excel.WmsProductsImport;
import org.springframework.beans.BeanUtils;

 /**
 * @Description: 商品信息表
 * @Author: jeecg-boot
 * @Date:   2025-04-14
 * @Version: V1.0
 */
@Tag(name="商品信息表")
@RestController
@RequestMapping("/goods/wmsProducts")
@Slf4j
public class WmsProductsController extends JeecgController<WmsProducts, IWmsProductsService> {
	@Autowired
	private IWmsProductsService wmsProductsService;

	//商品品牌service
	 @Autowired
	 private IWmsProductBrandService wmsProductBrandService;

	//注入货主service
	 @Autowired
	 private IWmsCargoOwnersService wmsCargoOwnersService;

	 //注入商品分类service
	 @Autowired
	 private IWmsProductCategoriesService wmsProductCategoriesService;

	 //注入商品图片service
	 @Autowired
	 private IWmsProductImagesService wmsProductImagesService;

	/**
	 * 分页列表查询
	 *
	 * @param wmsProducts
	 * @param pageNo
	 * @param pageSize
	 * @param req
	 * @return
	 */
	//@AutoLog(value = "商品信息表-分页列表查询")
	@Operation(summary="商品信息表-分页列表查询")
	@GetMapping(value = "/list")
	public Result<IPage<WmsProducts>> queryPageList(WmsProducts wmsProducts,
								   @RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
								   @RequestParam(name="pageSize", defaultValue="10") Integer pageSize,
								   HttpServletRequest req) {
        QueryWrapper<WmsProducts> queryWrapper = QueryGenerator.initQueryWrapper(wmsProducts, req.getParameterMap());
		Page<WmsProducts> page = new Page<WmsProducts>(pageNo, pageSize);
		IPage<WmsProducts> pageList = wmsProductsService.page(page, queryWrapper);
		if(pageList.getTotal()<=0){
			return Result.OK(pageList);
		}
		/**
		 // 查询仓库id
		 List<String> warehousIds = pageList.getRecords().stream().map(item -> item.getWarehouseId()).collect(Collectors.toList());

		 //获取mapper
		 BaseMapper<WmsWarehouses> baseMapper = wmsWarehousesService.getBaseMapper();
		 // 查询仓库信息
		 List<WmsWarehouses> wmsWarehouses = baseMapper.selectBatchIds(warehousIds);
		 // 设置仓库信息
		 pageList.getRecords().stream().forEach(item -> {
		 item.setWarehouse(wmsWarehouses.stream().filter(warehouse -> warehouse.getId().equals(item.getWarehouseId())).findFirst().orElse(null));
		 });
		 */
		//按上边注释的代码实现根据货主id关联查询货主名称
		List<String> ownerIds = pageList.getRecords().stream().map(item -> item.getOwnerId()).collect(Collectors.toList());

		List<WmsCargoOwners> wmsCargoOwners = wmsCargoOwnersService.listByIds(ownerIds);
		pageList.getRecords().stream().forEach(item -> {
			item.setOwnerName(wmsCargoOwners.stream().filter(owner -> owner.getId().equals(item.getOwnerId())).findFirst().map(WmsCargoOwners::getOwnerName).orElse(null));
		});
		//按上边注释的代码实现根据分类id关联查询分类名称
		List<String> categoryIds = pageList.getRecords().stream().map(item -> item.getCategoryId()).collect(Collectors.toList());
		List<WmsProductCategories> wmsProductCategories = wmsProductCategoriesService.listByIds(categoryIds);
		pageList.getRecords().stream().forEach(item -> {
			item.setCategoryName(wmsProductCategories.stream().filter(category -> category.getId().equals(item.getCategoryId())).findFirst().map(WmsProductCategories::getCategoryName).orElse(null));
		});
		//现根据品牌id关联查询品牌名称
		List<String> brandIds = pageList.getRecords().stream().map(item -> item.getProductBrand()).collect(Collectors.toList());
		List<WmsProductBrand> wmsProductBrands = wmsProductBrandService.listByIds(brandIds);
		pageList.getRecords().stream().forEach(item -> {
			item.setProductBrandName(wmsProductBrands.stream().filter(brand -> brand.getId().equals(item.getProductBrand())).findFirst().map(WmsProductBrand::getName).orElse(null));
		});

		return Result.OK(pageList);
	}

	/**
	 *   添加
	 *
	 * @param wmsProducts
	 * @return
	 */
	@AutoLog(value = "商品信息表-添加")
	@Operation(summary="商品信息表-添加")
	@RequiresPermissions("goods:wms_products:add")
	@PostMapping(value = "/add")
	public Result<String> add(@RequestBody WmsProducts wmsProducts) {
		wmsProductsService.add(wmsProducts);
		//图片
		String productImgs = wmsProducts.getProductImgs();

		return Result.OK("添加成功！");
	}

	/**
	 *  编辑
	 *
	 * @param wmsProducts
	 * @return
	 */
	@AutoLog(value = "商品信息表-编辑")
	@Operation(summary="商品信息表-编辑")
	@RequiresPermissions("goods:wms_products:edit")
	@RequestMapping(value = "/edit", method = {RequestMethod.PUT,RequestMethod.POST})
	public Result<String> edit(@RequestBody WmsProducts wmsProducts) {
		wmsProductsService.edit(wmsProducts);
		return Result.OK("编辑成功!");
	}

	/**
	 *   通过id删除
	 *
	 * @param id
	 * @return
	 */
	@AutoLog(value = "商品信息表-通过id删除")
	@Operation(summary="商品信息表-通过id删除")
	@RequiresPermissions("goods:wms_products:delete")
	@DeleteMapping(value = "/delete")
	public Result<String> delete(@RequestParam(name="id",required=true) String id) {
		wmsProductsService.removeById(id);
		return Result.OK("删除成功!");
	}

	/**
	 *  批量删除
	 *
	 * @param ids
	 * @return
	 */
	@AutoLog(value = "商品信息表-批量删除")
	@Operation(summary="商品信息表-批量删除")
	@RequiresPermissions("goods:wms_products:deleteBatch")
	@DeleteMapping(value = "/deleteBatch")
	public Result<String> deleteBatch(@RequestParam(name="ids",required=true) String ids) {
		this.wmsProductsService.removeByIds(Arrays.asList(ids.split(",")));
		return Result.OK("批量删除成功!");
	}

	/**
	 * 通过id查询
	 *
	 * @param id
	 * @return
	 */
	//@AutoLog(value = "商品信息表-通过id查询")
	@Operation(summary="商品信息表-通过id查询")
	@GetMapping(value = "/queryById")
	public Result<WmsProducts> queryById(@RequestParam(name="id",required=true) String id) {
		WmsProducts wmsProducts = wmsProductsService.getById(id);
		//根据商品id查询商品图片，并组成字符串，中间以逗号分隔
		List<WmsProductImages> wmsProductImages = wmsProductImagesService.list(new LambdaQueryWrapper<WmsProductImages>().eq(WmsProductImages::getProductId, id));
		String productImgs = wmsProductImages.stream().map(item -> item.getOriginal()).collect(Collectors.joining(","));
		wmsProducts.setProductImgs(productImgs);
		if(wmsProducts==null) {
			return Result.error("未找到对应数据");
		}
		return Result.OK(wmsProducts);
	}

    /**
    * 导出excel(EasyExcel): 按页面上的查询条件导出, 勾选了行则只导出勾选的行; 最多导出 1000 条
    *
    * @param request
    * @param response
    * @param wmsProducts 查询条件
    */
    @RequiresPermissions("goods:wms_products:exportXls")
    @RequestMapping(value = "/exportXls")
    public void exportXls(HttpServletRequest request, HttpServletResponse response, WmsProducts wmsProducts) throws IOException {
        //1. 查询要导出的商品
        QueryWrapper<WmsProducts> queryWrapper = QueryGenerator.initQueryWrapper(wmsProducts, request.getParameterMap());
        String selections = request.getParameter("selections");
        if (oConvertUtils.isNotEmpty(selections)) {
            queryWrapper.in("id", Arrays.asList(selections.split(",")));
        }
        List<WmsProducts> productList = wmsProductsService.page(new Page<WmsProducts>(1, 1000), queryWrapper).getRecords();

        //2. 商品表里存的是货主id、分类id、品牌id, 导出要的是名称: 各查一次, 转成 map<id, 名称>
        Map<String, String> ownerNameMap = new HashMap<>();
        Map<String, String> categoryNameMap = new HashMap<>();
        Map<String, String> brandNameMap = new HashMap<>();
        List<String> ownerIds = productList.stream().map(WmsProducts::getOwnerId).filter(oConvertUtils::isNotEmpty).distinct().collect(Collectors.toList());
        if (!ownerIds.isEmpty()) {
            wmsCargoOwnersService.listByIds(ownerIds).forEach(o -> ownerNameMap.put(o.getId(), o.getOwnerName()));
        }
        List<String> categoryIds = productList.stream().map(WmsProducts::getCategoryId).filter(oConvertUtils::isNotEmpty).distinct().collect(Collectors.toList());
        if (!categoryIds.isEmpty()) {
            wmsProductCategoriesService.listByIds(categoryIds).forEach(c -> categoryNameMap.put(c.getId(), c.getCategoryName()));
        }
        List<String> brandIds = productList.stream().map(WmsProducts::getProductBrand).filter(oConvertUtils::isNotEmpty).distinct().collect(Collectors.toList());
        if (!brandIds.isEmpty()) {
            wmsProductBrandService.listByIds(brandIds).forEach(b -> brandNameMap.put(b.getId(), b.getName()));
        }

        //3. 转成导出模型: 导出哪些列、列的顺序, 由 WmsProductsImport 里的 @ExcelProperty 注解决定
        List<WmsProductsImport> exportList = new ArrayList<>();
        for (WmsProducts product : productList) {
            WmsProductsImport row = new WmsProductsImport();
            BeanUtils.copyProperties(product, row);
            row.setOwnerName(ownerNameMap.get(product.getOwnerId()));
            row.setCategoryName(categoryNameMap.get(product.getCategoryId()));
            row.setProductBrandName(brandNameMap.get(product.getProductBrand()));
            exportList.add(row);
        }

        //4. 用 EasyExcel 把数据写到响应流里, 浏览器收到后下载
        //   前端页面是按 .xls 保存文件的, 所以这里指定生成 xls 格式
        response.setContentType("application/vnd.ms-excel");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("商品信息表", "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xls");
        EasyExcel.write(response.getOutputStream(), WmsProductsImport.class)
                .excelType(ExcelTypeEnum.XLS)
                .sheet("商品信息")
                .doWrite(exportList);
    }

    /**
    * 通过excel导入数据(EasyExcel)
    * Excel 的列名要和 WmsProductsImport 里 @ExcelProperty 的名称一致; 最简单的办法是先导出一份, 在导出的文件上改
    *
    * @param file 上传的 Excel 文件
    * @return
    */
    @RequiresPermissions("goods:wms_products:importExcel")
    @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
    public Result<?> importExcel(@RequestParam("file") MultipartFile file) throws IOException {
        // 监听器不能交给 spring 管理, 每次导入都 new 一个, 把 service 通过构造方法传进去
        ImportGoodsListener listener = new ImportGoodsListener(wmsProductsService);
        EasyExcel.read(file.getInputStream(), WmsProductsImport.class, listener).sheet().doRead();
        return Result.OK(listener.getResult().toMessage());
    }

}
