package com.fishmoun.soulroute.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIdType;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

@Slf4j
@Configuration
public class TravelAppVectorStoreConfig {

    @Bean
    VectorStore travelAppVectorStore(JdbcTemplate jdbcTemplate,
                                     EmbeddingModel dashscopeEmbeddingModel,
                                     @Value("${soulroute.rag.vector.schema-name:public}") String schemaName,
                                     @Value("${soulroute.rag.vector.table-name:travel_document_chunks}") String tableName,
                                     @Value("${soulroute.rag.vector.dimensions:1536}") int dimensions,
                                     @Value("${soulroute.rag.vector.initialize-schema:true}") boolean initializeSchema,
                                     @Value("${soulroute.rag.vector.remove-existing-table:false}") boolean removeExistingTable,
                                     @Value("${soulroute.rag.vector.index-type:HNSW}") PgIndexType indexType,
                                     @Value("${soulroute.rag.vector.validate-table:false}") boolean validateTable) {
        return PgVectorStore.builder(jdbcTemplate, dashscopeEmbeddingModel)
                .schemaName(schemaName)
                .vectorTableName(tableName)
                .idType(PgIdType.TEXT)
                .dimensions(dimensions)
                .distanceType(PgDistanceType.COSINE_DISTANCE)
                .indexType(indexType)
                .initializeSchema(initializeSchema)
                .removeExistingVectorStoreTable(removeExistingTable)
                .vectorTableValidationsEnabled(validateTable)
                .build();
    }

    @Bean
    @Order(0)
    ApplicationRunner travelAppVectorStoreJsonbInitializer(JdbcTemplate jdbcTemplate,
                                                           @Value("${soulroute.rag.vector.schema-name:public}") String schemaName,
                                                           @Value("${soulroute.rag.vector.table-name:travel_document_chunks}") String tableName) {
        return args -> jdbcTemplate.execute(String.format(
                "ALTER TABLE %s.%s ALTER COLUMN metadata TYPE jsonb USING metadata::jsonb",
                schemaName,
                tableName
        ));
    }

    @Bean
    @Order(1)
    ApplicationRunner travelAppVectorStoreIndexer(VectorStore travelAppVectorStore,
                                                  TravelAppDocumentLoader documentLoader,
                                                  @Value("${soulroute.rag.index-on-startup:true}") boolean indexOnStartup) {
        return args -> {
            if (!indexOnStartup) {
                log.info("RAG 文档启动索引已关闭。");
                return;
            }
            List<Document> chunks = documentLoader.loadAndSplitMarkdowns();
            if (chunks.isEmpty()) {
                return;
            }
            travelAppVectorStore.add(chunks);
            log.info("RAG chunk 已写入 pgvector，数量: {}", chunks.size());
        };
    }
}
