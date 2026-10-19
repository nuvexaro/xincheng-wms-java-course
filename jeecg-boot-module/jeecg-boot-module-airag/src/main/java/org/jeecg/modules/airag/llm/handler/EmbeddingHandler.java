package org.jeecg.modules.airag.llm.handler;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.router.DefaultQueryRouter;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.tika.parser.AutoDetectParser;
import org.jeecg.ai.factory.AiModelFactory;
import org.jeecg.ai.factory.AiModelOptions;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.*;
import org.jeecg.modules.airag.common.handler.IEmbeddingHandler;
import org.jeecg.modules.airag.common.vo.knowledge.KnowledgeSearchResult;
import org.jeecg.modules.airag.llm.config.EmbedStoreConfigBean;
import org.jeecg.modules.airag.llm.config.KnowConfigBean;
import org.jeecg.modules.airag.llm.consts.LLMConsts;
import org.jeecg.modules.airag.llm.document.TikaDocumentParser;
import org.jeecg.modules.airag.llm.entity.AiragKnowledge;
import org.jeecg.modules.airag.llm.entity.AiragKnowledgeDoc;
import org.jeecg.modules.airag.llm.entity.AiragModel;
import org.jeecg.modules.airag.llm.service.IAiragKnowledgeService;
import org.jeecg.modules.airag.llm.service.IAiragModelService;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.jeecg.modules.airag.llm.consts.LLMConsts.KNOWLEDGE_DOC_TYPE_FILE;
import static org.jeecg.modules.airag.llm.consts.LLMConsts.KNOWLEDGE_DOC_TYPE_WEB;

/**
 * 向量工具类
 *
 * @Author: chenrui
 * @Date: 2025/2/18 14:31
 */
@Slf4j
@Component
public class EmbeddingHandler implements IEmbeddingHandler {

    @Autowired
    EmbedStoreConfigBean embedStoreConfigBean;

    @Autowired
    @Lazy
    private IAiragModelService airagModelService;

    @Autowired
    @Lazy
    private IAiragKnowledgeService airagKnowledgeService;

    @Value(value = "${jeecg.path.upload:}")
    private String uploadpath;

    @Autowired
    KnowConfigBean knowConfigBean;

    /**
     * Spring AI 向量库（PostgreSQL + pgvector，由 ChatConfiguration 配置）
     */
    @Resource(name = "pgVectorStore")
    private VectorStore pgVectorStore;

    /**
     * 阿里百炼向量模型单次请求最多处理 10 段文本，所以分批写入
     */
    private static final int EMBEDDING_BATCH_SIZE = 10;

    /**
     * 向量过滤条件里只允许安全字符。
     * Spring AI M6 的 PgVector 过滤条件转换器会把字符串值直接拼进 SQL 的 jsonpath，
     * 不做转义；知识库/文档 id 必须先校验，避免单引号等内容破坏 SQL。
     */
    private static final Pattern SAFE_EMBED_ID_PATTERN = Pattern.compile("^[0-9A-Za-z_-]{1,64}$");

    /**
     * 向量存储元数据:knowledgeId
     */
    public static final String EMBED_STORE_METADATA_KNOWLEDGEID = "knowledgeId";

    /**
     * 向量存储元数据:docId
     */
    public static final String EMBED_STORE_METADATA_DOCID = "docId";

    /**
     * 向量存储元数据:docName
     */
    public static final String EMBED_STORE_METADATA_DOCNAME = "docName";

    /**
     * 向量存储缓存
     */
    private static final ConcurrentHashMap<String, EmbeddingStore<TextSegment>> EMBED_STORE_CACHE = new ConcurrentHashMap<>();

    /**
     * 判断 id 是否可以直接用于 PgVector 过滤条件
     */
    public static boolean isValidEmbedId(String id) {
        return oConvertUtils.isNotEmpty(id) && SAFE_EMBED_ID_PATTERN.matcher(id).matches();
    }

    /**
     * 校验 id，避免用户可控的 id 被拼进 PgVector 的 jsonpath/SQL 里
     */
    private static String checkEmbedId(String label, String id) {
        AssertUtils.assertNotEmpty(label, id);
        if (!SAFE_EMBED_ID_PATTERN.matcher(id).matches()) {
            throw new JeecgBootException(label + "格式不正确");
        }
        return id;
    }

