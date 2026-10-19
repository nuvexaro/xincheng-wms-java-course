-- =============================================================
-- 4.2 PostgreSQL + pgvector 初始化脚本
-- 说明：本文件只供手动执行，应用启动不会自动执行；所有语句都是幂等的。
-- 执行方式：在 PostgreSQL 的 postgres 库中执行本文件。
-- =============================================================

-- 扩展
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 向量表：维度必须和 application-dev.yml 的 dimensions、
-- ChatConfiguration 的 EMBEDDING_DIMENSIONS 保持一致（当前为 768）
CREATE TABLE IF NOT EXISTS public.vector_store (
    id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    content text,
    metadata json,
    embedding vector(768)
);

-- HNSW 余弦距离索引
CREATE INDEX IF NOT EXISTS spring_ai_vector_index
    ON public.vector_store USING hnsw (embedding vector_cosine_ops);

-- 清理 /ai/embedtest 写入的测试数据（默认注释，按需手动执行）
-- DELETE FROM public.vector_store WHERE metadata->>'source' = 'embedtest';
-- 早期 embedtest 写入的数据没有 source 元数据，可执行下面这条清理（仅限测试库）：
-- DELETE FROM public.vector_store WHERE metadata::jsonb = '{}'::jsonb;
