package com.codegym.aiplanning.repository.ai.impl;

import com.codegym.aiplanning.controller.admin.ai.dto.AdminAiExecutionFilter;
import com.codegym.aiplanning.entity.ai.AiExecution;
import com.codegym.aiplanning.entity.ai.AiExecutionOperation;
import com.codegym.aiplanning.entity.ai.AiExecutionStatus;
import com.codegym.aiplanning.entity.ai.AiProvider;
import com.codegym.aiplanning.entity.ai.AiProviderConfig;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import com.codegym.aiplanning.repository.ai.AiExecutionAdminRepositoryCustom;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class AiExecutionAdminRepositoryCustomImpl implements AiExecutionAdminRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    public AiExecutionAdminRepositoryCustomImpl(EntityManager em) {
        this.em = em;
    }

    public AiExecutionAdminRepositoryCustomImpl() {}

    @Override
    public Page<AiExecution> searchAdminExecutions(AdminAiExecutionFilter filter, Pageable pageable) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        // 1. Count query
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<AiExecution> countRoot = countQuery.from(AiExecution.class);
        List<Predicate> countPredicates = buildPredicates(cb, countRoot, filter);
        countQuery.select(cb.count(countRoot));
        if (!countPredicates.isEmpty()) {
            countQuery.where(countPredicates.toArray(new Predicate[0]));
        }

        Long total = em.createQuery(countQuery).getSingleResult();
        if (total == null || total == 0L) {
            return new PageImpl<>(Collections.emptyList(), pageable != null ? pageable : Pageable.unpaged(), 0);
        }

        // 2. Data query
        CriteriaQuery<AiExecution> query = cb.createQuery(AiExecution.class);
        Root<AiExecution> root = query.from(AiExecution.class);
        List<Predicate> dataPredicates = buildPredicates(cb, root, filter);
        if (!dataPredicates.isEmpty()) {
            query.where(dataPredicates.toArray(new Predicate[0]));
        }

        // Apply sorting: default is createdAt DESC, id DESC
        List<Order> orders = new ArrayList<>();
        if (pageable != null && pageable.getSort().isSorted()) {
            boolean hasId = false;
            for (Sort.Order sortOrder : pageable.getSort()) {
                Path<?> sortPath = resolveSortPath(root, sortOrder.getProperty());
                if ("id".equalsIgnoreCase(sortOrder.getProperty())) {
                    hasId = true;
                }
                orders.add(sortOrder.isAscending() ? cb.asc(sortPath) : cb.desc(sortPath));
            }
            if (!hasId) {
                orders.add(cb.desc(root.get("id")));
            }
        } else {
            orders.add(cb.desc(root.get("createdAt")));
            orders.add(cb.desc(root.get("id")));
        }
        query.orderBy(orders);

        TypedQuery<AiExecution> typedQuery = em.createQuery(query);

        // Fetch graph to eagerly load providerConfig and provider avoiding N+1
        EntityGraph<AiExecution> graph = em.createEntityGraph(AiExecution.class);
        graph.addSubgraph("providerConfig").addAttributeNodes("provider");
        typedQuery.setHint("jakarta.persistence.fetchgraph", graph);

        if (pageable != null && pageable.isPaged()) {
            typedQuery.setFirstResult((int) pageable.getOffset());
            typedQuery.setMaxResults(pageable.getPageSize());
        }

        List<AiExecution> content = typedQuery.getResultList();
        return new PageImpl<>(content, pageable != null ? pageable : Pageable.unpaged(), total);
    }

    private List<Predicate> buildPredicates(CriteriaBuilder cb, Root<AiExecution> root, AdminAiExecutionFilter filter) {
        List<Predicate> predicates = new ArrayList<>();
        if (filter == null) {
            return predicates;
        }

        if (filter.from() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.from()));
        }

        if (filter.to() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.to()));
        }

        if (filter.providerId() != null || (filter.model() != null && !filter.model().isBlank())) {
            Join<AiExecution, AiProviderConfig> configJoin = root.join("providerConfig", JoinType.INNER);
            if (filter.providerId() != null) {
                Join<AiProviderConfig, AiProvider> providerJoin = configJoin.join("provider", JoinType.INNER);
                predicates.add(cb.equal(providerJoin.get("id"), filter.providerId()));
            }
            if (filter.model() != null && !filter.model().isBlank()) {
                predicates.add(cb.equal(cb.lower(configJoin.get("model")), filter.model().trim().toLowerCase()));
            }
        }

        if (filter.purpose() != null && !filter.purpose().isBlank()) {
            try {
                AiPurpose purpose = AiPurpose.valueOf(filter.purpose().trim().toUpperCase());
                predicates.add(cb.equal(root.get("purpose"), purpose));
            } catch (IllegalArgumentException e) {
                predicates.add(cb.disjunction());
            }
        }

        if (filter.operation() != null && !filter.operation().isBlank()) {
            try {
                AiExecutionOperation operation = AiExecutionOperation.valueOf(filter.operation().trim().toUpperCase());
                predicates.add(cb.equal(root.get("operation"), operation));
            } catch (IllegalArgumentException e) {
                predicates.add(cb.disjunction());
            }
        }

        if (filter.status() != null && !filter.status().isBlank()) {
            try {
                AiExecutionStatus status = AiExecutionStatus.valueOf(filter.status().trim().toUpperCase());
                predicates.add(cb.equal(root.get("status"), status));
            } catch (IllegalArgumentException e) {
                predicates.add(cb.disjunction());
            }
        }

        if (filter.failureCode() != null && !filter.failureCode().isBlank()) {
            predicates.add(cb.equal(root.get("failureCode"), filter.failureCode().trim()));
        }

        return predicates;
    }

    private Path<?> resolveSortPath(Root<AiExecution> root, String property) {
        if ("id".equalsIgnoreCase(property)) {
            return root.get("id");
        } else if ("startedAt".equalsIgnoreCase(property)) {
            return root.get("startedAt");
        } else if ("completedAt".equalsIgnoreCase(property)) {
            return root.get("completedAt");
        } else if ("latencyMs".equalsIgnoreCase(property)) {
            return root.get("latencyMs");
        } else if ("status".equalsIgnoreCase(property)) {
            return root.get("status");
        } else {
            return root.get("createdAt");
        }
    }
}