    /**
     * 向量化文档
     *
     * @param knowId
     * @param doc
     * @return
     * @author chenrui
     * @date 2025/2/18 11:52
     */
    public Map<String, Object> embeddingDocument(String knowId, AiragKnowledgeDoc doc) {
        checkEmbedId("知识库id", knowId);
        AssertUtils.assertNotEmpty("文档不能为空", doc);
        AiragKnowledge airagKnowledge = airagKnowledgeService.getById(knowId);
        AssertUtils.assertNotEmpty("知识库不存在", airagKnowledge);
        // 读取文档
        String content = doc.getContent();
        // 向量化并存储
        if (oConvertUtils.isEmpty(content) && oConvertUtils.isNotEmpty(doc.getType())) {
            switch (doc.getType()) {
                case KNOWLEDGE_DOC_TYPE_FILE:
                    //解析文件
                    if (knowConfigBean.isEnableMinerU()) {
                        parseFileByMinerU(doc);
                    }
                    content = parseFile(doc);
                    break;
                case KNOWLEDGE_DOC_TYPE_WEB:
                    // TODO author: chenrui for:读取网站内容 date:2025/2/18
                    break;
            }
        }
        // 内容为空时直接失败，避免标题拼接后变成 "标题\n\nnull" 这种无效内容
        if (oConvertUtils.isEmpty(content)) {
            throw new JeecgBootException("文档内容为空，无法向量化");
        }
        //update-begin---author:chenrui ---date:20250307  for：[QQYUN-11443]【AI】是不是应该把标题也生成到向量库里，标题一般是有意义的------------
        if (oConvertUtils.isNotEmpty(doc.getTitle())) {
            content = doc.getTitle() + "\n\n" + content;
        }
        //update-end---author:chenrui ---date:20250307  for：[QQYUN-11443]【AI】是不是应该把标题也生成到向量库里，标题一般是有意义的------------

        String docId = checkEmbedId("文档id", doc.getId());
        checkEmbedId("知识库id", doc.getKnowledgeId());

        // 文档名做非空兜底，Spring AI 的 Document 不允许 metadata 中出现 null 值
        String docName = FilenameUtils.getName(doc.getTitle());
        if (oConvertUtils.isEmpty(docName)) {
            docName = oConvertUtils.getString(doc.getTitle(), docId);
        }

        // 元信息：检索时靠 knowledgeId 过滤，靠 docName 展示
        Map<String, Object> metadataMap = new HashMap<>();
        metadataMap.put(EMBED_STORE_METADATA_DOCID, docId);
        metadataMap.put(EMBED_STORE_METADATA_KNOWLEDGEID, doc.getKnowledgeId());
        metadataMap.put(EMBED_STORE_METADATA_DOCNAME, docName);

        // 分段：TokenTextSplitter 默认每块约 800 token
        org.springframework.ai.document.Document springDocument =
                new org.springframework.ai.document.Document(content, metadataMap);
        TokenTextSplitter textSplitter = new TokenTextSplitter();
        List<org.springframework.ai.document.Document> documents = textSplitter.apply(List.of(springDocument));
        if (oConvertUtils.isObjectEmpty(documents)) {
            throw new JeecgBootException("文档内容过短，无法分段向量化");
        }

        // 在每个分段的 metadata 中添加序号 index（从 0 开始），文档名后面加上 (序号)
        for (int i = 0; i < documents.size(); i++) {
            Map<String, Object> metadata = documents.get(i).getMetadata();
            metadata.put("index", i);
            metadata.put(EMBED_STORE_METADATA_DOCNAME, docName + "(" + i + ")");
        }

        // 分段成功后再删除旧向量，避免分段失败时把旧向量弄丢
        // 重新向量化前，先按 docId 删除这篇文档的旧向量，避免同一篇文档存两份
        Filter.Expression docFilter = new FilterExpressionBuilder()
                .eq(EMBED_STORE_METADATA_DOCID, docId)
                .build();
        pgVectorStore.delete(docFilter);

        // 向量化并存入向量库：add 内部会调用向量模型，每批最多 10 段
        try {
            for (int i = 0; i < documents.size(); i += EMBEDDING_BATCH_SIZE) {
                pgVectorStore.add(documents.subList(i, Math.min(i + EMBEDDING_BATCH_SIZE, documents.size())));
            }
        } catch (Exception e) {
            // 写入中途失败时清理已经写入的半成品向量，避免留下不完整数据
            try {
                pgVectorStore.delete(docFilter);
            } catch (Exception cleanException) {
                log.error("清理文档半成品向量失败, docId: {}", docId, cleanException);
            }
            throw new JeecgBootException("文档向量化失败：" + e.getMessage(), e);
        }
        return metadataMap;
    }

