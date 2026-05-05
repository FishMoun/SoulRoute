package com.fishmoun.soulroute.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishmoun.soulroute.rag.HybridTravelDocumentRetriever;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@Slf4j
class TravelAppRagCloudAdvisorConfig {

    @Bean
    public Advisor travelAppRagCloudAdvisor(@Qualifier("travelAppVectorStore") VectorStore vectorStore,
                                            JdbcTemplate jdbcTemplate,
                                            ObjectMapper objectMapper,
                                            @Value("${soulroute.rag.top-k:5}") int topK,
                                            @Value("${soulroute.rag.hybrid.vector-top-k:12}") int vectorTopK,
                                            @Value("${soulroute.rag.hybrid.metadata-top-k:12}") int metadataTopK,
                                            @Value("${soulroute.rag.similarity-threshold:0.65}") double similarityThreshold,
                                            @Value("${soulroute.rag.hybrid.vector-weight:0.7}") double vectorWeight,
                                            @Value("${soulroute.rag.hybrid.metadata-weight:0.3}") double metadataWeight,
                                            @Value("${soulroute.rag.vector.schema-name:public}") String schemaName,
                                            @Value("${soulroute.rag.vector.table-name:travel_document_chunks}") String tableName) {
        DocumentRetriever documentRetriever = new HybridTravelDocumentRetriever(
                vectorStore,
                jdbcTemplate,
                objectMapper,
                schemaName,
                tableName,
                topK,
                vectorTopK,
                metadataTopK,
                similarityThreshold,
                vectorWeight,
                metadataWeight
        );
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .build();
    }
}
