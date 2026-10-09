package com.codegym.aiplanning.service.billing;

import com.codegym.aiplanning.entity.billing.BillingAbnormalTransaction;
import com.codegym.aiplanning.repository.billing.BillingAbnormalTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingAbnormalTransactionService {

    private static final Logger log = LoggerFactory.getLogger(BillingAbnormalTransactionService.class);

    private final BillingAbnormalTransactionRepository repository;

    public BillingAbnormalTransactionService(BillingAbnormalTransactionRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BillingAbnormalTransaction recordAbnormal(
            String orderCode,
            String externalTransactionId,
            Long amountVnd,
            String reason,
            String details,
            String rawPayload) {
        log.warn("Recording abnormal billing transaction: orderCode={}, extTx={}, reason={}, details={}",
                orderCode, externalTransactionId, reason, details);
        BillingAbnormalTransaction record = new BillingAbnormalTransaction(
                orderCode,
                externalTransactionId,
                amountVnd,
                reason,
                details,
                rawPayload);
        return repository.save(record);
    }
}