    /**
     * 向量查询(多知识库)
     *
     * @param knowIds
     * @param queryText
     * @param topNumber
     * @param similarity
     * @return
     * @author chenrui
     * @date 2025/2/18 16:52
     */
    public KnowledgeSearchResult embeddingSearch(List<String> knowIds, String queryText, Integer topNumber, Double similarity) {
        AssertUtils.assertNotEmpty("请选择知识库", knowIds);
        AssertUtils.assertNotEmpty("请填写查询内容", queryText);

        // 过滤空 id、非法 id 和重复 id；非法 id 会被 PgVector 过滤条件直接拼进 SQL
        List<String> validKnowIds = knowIds.stream()
                .filter(EmbeddingHandler::isValidEmbedId)
                .distinct()
                .collect(Collectors.toList());
        AssertUtils.assertNotEmpty("请选择知识库", validKnowIds);

        // topK 与单知识库查询保持一致：1~50
        int finalTopNumber = Math.max(1, Math.min(oConvertUtils.getInteger(topNumber, 5), 50));

        //命中的文档列表
        List<Map<String, Object>> documents = new ArrayList<>(16);
        for (String knowId : validKnowIds) {
            List<Map<String, Object>> searchResp = searchEmbedding(knowId, queryText, finalTopNumber, similarity);
            if (oConvertUtils.isObjectNotEmpty(searchResp)) {
                documents.addAll(searchResp);
            }
        }

        //命中的文档内容
        StringBuilder data = new StringBuilder();
        // 对documents按score降序排序并取前topNumber个
        List<Map<String, Object>> sortedDocuments = documents.stream()
                .sorted(Comparator.comparingDouble((Map<String, Object> doc) -> (Double) doc.get("score")).reversed())
                .limit(finalTopNumber)
                .peek(doc -> data.append(doc.get("content")).append("\n"))
                .collect(Collectors.toList());

        return new KnowledgeSearchResult(data.toString(), sortedDocuments);
    }

    /**
     * 向量查询
     *
     * @param knowId
     * @param queryText
     * @param topNumber
     * @param similarity
     * @return
     * @author chenrui
     * @date 2025/2/18 16:52
     */
    public List<Map<String, Object>> searchEmbedding(String knowId, String queryText, Integer topNumber, Double similarity) {
        knowId = checkEmbedId("选择知识库", knowId);
        AiragKnowledge knowledge = airagKnowledgeService.getById(knowId);
        AssertUtils.assertNotEmpty("知识库不存在", knowledge);
        AssertUtils.assertNotEmpty("请填写查询内容", queryText);

        // 参数兜底：topK 限制 1~50，相似度限制 [0,1]
        int finalTopNumber = oConvertUtils.getInteger(topNumber, 5);
        finalTopNumber = Math.max(1, Math.min(finalTopNumber, 50));
        double finalSimilarity = oConvertUtils.getDou(similarity, 0.65);
        // NaN 会绕过 Math.max/Math.min，直接回退到默认值
        if (!Double.isFinite(finalSimilarity)) {
            finalSimilarity = 0.65;
        }
        finalSimilarity = Math.max(0, Math.min(finalSimilarity, 1));

        // 只在当前知识库里检索
        Filter.Expression expression = new FilterExpressionBuilder()
                .eq(EMBED_STORE_METADATA_KNOWLEDGEID, knowId)
                .build();
        SearchRequest request = SearchRequest.builder()
                .query(queryText)
                .filterExpression(expression)
                .topK(finalTopNumber)
                .similarityThreshold(finalSimilarity)
                .build();
        List<org.springframework.ai.document.Document> documents = pgVectorStore.similaritySearch(request);

        List<Map<String, Object>> result = new ArrayList<>();
        if (oConvertUtils.isObjectNotEmpty(documents)) {
            result = documents.stream().map(matchRes -> {
                Map<String, Object> data = new HashMap<>();
                data.put("score", matchRes.getScore());
                data.put("content", matchRes.getText());
                Map<String, Object> metadata = matchRes.getMetadata();
                data.put("chunk", metadata.get("index"));
                data.put(EMBED_STORE_METADATA_DOCNAME, metadata.get(EMBED_STORE_METADATA_DOCNAME));
                return data;
            }).collect(Collectors.toList());
        }
        return result;
    }

