package com.enterprise.rag.evaluation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EvalRunRepository extends JpaRepository<EvalRun, Long> {

    List<EvalRun> findTop20ByOrderByCreatedAtDesc();
}
