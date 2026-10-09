package org.jeecg.modules.wms.inventory.excel;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.wms.config.ExcelImportResult;
import org.jeecg.modules.wms.inventory.service.IWmsInventoryService;

import java.util.ArrayList;
import java.util.List;

/**
 * @Description: 库存导入监听器: EasyExcel 每读到一行数据就会调用一次 invoke
 *               注意: 监听器不能交给 spring 管理, 每次导入都要 new 一个, 用到的 service 通过构造方法传进来
 * @Version: V1.0
 */
@Slf4j
public class ImportInventoryListener implements ReadListener<WmsInventoryImport> {

    /** 每攒够 100 条存一次数据库, 然后清空缓存, 防止几万条数据全放在内存里 */
    private static final int BATCH_COUNT = 100;

    /** 缓存的数据 */
    private List<WmsInventoryImport> cachedDataList = new ArrayList<>(BATCH_COUNT);

    private final IWmsInventoryService wmsInventoryService;

    /** 导入结果: 成功、跳过、失败的条数和原因 */
    private final ExcelImportResult result = new ExcelImportResult();

    public ImportInventoryListener(IWmsInventoryService wmsInventoryService) {
        this.wmsInventoryService = wmsInventoryService;
    }

    /**
     * 每解析到一行数据都会调用
     */
    @Override
    public void invoke(WmsInventoryImport data, AnalysisContext context) {
        // 行号: getRowIndex 从 0 开始, 加 1 才是 Excel 里看到的行号
        data.setRowNo(context.readRowHolder().getRowIndex() + 1);
        cachedDataList.add(data);
        if (cachedDataList.size() >= BATCH_COUNT) {
            saveData();
            cachedDataList = new ArrayList<>(BATCH_COUNT);
        }
    }

    /**
     * 某一行解析出错(比如数字列里填了文字)时调用: 记下原因, 不抛异常, 这样会继续读下一行
     */
    @Override
    public void onException(Exception exception, AnalysisContext context) {
        Integer rowNo = context.readRowHolder().getRowIndex() + 1;
        log.warn("库存导入, 第{}行解析失败: {}", rowNo, exception.getMessage());
        result.fail(rowNo, "数据格式不正确(数字列请填数字, 日期请填 2026-01-31 这种格式)");
    }

    /**
     * 所有数据解析完成后调用: 把最后不满 100 条的数据也存进去
     */
    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        saveData();
        log.info("库存导入完成: {}", result.toMessage());
    }

    private void saveData() {
        if (cachedDataList.isEmpty()) {
            return;
        }
        wmsInventoryService.importInventory(cachedDataList, result);
    }

    public ExcelImportResult getResult() {
        return result;
    }
}