    /**
     * 获取向量查询路由
     *
     * @param knowIds
     * @param topNumber
     * @param similarity
     * @return
     * @author chenrui
     * @date 2025/2/20 21:03
     */
    public QueryRouter getQueryRouter(List<String> knowIds, Integer topNumber, Double similarity) {
        AssertUtils.assertNotEmpty("请选择知识库", knowIds);
        List<ContentRetriever> retrievers = Lists.newArrayList();
        for (String knowId : knowIds) {
            if (oConvertUtils.isEmpty(knowId)) {
                continue;
            }
            AiragKnowledge knowledge = airagKnowledgeService.getById(knowId);
            AssertUtils.assertNotEmpty("知识库不存在", knowledge);
            AiragModel model = getEmbedModelData(knowledge.getEmbedId());
            AiModelOptions modelOptions = buildModelOptions(model);
            EmbeddingModel embeddingModel = AiModelFactory.createEmbeddingModel(modelOptions);

            EmbeddingStore<TextSegment> embeddingStore = getEmbedStore(model);
            topNumber = oConvertUtils.getInteger(topNumber, 5);
            similarity = oConvertUtils.getDou(similarity, 0.75);
            // 构建一个嵌入存储内容检索器，用于从嵌入存储中检索内容
            EmbeddingStoreContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(embeddingStore)
                    .embeddingModel(embeddingModel)
                    .maxResults(topNumber)
                    .minScore(similarity)
                    .filter(metadataKey(EMBED_STORE_METADATA_KNOWLEDGEID).isEqualTo(knowId))
                    .build();
            retrievers.add(contentRetriever);
        }
        if (retrievers.isEmpty()) {
            return null;
        } else {
            return new DefaultQueryRouter(retrievers);
        }
    }

    /**
     * 删除知识库下所有向量
     *
     * @param knowId 知识库id
     */
    public void deleteEmbedDocsByKnowId(String knowId) {
        checkEmbedId("选择知识库", knowId);
        Filter.Expression expression = new FilterExpressionBuilder()
                .eq(EMBED_STORE_METADATA_KNOWLEDGEID, knowId)
                .build();
        pgVectorStore.delete(expression);
    }

    /**
     * 批量删除多个知识库下的所有向量（单条 SQL，避免部分删除）
     *
     * @param knowIds 知识库id列表
     */
    public void deleteEmbedDocsByKnowIds(List<String> knowIds) {
        AssertUtils.assertNotEmpty("选择知识库", knowIds);
        knowIds.forEach(knowId -> checkEmbedId("选择知识库", knowId));
        Filter.Expression expression = new FilterExpressionBuilder()
                .in(EMBED_STORE_METADATA_KNOWLEDGEID, new ArrayList<Object>(knowIds))
                .build();
        pgVectorStore.delete(expression);
    }

    /**
     * 删除文档对应的向量
     *
     * @param docIds 文档id列表
     */
    public void deleteEmbedDocsByDocIds(List<String> docIds) {
        AssertUtils.assertNotEmpty("选择文档", docIds);
        docIds.forEach(docId -> checkEmbedId("选择文档", docId));
        Filter.Expression expression = new FilterExpressionBuilder()
                .in(EMBED_STORE_METADATA_DOCID, new ArrayList<Object>(docIds))
                .build();
        pgVectorStore.delete(expression);
    }

    /**
     * 查询向量模型数据
     *
     * @param modelId
     * @return
     * @author chenrui
     * @date 2025/2/20 20:08
     */
    private AiragModel getEmbedModelData(String modelId) {
        AssertUtils.assertNotEmpty("向量模型不能为空", modelId);
        AiragModel model = airagModelService.getById(modelId);
        AssertUtils.assertNotEmpty("向量模型不存在", model);
        AssertUtils.assertEquals("仅支持向量模型", LLMConsts.MODEL_TYPE_EMBED, model.getModelType());
        return model;
    }

