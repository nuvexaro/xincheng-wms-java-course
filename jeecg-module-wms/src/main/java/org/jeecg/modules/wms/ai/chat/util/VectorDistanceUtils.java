package org.jeecg.modules.wms.ai.chat.util;

/**
 * 向量距离 / 相似度计算工具（4.1 向量模型测试用）
 * <p>
 * 注意区分两个概念：
 * 1. 相似度：越大越像（余弦相似度范围 [-1, 1]）
 * 2. 距离  ：越小越像（余弦距离 = 1 - 余弦相似度，范围 [0, 2]）
 * <p>
 * 课件里的 cosineDistance 方法实际返回的是“余弦相似度”，名字容易误导，
 * 这里拆成 cosineSimilarity（相似度）和 cosineDistance（距离）两个方法。
 */
public class VectorDistanceUtils {

    // 防止实例化
    private VectorDistanceUtils() {
    }

    // 浮点数计算精度阈值
    private static final double EPSILON = 1e-12;

    /**
     * 欧氏距离：两点之间的直线距离，越小越像
     */
    public static double euclideanDistance(float[] vectorA, float[] vectorB) {
        validateVectors(vectorA, vectorB);

        double sum = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            double diff = vectorA[i] - vectorB[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    /**
     * 余弦相似度：两个向量夹角的余弦值，范围 [-1, 1]，越大越像
     */
    public static double cosineSimilarity(float[] vectorA, float[] vectorB) {
        validateVectors(vectorA, vectorB);

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }

        normA = Math.sqrt(normA);
        normB = Math.sqrt(normB);

        // 处理零向量情况
        if (normA < EPSILON || normB < EPSILON) {
            throw new IllegalArgumentException("Vectors cannot be zero vectors");
        }

        // 处理浮点误差，确保结果在 [-1, 1] 范围内
        double similarity = dotProduct / (normA * normB);
        return Math.max(Math.min(similarity, 1.0), -1.0);
    }

    /**
     * 余弦距离 = 1 - 余弦相似度，范围 [0, 2]，越小越像
     * （pgvector 数据库里算的就是这个，Spring AI 返回的 score = 1 - 距离）
     */
    public static double cosineDistance(float[] vectorA, float[] vectorB) {
        return 1.0 - cosineSimilarity(vectorA, vectorB);
    }

    // 参数校验统一方法
    private static void validateVectors(float[] a, float[] b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("Vectors cannot be null");
        }
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vectors must have same dimension");
        }
        if (a.length == 0) {
            throw new IllegalArgumentException("Vectors cannot be empty");
        }
    }
}
