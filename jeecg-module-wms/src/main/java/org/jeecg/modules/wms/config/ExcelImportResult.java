package org.jeecg.modules.wms.config;

import java.util.ArrayList;
import java.util.List;

/**
 * @description Excel 导入结果: 统计成功、跳过、失败的条数, 并记录每一条失败/跳过的原因
 *              一次导入会分批处理, 这个对象贯穿整个导入过程, 不断累加
 */
public class ExcelImportResult {

    /** 提示信息里最多展示几条原因, 太多了页面显示不下 */
    private static final int MAX_SHOW_REASONS = 5;

    /** 成功条数 */
    private int successCount;
    /** 跳过条数(数据已存在等) */
    private int skipCount;
    /** 失败条数 */
    private int failCount;
    /** 跳过、失败的原因 */
    private final List<String> reasons = new ArrayList<>();

    public void success() {
        successCount++;
    }

    public void skip(Integer rowNo, String reason) {
        skipCount++;
        reasons.add("第" + rowNo + "行: " + reason);
    }

    public void fail(Integer rowNo, String reason) {
        failCount++;
        reasons.add("第" + rowNo + "行: " + reason);
    }

    public int getSuccessCount() {
        return successCount;
    }

    public int getSkipCount() {
        return skipCount;
    }

    public int getFailCount() {
        return failCount;
    }

    /**
     * 生成给用户看的提示信息
     */
    public String toMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("导入完成: 成功").append(successCount).append("条");
        if (skipCount > 0) {
            sb.append(", 跳过").append(skipCount).append("条");
        }
        if (failCount > 0) {
            sb.append(", 失败").append(failCount).append("条");
        }
        if (!reasons.isEmpty()) {
            sb.append("。原因: ");
            int show = Math.min(reasons.size(), MAX_SHOW_REASONS);
            for (int i = 0; i < show; i++) {
                sb.append(i == 0 ? "" : "; ").append(reasons.get(i));
            }
            if (reasons.size() > show) {
                sb.append(" 等共").append(reasons.size()).append("条");
            }
        }
        return sb.toString();
    }
}
