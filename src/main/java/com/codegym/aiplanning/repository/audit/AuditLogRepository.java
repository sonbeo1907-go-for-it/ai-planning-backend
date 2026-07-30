package com.codegym.aiplanning.repository.audit;

import com.codegym.aiplanning.entity.audit.AuditLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {}