    /**
     * 获取向量存储
     *
     * @param model
     * @return
     * @author chenrui
     * @date 2025/2/18 14:56
     */
    private EmbeddingStore<TextSegment> getEmbedStore(AiragModel model) {
        AssertUtils.assertNotEmpty("未配置模型", model);
        String modelId = model.getId();
        String connectionInfo = embedStoreConfigBean.getHost() + embedStoreConfigBean.getPort() + embedStoreConfigBean.getDatabase();
        String key = modelId + connectionInfo;
        if (EMBED_STORE_CACHE.containsKey(key)) {
            return EMBED_STORE_CACHE.get(key);
        }


        AiModelOptions modelOp = buildModelOptions(model);
        EmbeddingModel embeddingModel = AiModelFactory.createEmbeddingModel(modelOp);
        EmbeddingStore<TextSegment> embeddingStore = PgVectorEmbeddingStore.builder()
                // Connection and table parameters
                .host(embedStoreConfigBean.getHost())
                .port(embedStoreConfigBean.getPort())
                .database(embedStoreConfigBean.getDatabase())
                .user(embedStoreConfigBean.getUser())
                .password(embedStoreConfigBean.getPassword())
                .table(embedStoreConfigBean.getTable())
                // Embedding dimension
                // Required: Must match the embedding model’s output dimension
                .dimension(embeddingModel.dimension())
                // Indexing and performance options
                // Enable IVFFlat index
                .useIndex(true)
                // Number of lists
                // for IVFFlat index
                .indexListSize(100)
                // Table creation options
                // Automatically create the table if it doesn’t exist
                .createTable(true)
                //Don’t drop the table first (set to true if you want a fresh start)
                .dropTableFirst(false)
                .build();
        EMBED_STORE_CACHE.put(key, embeddingStore);
        return embeddingStore;
    }

    /**
     * 构造ModelOptions
     *
     * @param model
     * @return
     * @author chenrui
     * @date 2025/3/11 17:45
     */
    public static AiModelOptions buildModelOptions(AiragModel model) {
        AiModelOptions.AiModelOptionsBuilder modelOpBuilder = AiModelOptions.builder()
                .provider(model.getProvider())
                .modelName(model.getModelName())
                .baseUrl(model.getBaseUrl());
        if (oConvertUtils.isObjectNotEmpty(model.getCredential())) {
            JSONObject modelCredential = JSONObject.parseObject(model.getCredential());
            modelOpBuilder.apiKey(oConvertUtils.getString(modelCredential.getString("apiKey"), null));
            modelOpBuilder.secretKey(oConvertUtils.getString(modelCredential.getString("secretKey"), null));
        }
        modelOpBuilder.topNumber(5);
        modelOpBuilder.similarity(0.75);
        return modelOpBuilder.build();
    }

    /**
     * 解析文件
     *
     * @param doc
     * @author chenrui
     * @date 2025/3/5 11:31
     */
    private String parseFile(AiragKnowledgeDoc doc) {
        String metadata = doc.getMetadata();
        AssertUtils.assertNotEmpty("请先上传文件", metadata);
        JSONObject metadataJson = JSONObject.parseObject(metadata);
        if (!metadataJson.containsKey(LLMConsts.KNOWLEDGE_DOC_METADATA_FILEPATH)) {
            throw new JeecgBootException("请先上传文件");
        }
        String filePath = metadataJson.getString(LLMConsts.KNOWLEDGE_DOC_METADATA_FILEPATH);
        AssertUtils.assertNotEmpty("请先上传文件", filePath);
        // 网络资源,先下载到临时目录
        filePath = ensureFile(filePath);
        // 提取文档内容
        File docFile = new File(filePath);
        if (docFile.exists()) {
            Document document = new TikaDocumentParser(AutoDetectParser::new, null, null, null).parse(docFile);
            if (null != document) {
                String content = document.text();
                // 判断是否md文档
                String fileType = FilenameUtils.getExtension(docFile.getName());
                if ("md".equalsIgnoreCase(fileType)) {
                    // 如果是md文件，查找所有图片语法，如果是本地图片，替换成网络图片
                    String baseUrl = doc.getBaseUrl() + "/sys/common/static/";
                    String sourcePath = metadataJson.getString(LLMConsts.KNOWLEDGE_DOC_METADATA_SOURCES_PATH);
                    if(oConvertUtils.isNotEmpty(sourcePath)) {
                        String escapedPath = uploadpath;
                        if (File.separator.equals("\\")){
                            escapedPath = uploadpath.replace("//", "\\\\");
                        }
                        sourcePath = sourcePath.replaceFirst("^" + escapedPath, "").replace("\\", "/");
                        baseUrl = baseUrl + sourcePath + "/";
                        StringBuffer sb = replaceImageUrl(content, baseUrl);
                        content = sb.toString();
                    }
                }
                return content;
            }
        }
        return null;
    }

