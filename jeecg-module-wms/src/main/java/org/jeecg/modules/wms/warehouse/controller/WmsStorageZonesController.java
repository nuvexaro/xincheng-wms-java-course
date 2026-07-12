package org.jeecg.modules.wms.warehouse.controller;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.system.query.QueryRuleEnum;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.wms.warehouse.entity.WmsStorageZones;
import org.jeecg.modules.wms.warehouse.entity.WmsWarehouses;
import org.jeecg.modules.wms.warehouse.service.IWmsStorageZonesService;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;

import org.jeecg.modules.wms.warehouse.service.IWmsWarehousesService;
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
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.apache.shiro.authz.annotation.RequiresPermissions;

 /**
 * @Description: 储区表
 * @Author: jeecg-boot
 * @Date:   2026-07-08
 * @Version: V1.0
 */
@Tag(name="储区表")
@RestController
@RequestMapping("/warehouse/wmsStorageZones")
@Slf4j
public class WmsStorageZonesController extends JeecgController<WmsStorageZones, IWmsStorageZonesService> {
	@Autowired
	private IWmsStorageZonesService wmsStorageZonesService;
	@Autowired
	private IWmsWarehousesService wmsWarehousesService;
	
	/**
	 * 分页列表查询
	 *
	 * @param wmsStorageZones
	 * @param pageNo
	 * @param pageSize
	 * @param req
	 * @return
	 */
	//@AutoLog(value = "储区表-分页列表查询")
	@Operation(summary="储区表-分页列表查询")
	@GetMapping(value = "/list")
	public Result<IPage<WmsStorageZones>> queryPageList(WmsStorageZones wmsStorageZones,
								   @RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
								   @RequestParam(name="pageSize", defaultValue="10") Integer pageSize,
								   HttpServletRequest req) {
		IPage<WmsStorageZones> wmsStorageZonesIPage = wmsStorageZonesService.queryList(wmsStorageZones, pageNo, pageSize);
		return Result.OK(wmsStorageZonesIPage);
//        QueryWrapper<WmsStorageZones> queryWrapper = QueryGenerator.initQueryWrapper(wmsStorageZones, req.getParameterMap());
//		Page<WmsStorageZones> page = new Page<WmsStorageZones>(pageNo, pageSize);
//		IPage<WmsStorageZones> pageList = wmsStorageZonesService.page(page, queryWrapper);
//		if(pageList.getRecords().size()<=0){
//			return Result.OK(pageList);
//		}
//		//查询仓库id
//		List<String> warehouseIds = pageList.getRecords().stream().map(item -> item.getWarehouseId()).toList();
//
//		//获取mapper
//		BaseMapper<WmsWarehouses> baseMapper = wmsWarehousesService.getBaseMapper();
//
//		//查询仓库信息
//		List<WmsWarehouses> wmsWarehouses = baseMapper.selectByIds(warehouseIds);
//
//		//设置仓库信息
//		pageList.getRecords().stream().forEach(item -> {
//			WmsWarehouses warehousesTemp = wmsWarehouses.stream().filter(warearehouse -> warearehouse.getId().equals(item.getWarehouseId())).findFirst().orElse(new WmsWarehouses());
//			item.setWarehouseName(warehousesTemp.getWarehouseName());
//		});
//
//		return Result.OK(pageList);

	}
	
	/**
	 *   添加
	 *
	 * @param wmsStorageZones
	 * @return
	 */
	@AutoLog(value = "储区表-添加")
	@Operation(summary="储区表-添加")
	@RequiresPermissions("warehouse:wms_storage_zones:add")
	@PostMapping(value = "/add")
	public Result<String> add(@RequestBody WmsStorageZones wmsStorageZones) {
		wmsStorageZonesService.save(wmsStorageZones);
		return Result.OK("添加成功！");
	}
	
