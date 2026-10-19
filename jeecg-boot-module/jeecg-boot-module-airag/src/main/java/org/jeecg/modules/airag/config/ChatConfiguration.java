package org.jeecg.modules.airag.config;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.autoconfigure.openai.OpenAiChatProperties;
import org.springframework.ai.autoconfigure.openai.OpenAiConnectionProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Spring AI 配置（2.1 / 2.4 / 3.5 / 4.3）
 */
@Configuration
public class ChatConfiguration {

    /**
     * 向量维度：必须和下面三处保持一致
     * 1. application-dev.yml  spring.ai.openai.embedding.options.dimensions
     * 2. 建表语句             embedding vector(768)
     * 3. 这里的 dimensions()
     */
    private static final int EMBEDDING_DIMENSIONS = 768;

    /**
     * 动态数据源中 PostgreSQL 数据源的名字，要和 application-dev.yml 里
     * spring.datasource.dynamic.datasource.postgresql 对应
     */
    private static final String PG_DATASOURCE_NAME = "postgresql";

    /**
     * 阿里百炼兼容 OpenAI 协议的聊天模型
     * <p>
     * 本项目的 AlibabaOpenAiChatModel 会保留流式响应中的 tool_calls 增量，
     * 否则工具调用在流式模式下拿不到参数；ChatClient 使用这个模型。
     */
    @Bean
    public AlibabaOpenAiChatModel alibabaOpenAiChatModel(
            OpenAiConnectionProperties commonProperties,
            OpenAiChatProperties chatProperties,
            ObjectProvider<RestClient.Builder> restClientBuilderProvider,
            ObjectProvider<WebClient.Builder> webClientBuilderProvider,
            ToolCallingManager toolCallingManager,
            RetryTemplate retryTemplate,
            ResponseErrorHandler responseErrorHandler,
            ObjectProvider<ObservationRegistry> observationRegistry,
            ObjectProvider<ChatModelObservationConvention> observationConvention) {
        String baseUrl = StringUtils.hasText(chatProperties.getBaseUrl()) ? chatProperties.getBaseUrl() : commonProperties.getBaseUrl();
        String apiKey = StringUtils.hasText(chatProperties.getApiKey()) ? chatProperties.getApiKey() : commonProperties.getApiKey();
        String projectId = StringUtils.hasText(chatProperties.getProjectId()) ? chatProperties.getProjectId() : commonProperties.getProjectId();
        String organizationId = StringUtils.hasText(chatProperties.getOrganizationId()) ? chatProperties.getOrganizationId() : commonProperties.getOrganizationId();
        Map<String, List<String>> connectionHeaders = new HashMap<>();
        if (StringUtils.hasText(projectId)) {
            connectionHeaders.put("OpenAI-Project", List.of(projectId));
        }
        if (StringUtils.hasText(organizationId)) {
            connectionHeaders.put("OpenAI-Organization", List.of(organizationId));
        }
        RestClient.Builder restClientBuilder = restClientBuilderProvider.getIfAvailable(RestClient::builder);
        WebClient.Builder webClientBuilder = webClientBuilderProvider.getIfAvailable(WebClient::builder);
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(new SimpleApiKey(apiKey))
                .headers(CollectionUtils.toMultiValueMap(connectionHeaders))
                .completionsPath(chatProperties.getCompletionsPath())
                .embeddingsPath("/v1/embeddings")
                .restClientBuilder(restClientBuilder)
                .webClientBuilder(webClientBuilder)
                .responseErrorHandler(responseErrorHandler)
                .build();
        AlibabaOpenAiChatModel chatModel = AlibabaOpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(chatProperties.getOptions())
                .toolCallingManager(toolCallingManager)
                .retryTemplate(retryTemplate)
                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
                .build();
        Objects.requireNonNull(chatModel);
        observationConvention.ifAvailable(chatModel::setObservationConvention);
        return chatModel;
    }

    /**
     * 对接 OpenAI 协议的大模型（DeepSeek / 阿里百炼）
     * 带日志 Advisor 和会话记忆 Advisor
     */
    @Bean
    public ChatClient chatClientOpenAi(AlibabaOpenAiChatModel model, ChatMemory chatMemory) {
        return ChatClient
                .builder(model)
                .defaultAdvisors(new SimpleLoggerAdvisor())                 // 添加默认的Advisor，记录日志
                .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory))  // 会话记忆
                .build();
    }

    /**
     * 向量库：PostgreSQL + pgvector
     * <p>
     * 为什么要自己配而不是用自动配置：
     * 项目主数据源是 MySQL，自动配置会拿主数据源去找 vector_store 表，
     * 所以要从动态数据源里取出名为 postgresql 的那个数据源，自己构建 PgVectorStore。
     * （同时要在 yml 里排除 PgVectorStoreAutoConfiguration）
     */
    @Bean
    public VectorStore pgVectorStore(DataSource dataSource,
                                     @Qualifier("openAiEmbeddingModel") OpenAiEmbeddingModel openAiEmbeddingModel) {
        // 从动态数据源中取出 PostgreSQL 数据源，类型不对时给出明确的启动错误
        if (!(dataSource instanceof DynamicRoutingDataSource routingDataSource)) {
            throw new IllegalStateException("pgVectorStore 初始化失败：当前数据源不是 DynamicRoutingDataSource，请检查 spring.datasource.dynamic 配置");
        }
        DataSource pgDataSource = routingDataSource.getDataSource(PG_DATASOURCE_NAME);
        if (pgDataSource == null) {
            throw new IllegalStateException("pgVectorStore 初始化失败：未找到名为 " + PG_DATASOURCE_NAME
                    + " 的数据源，请检查 application-dev.yml 中的 spring.datasource.dynamic.datasource.postgresql 配置");
        }
        // 这个 JdbcTemplate 只给向量库内部使用，不注册成 Bean，避免影响项目里其它按类型注入 JdbcTemplate 的地方
        JdbcTemplate jdbcTemplate = new JdbcTemplate(pgDataSource);

        return PgVectorStore.builder(jdbcTemplate, openAiEmbeddingModel)
                .dimensions(EMBEDDING_DIMENSIONS)                          // 与嵌入模型维度对齐
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE) // 余弦距离
                .schemaName("public")                                       // 表所属的 schema
                .vectorTableName("vector_store")                            // 存储向量的表名
                .indexType(PgVectorStore.PgIndexType.HNSW)                  // 高效近似最近邻搜索
                .initializeSchema(false)                                    // false = 不自动建表，我们手动执行 SQL 建表
                .idType(PgVectorStore.PgIdType.UUID)
                .build();
    }
}
