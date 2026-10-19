package org.jeecg.modules.wms.ai.chat.controller;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.AssertUtils;
import org.jeecg.config.shiro.IgnoreAuth;
import org.jeecg.modules.wms.ai.chat.util.VectorDistanceUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 测试接口（仅 dev 环境启用）
 * <p>
 * 4.1 / 4.3：向量模型测试 + 写入向量库
 * 2.3      ：流式调用测试
 * <p>
 * 这些接口只用于学习调试，生产环境通过 @Profile("dev") 直接不注册。
 */
@Slf4j
@RestController
@RequestMapping("/ai")
@Profile("dev")
public class TestController {

    /** 测试文本最大长度，避免一次请求提交过大的内容 */
    private static final int MAX_TEST_TEXT_LENGTH = 2000;

    @Resource(name = "chatClientOpenAi")
    private ChatClient chatClient;

    /** 向量模型：把文本转成向量（application-dev.yml 里 spring.ai.openai.embedding 配置） */
    @Resource
    private OpenAiEmbeddingModel openAiEmbeddingModel;

    /** 向量库：PostgreSQL + pgvector（ChatConfiguration 里配置） */
    @Resource(name = "pgVectorStore")
    private VectorStore pgVectorStore;

    /**
     * 2.3 流式调用测试
     * 浏览器访问：http://localhost:8080/jeecg-boot/ai/chat?prompt=你好你是谁
     * <p>
     * 2.2 同步调用写法：chatClient.prompt().user(prompt).call().content();
     */
    @IgnoreAuth
    @GetMapping(value = "/chat", produces = "text/plain;charset=UTF-8")
    public Flux<String> chat(String prompt) {
        AssertUtils.assertNotEmpty("请输入 prompt", prompt);
        if (prompt.length() > MAX_TEST_TEXT_LENGTH) {
            throw new JeecgBootException("prompt 不能超过" + MAX_TEST_TEXT_LENGTH + "个字符");
        }
        return chatClient
                .prompt().user(prompt)
                .stream()   // 流式调用
                .content();
    }

    /**
     * 4.1 / 4.3 向量模型测试
     * 输入关键字 query，分别和两段文字 t1、t2 比较相似度，并把 t1、t2 写入向量库。
     * <p>
     * 测试：http://localhost:8080/jeecg-boot/ai/embedtest?query=编程&t1=自学java&t2=上证指数
     * 期望：query 和 t1 的相似度明显高于和 t2 的相似度。
     * <p>
     * 提示：这个接口每调用一次就会往 vector_store 表里多写两行（metadata 中 source=embedtest），
     * 测试完可以执行 sql/vector_store.sql 末尾那条 DELETE 清理。
     */
    @IgnoreAuth
    @GetMapping(value = "/embedtest", produces = "text/plain;charset=UTF-8")
    public String embedtest(String query, String t1, String t2) {
        checkTestText("query", query);
        checkTestText("t1", t1);
        checkTestText("t2", t2);
        try {
            return doEmbedTest(query, t1, t2);
        } catch (JeecgBootException e) {
            throw e;
        } catch (Exception e) {
            // 底层向量接口/数据库的异常信息可能包含连接串等细节，只记服务端日志，不回传前端
            log.error("向量模型测试失败", e);
            throw new JeecgBootException("向量模型测试失败，请查看服务端日志");
        }
    }

    /**
     * 执行向量模型测试
     */
    private String doEmbedTest(String query, String t1, String t2) {
        StringBuilder sb = new StringBuilder();
        sb.append("query: ").append(query).append("\n");
        // 先将查询文本向量化
        float[] queryVector = openAiEmbeddingModel.embed(query);
        sb.append("query向量: ").append(arrayToString(queryVector)).append("\n");

        appendCompare(sb, "t1", t1, queryVector);
        appendCompare(sb, "t2", t2, queryVector);

        // 将向量数据写入向量库：Document 是向量库操作的基本单位，写入时会自动调用向量模型生成向量
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "embedtest");
        List<Document> documentList = new ArrayList<>();
        documentList.add(new Document(t1, metadata));
        documentList.add(new Document(t2, metadata));
        pgVectorStore.add(documentList);
        sb.append("==================================\n");
        sb.append("已将 t1、t2 写入向量库 vector_store 表");
        return sb.toString();
    }

    /**
     * 校验测试文本
     */
    private void checkTestText(String name, String text) {
        AssertUtils.assertNotEmpty("请填写 " + name, text);
        if (text.length() > MAX_TEST_TEXT_LENGTH) {
            throw new JeecgBootException(name + " 不能超过" + MAX_TEST_TEXT_LENGTH + "个字符");
        }
    }

    /**
     * 辅助方法：向量化文本 text，并和查询向量比较
     */
    private void appendCompare(StringBuilder sb, String name, String text, float[] queryVector) {
        sb.append("==================================\n");
        sb.append(name).append(": ").append(text).append("\n");
        float[] vector = openAiEmbeddingModel.embed(text);
        sb.append(name).append(" 向量: ").append(arrayToString(vector)).append("\n");
        sb.append("欧氏距离（越小越像）: ")
                .append(VectorDistanceUtils.euclideanDistance(queryVector, vector)).append("\n");
        sb.append("余弦相似度（越大越像）: ")
                .append(VectorDistanceUtils.cosineSimilarity(queryVector, vector)).append("\n");
        sb.append("余弦距离（越小越像）: ")
                .append(VectorDistanceUtils.cosineDistance(queryVector, vector)).append("\n");
    }

    /**
     * 辅助方法：将 float 数组格式化为字符串（只显示前 10 个数，避免输出过长）
     */
    private String arrayToString(float[] array) {
        if (array == null) {
            return "null";
        }
        if (array.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        int limit = Math.min(array.length, 10);
        for (int i = 0; i < limit; i++) {
            sb.append(String.format("%.6f", array[i]));
            if (i < limit - 1) {
                sb.append(", ");
            }
        }
        if (array.length > limit) {
            sb.append(", ... (").append(array.length).append(" dimensions)");
        }
        sb.append("]");
        return sb.toString();
    }
}
