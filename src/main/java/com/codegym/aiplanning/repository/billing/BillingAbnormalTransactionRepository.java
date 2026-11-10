package com.codegym.aiplanning.repository.billing;

import com.codegym.aiplanning.entity.billing.BillingAbnormalTransaction;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BillingAbnormalTransactionRepository extends JpaRepository<BillingAbnormalTransaction, UUID> {

    List<BillingAbnormalTransaction> findByOrderCode(String orderCode);

    List<BillingAbnormalTransaction> findByExternalTransactionId(String externalTransactionId);
}
