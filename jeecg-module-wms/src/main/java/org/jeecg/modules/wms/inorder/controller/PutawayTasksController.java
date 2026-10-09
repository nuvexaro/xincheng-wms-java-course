package org.jeecg.modules.wms.inorder.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.wms.config.WarehouseDictEnum;
import org.jeecg.modules.wms.inorder.service.IPutawayTasksService;
import org.jeecg.modules.wms.wmstask.entity.WmsTasks;
import org.jeecg.modules.wms.wmstask.entity.WmsTasksRecords;
import org.jeecg.modules.wms.wmstask.service.IWmsTasksRecordsService;
import org.jeecg.modules.wms.wmstask.service.IWmsTasksService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
* @Description: 上架任务接口类
* @Author: jeecg-boot
* @Date:   2026-10-06
* @Version: V1.0
*/
@Tag(name="上架任务")
@RestController
@RequestMapping("/inorder/putawayTasks")
@Slf4j
public class PutawayTasksController  {

    /** 管理员账号: 管理员可以查看所有人的上架任务 */
    private static final String ADMIN_USERNAME = "admin";

    @Autowired
    private IWmsTasksService wmsTasksService;

    @Autowired
    private IWmsTasksRecordsService wmsTasksRecordsService;

    @Autowired
    private IPutawayTasksService putawayTasksService;

    /**
     * 待上架任务查询
     * 只查询任务类型为"上架任务"的任务; 工人只允许查询指派给自己的任务, 管理员(admin)可以查看全部
     *
     * @param wmsTasks 查询条件: 任务号、任务状态
     * @param pageNo
     * @param pageSize
     * @return
     */
    @Operation(summary="待上架任务查询")
    @GetMapping(value = "/list")
    public Result<IPage<WmsTasks>> queryPageList(WmsTasks wmsTasks,
                                   @RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
                                   @RequestParam(name="pageSize", defaultValue="10") Integer pageSize) {
        //任务类型固定为上架任务, 不依赖前端传参
        wmsTasks.setTaskType(WarehouseDictEnum.TASK_TYPE_PUTAWAY.getCode());
        //工人只允许查询指派给自己的任务(任务的执行人存的是用户id)
        LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (loginUser != null && !ADMIN_USERNAME.equals(loginUser.getUsername())) {
            wmsTasks.setOperator(loginUser.getId());
        }
        Page<WmsTasks> page = new Page<WmsTasks>(pageNo, pageSize);
        IPage<WmsTasks> pageList = wmsTasksService.pageList(page, wmsTasks);
        return Result.OK(pageList);
    }

    /**
     * 上架记录查询
     *
     * @param wmsTasksRecords 查询条件: 任务号
     * @param pageNo
     * @param pageSize
     * @return
     */
    @Operation(summary="上架记录查询")
    @GetMapping(value = "/records")
    public Result<IPage<WmsTasksRecords>> records(WmsTasksRecords wmsTasksRecords,
                                   @RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
                                   @RequestParam(name="pageSize", defaultValue="10") Integer pageSize) {
        //任务类型固定为上架任务, 只查上架记录
        wmsTasksRecords.setTaskType(WarehouseDictEnum.TASK_TYPE_PUTAWAY.getCode());
        IPage<WmsTasksRecords> pageList = wmsTasksRecordsService.pageList(wmsTasksRecords, pageNo, pageSize);
        return Result.OK(pageList);
    }

    /**
     * 上架
     *
     * @param wmsTasksRecords 上架表单数据。注意: 表单里的 id 是"上架任务"的 id, 不是上架记录的 id
     * @return
     */
    @AutoLog(value = "上架")
    @Operation(summary="上架")
    @PostMapping(value = "/addRecords")
    public Result<String> addRecords(@RequestBody WmsTasksRecords wmsTasksRecords) {
        //前端传过来的 id 是任务id, 放到 taskId 里; 记录自己的 id 置空, 保存时自动生成
        String taskId = wmsTasksRecords.getId();
        wmsTasksRecords.setTaskId(taskId);
        wmsTasksRecords.setId(null);
        putawayTasksService.putaway(wmsTasksRecords);
        return Result.OK("上架成功！");
    }

    /**
     * 通过id查询
     *
     * @param id
     * @return
     */
    @Operation(summary="任务表-通过id查询")
    @GetMapping(value = "/queryById")
    public Result<WmsTasks> queryById(@RequestParam(name="id",required=true) String id) {
        WmsTasks wmsTasks = wmsTasksService.getById(id);
        if(wmsTasks==null) {
            return Result.error("未找到对应数据");
        }
        return Result.OK(wmsTasks);
    }

}