    @NotNull
    private static StringBuffer replaceImageUrl(String content, String baseUrl) {
        // 正则表达式匹配md文件中的图片语法 ![alt text](image url)
        String mdImagePattern = "!\\[(.*?)]\\((.*?)(\\s*=\\d+)?\\)";
        Pattern pattern = Pattern.compile(mdImagePattern);
        Matcher matcher = pattern.matcher(content);

        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String imageUrl = matcher.group(2);
            // 检查是否是本地图片路径
            if (!imageUrl.startsWith("http")) {
                // 替换成网络图片路径
                String networkImageUrl = baseUrl + imageUrl;
                matcher.appendReplacement(sb, "![" + matcher.group(1) + "](" + networkImageUrl + ")");
            } else {
                matcher.appendReplacement(sb, "![" + matcher.group(1) + "](" + imageUrl + ")");
            }
        }
        matcher.appendTail(sb);
        return sb;
    }

    /**
     * 通过MinerU解析文件
     *
     * @param doc
     * @author chenrui
     * @date 2025/4/1 17:37
     */
    private void parseFileByMinerU(AiragKnowledgeDoc doc) {
        String metadata = doc.getMetadata();
        AssertUtils.assertNotEmpty("请先上传文件", metadata);
        JSONObject metadataJson = JSONObject.parseObject(metadata);
        if (!metadataJson.containsKey(LLMConsts.KNOWLEDGE_DOC_METADATA_FILEPATH)) {
            throw new JeecgBootException("请先上传文件");
        }
        String filePath = metadataJson.getString(LLMConsts.KNOWLEDGE_DOC_METADATA_FILEPATH);
        AssertUtils.assertNotEmpty("请先上传文件", filePath);
        filePath = ensureFile(filePath);

        File docFile = new File(filePath);
        String fileType = FilenameUtils.getExtension(filePath);
        if (!docFile.exists()
                || "txt".equalsIgnoreCase(fileType)
                || "md".equalsIgnoreCase(fileType)) {
            return ;
        }

        String command = "magic-pdf";
        if (oConvertUtils.isNotEmpty(knowConfigBean.getCondaEnv())) {
            command = "conda run -n " + knowConfigBean.getCondaEnv() + " " + command;
        }

        String outputPath = docFile.getParentFile().getAbsolutePath();
        String[] args = {
                "-p", docFile.getAbsolutePath(),
                "-o", outputPath,
        };

        try {
            String execLog = CommandExecUtil.execCommand(command, args);
            log.info("执行命令行:" + command + " args:" + Arrays.toString(args) + "\n log::" + execLog);
            // 如果成功,替换文件路径和静态资源路径
            String fileBaseName = FilenameUtils.getBaseName(docFile.getName());
            String newFileDir = outputPath + File.separator + fileBaseName + File.separator + "auto" + File.separator ;
            // 先检查文件是否存在,存在才替换
            File convertedFile = new File(newFileDir + fileBaseName + ".md");
            if (convertedFile.exists()) {
                log.info("文件转换成md成功,替换文件路径和静态资源路径");
                newFileDir = newFileDir.replaceFirst("^" + uploadpath, "");
                metadataJson.put(LLMConsts.KNOWLEDGE_DOC_METADATA_FILEPATH, newFileDir + fileBaseName + ".md");
                metadataJson.put(LLMConsts.KNOWLEDGE_DOC_METADATA_SOURCES_PATH, newFileDir);
                doc.setMetadata(metadataJson.toJSONString());
            }
        } catch (IOException e) {
            log.error("文件转换md失败,使用传统提取方案{}", e.getMessage(), e);
        }
    }

    /**
     * 确保文件存在
     * @param filePath
     * @return
     * @author chenrui
     * @date 2025/4/1 17:36
     */
    @NotNull
    private String ensureFile(String filePath) {
        // 网络资源,先下载到临时目录
        Matcher matcher = LLMConsts.WEB_PATTERN.matcher(filePath);
        if (matcher.matches()) {
            log.info("网络资源,下载到临时目录:" + filePath);
            // 准备文件
            String tempFilePath = uploadpath + File.separator + "tmp" + File.separator + UUIDGenerator.generate() + File.separator;
            String fileName = filePath;
            if (fileName.contains("?")) {
                fileName = fileName.substring(0, fileName.indexOf("?"));
            }
            fileName = FilenameUtils.getName(fileName);
            tempFilePath = tempFilePath + fileName;
            FileDownloadUtils.download2DiskFromNet(filePath, tempFilePath);
            filePath = tempFilePath;
        } else {
            //本地文件
            filePath = uploadpath + File.separator + filePath;
        }
        return filePath;
    }


}
