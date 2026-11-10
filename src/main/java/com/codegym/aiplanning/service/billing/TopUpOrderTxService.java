package com.codegym.aiplanning.service.billing;

import com.codegym.aiplanning.entity.billing.TopUpOrder;
import com.codegym.aiplanning.repository.billing.TopUpOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TopUpOrderTxService {

    private final TopUpOrderRepository topUpOrderRepository;

    public TopUpOrderTxService(TopUpOrderRepository topUpOrderRepository) {
        this.topUpOrderRepository = topUpOrderRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TopUpOrder saveOrderInNewTransaction(TopUpOrder order) {
        return topUpOrderRepository.saveAndFlush(order);
    }
}
