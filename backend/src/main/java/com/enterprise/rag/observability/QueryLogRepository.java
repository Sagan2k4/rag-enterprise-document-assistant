package com.enterprise.rag.observability;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QueryLogRepository extends JpaRepository<QueryLog, Long> {

    List<QueryLog> findTop20ByOrderByCreatedAtDesc();
}