	/**
	 *  编辑
	 *
	 * @param wmsStorageZones
	 * @return
	 */
	@AutoLog(value = "储区表-编辑")
	@Operation(summary="储区表-编辑")
	@RequiresPermissions("warehouse:wms_storage_zones:edit")
	@RequestMapping(value = "/edit", method = {RequestMethod.PUT,RequestMethod.POST})
	public Result<String> edit(@RequestBody WmsStorageZones wmsStorageZones) {
		wmsStorageZonesService.updateById(wmsStorageZones);
		return Result.OK("编辑成功!");
	}
	
	/**
	 *   通过id删除
	 *
	 * @param id
	 * @return
	 */
	@AutoLog(value = "储区表-通过id删除")
	@Operation(summary="储区表-通过id删除")
	@RequiresPermissions("warehouse:wms_storage_zones:delete")
	@DeleteMapping(value = "/delete")
	public Result<String> delete(@RequestParam(name="id",required=true) String id) {
		wmsStorageZonesService.removeById(id);
		return Result.OK("删除成功!");
	}
	
	/**
	 *  批量删除
	 *
	 * @param ids
	 * @return
	 */
	@AutoLog(value = "储区表-批量删除")
	@Operation(summary="储区表-批量删除")
	@RequiresPermissions("warehouse:wms_storage_zones:deleteBatch")
	@DeleteMapping(value = "/deleteBatch")
	public Result<String> deleteBatch(@RequestParam(name="ids",required=true) String ids) {
		this.wmsStorageZonesService.removeByIds(Arrays.asList(ids.split(",")));
		return Result.OK("批量删除成功!");
	}
	
	/**
	 * 通过id查询
	 *
	 * @param id
	 * @return
	 */
	//@AutoLog(value = "储区表-通过id查询")
	@Operation(summary="储区表-通过id查询")
	@GetMapping(value = "/queryById")
	public Result<WmsStorageZones> queryById(@RequestParam(name="id",required=true) String id) {
		WmsStorageZones wmsStorageZones = wmsStorageZonesService.getById(id);
		if(wmsStorageZones==null) {
			return Result.error("未找到对应数据");
		}
		return Result.OK(wmsStorageZones);
	}

    /**
    * 导出excel
    *
    * @param request
    * @param wmsStorageZones
    */
    @RequiresPermissions("warehouse:wms_storage_zones:exportXls")
    @RequestMapping(value = "/exportXls")
    public ModelAndView exportXls(HttpServletRequest request, WmsStorageZones wmsStorageZones) {
        return super.exportXls(request, wmsStorageZones, WmsStorageZones.class, "储区表");
    }

    /**
      * 通过excel导入数据
    *
    * @param request
    * @param response
    * @return
    */
    @RequiresPermissions("warehouse:wms_storage_zones:importExcel")
    @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
    public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
        return super.importExcel(request, response, WmsStorageZones.class);
    }

	 /**
	  *  启用
	  *
	  * @param id
	  * @return
	  */
	 @AutoLog(value = "库区表-启用")
	 @Operation(summary = "库区表-启用")
	 @RequiresPermissions("warehouse:wms_storage_zones:edit")
	 @RequestMapping(value = "/enable", method = {RequestMethod.PUT,RequestMethod.POST})
	 public Result<String> enable(@RequestParam (name="id",required=true) String id){
		 wmsStorageZonesService.enable(id);
		 return Result.OK("启用成功！");
	 }

	 /**
	  *  禁用
	  *
	  * @param id
	  * @return
	  */
	 @AutoLog(value = "库区表-禁用")
	 @Operation(summary="库区表-禁用")
	 @RequiresPermissions("warehouse:wms_storage_zones:edit")
	 @RequestMapping(value = "/disable",method = {RequestMethod.PUT,RequestMethod.POST})
	 public Result<String> disable(@RequestParam(name="id",required=true) String id) {
		 wmsStorageZonesService.disable(id);
		 return Result.OK("禁用成功!");
	 }

}
