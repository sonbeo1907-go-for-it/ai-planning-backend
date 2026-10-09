package com.codegym.aiplanning.service.billing;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.config.BillingProperties;
import com.codegym.aiplanning.controller.billing.dto.CreditPackageResponse;
import com.codegym.aiplanning.controller.billing.dto.TopUpOrderResponse;
import com.codegym.aiplanning.entity.billing.CreditPackage;
import com.codegym.aiplanning.entity.billing.CreditPackageStatus;
import com.codegym.aiplanning.entity.billing.TopUpOrder;
import com.codegym.aiplanning.entity.billing.TopUpOrderStatus;
import com.codegym.aiplanning.repository.billing.CreditPackageRepository;
import com.codegym.aiplanning.repository.billing.TopUpOrderRepository;
import com.codegym.aiplanning.service.billing.gateway.CheckoutResult;
import com.codegym.aiplanning.service.billing.gateway.PaymentGateway;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingOrderService {

    private static final Logger log = LoggerFactory.getLogger(BillingOrderService.class);

    private final CreditPackageRepository creditPackageRepository;
    private final TopUpOrderRepository topUpOrderRepository;
    private final TopUpOrderTxService topUpOrderTxService;
    private final PaymentGateway paymentGateway;
    private final BillingProperties billingProperties;

    public BillingOrderService(
            CreditPackageRepository creditPackageRepository,
            TopUpOrderRepository topUpOrderRepository,
            TopUpOrderTxService topUpOrderTxService,
            PaymentGateway paymentGateway,
            BillingProperties billingProperties) {
        this.creditPackageRepository = creditPackageRepository;
        this.topUpOrderRepository = topUpOrderRepository;
        this.topUpOrderTxService = topUpOrderTxService;
        this.paymentGateway = paymentGateway;
        this.billingProperties = billingProperties;
    }

    @Transactional(readOnly = true)
    public List<CreditPackageResponse> getActivePackages() {
        return creditPackageRepository.findByStatusOrderBySortOrderAsc(CreditPackageStatus.ACTIVE).stream()
                .map(CreditPackageResponse::from)
                .toList();
    }

    public TopUpOrderResponse createTopUpOrder(UUID userId, UUID packageId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Idempotency-Key header is required");
        }
        idempotencyKey = idempotencyKey.trim();
        if (idempotencyKey.length() > 128) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Idempotency-Key must not exceed 128 characters");
        }
        if (packageId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "packageId is required");
        }

        // Fast check existing order for idempotency
        Optional<TopUpOrder> existingOrderOpt = topUpOrderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existingOrderOpt.isPresent()) {
            TopUpOrder existingOrder = existingOrderOpt.get();
            return handleExistingOrder(existingOrder, packageId);
        }

        // Backend is Source of Truth: load package from DB
        CreditPackage pkg = creditPackageRepository.findById(packageId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.CREDIT_PACKAGE_NOT_FOUND, "Credit package not found with id: " + packageId));

        // Validate package status: only ACTIVE allowed
        if (pkg.getStatus() != CreditPackageStatus.ACTIVE) {
            throw new BusinessException(
                    ErrorCode.CREDIT_PACKAGE_NOT_ACTIVE, "Credit package is not active: " + pkg.getPackageCode());
        }

        // Validate min/max from configuration
        long minVnd = billingProperties.getTopUp().getMinVnd();
        long maxVnd = billingProperties.getTopUp().getMaxVnd();
        if (pkg.getPriceVnd() < minVnd || pkg.getPriceVnd() > maxVnd) {
            throw new BusinessException(
                    ErrorCode.TOP_UP_AMOUNT_OUT_OF_BOUNDS,
                    String.format("Package price %d VND is outside configured bounds [%d, %d]", pkg.getPriceVnd(), minVnd, maxVnd));
        }

        // Backend determines totalCreditsSnapshot
        long baseCredits = pkg.getBaseCredits();
        long bonusCredits = pkg.getBonusCredits();
        long totalCredits = baseCredits + bonusCredits;

        // Backend determines expiresAt (+15 minutes from configuration)
        Instant expiresAt = Instant.now().plus(billingProperties.getTopUp().getExpirationMinutes(), ChronoUnit.MINUTES);

        // Generate unique order code
        String orderCode = "TOPUP-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        TopUpOrder newOrder = new TopUpOrder(
                userId,
                orderCode,
                pkg.getId(),
                pkg.getPackageCode(),
                pkg.getName(),
                pkg.getPriceVnd(),
                baseCredits,
                bonusCredits,
                totalCredits,
                TopUpOrderStatus.PENDING,
                "VND",
                paymentGateway.getProviderName(),
                null,
                idempotencyKey,
                expiresAt);

        // Phase 1: Persist Order in independent transaction
        TopUpOrder savedOrder;
        try {
            savedOrder = topUpOrderTxService.saveOrderInNewTransaction(newOrder);
        } catch (DataIntegrityViolationException ex) {
            if (isIdempotencyConstraintViolation(ex)) {
                log.info("Concurrent insert caught idempotency unique constraint for user: {} and key: {}", userId, idempotencyKey);
                // Read in new transaction context after rollback
                TopUpOrder concurrentOrder = topUpOrderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                        .orElseThrow(() -> ex);
                return handleExistingOrder(concurrentOrder, packageId);
            }
            throw ex;
        }

        // Phase 2: Call PaymentGateway without holding DB transaction
        CheckoutResult checkoutResult;
        try {
            checkoutResult = paymentGateway.createCheckout(savedOrder);
        } catch (Exception ex) {
            log.error("Payment provider unavailable for order: {}", savedOrder.getOrderCode(), ex);
            // Failure semantics: Order stays PENDING, throw 503
            throw new BusinessException(
                    ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE, "Payment provider is currently unavailable");
        }

        return TopUpOrderResponse.from(savedOrder, checkoutResult.checkoutUrl());
    }

    @Transactional(readOnly = true)
    public TopUpOrderResponse getTopUpOrder(UUID userId, UUID orderId) {
        TopUpOrder order = topUpOrderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TOP_UP_ORDER_NOT_FOUND, "Top-up order not found with id: " + orderId));

        String checkoutUrl = null;
        if (order.getStatus() == TopUpOrderStatus.PENDING && order.getExpiresAt().isAfter(Instant.now())) {
            try {
                checkoutUrl = paymentGateway.createCheckout(order).checkoutUrl();
            } catch (Exception ex) {
                log.warn("Could not generate checkout url for pending order: {}", order.getOrderCode(), ex);
            }
        }

        return TopUpOrderResponse.from(order, checkoutUrl);
    }

    private TopUpOrderResponse handleExistingOrder(TopUpOrder existingOrder, UUID requestedPackageId) {
        // If package matches -> return existing order
        if (existingOrder.getPackageId() != null && existingOrder.getPackageId().equals(requestedPackageId)) {
            String checkoutUrl = null;
            if (existingOrder.getStatus() == TopUpOrderStatus.PENDING && existingOrder.getExpiresAt().isAfter(Instant.now())) {
                try {
                    checkoutUrl = paymentGateway.createCheckout(existingOrder).checkoutUrl();
                } catch (Exception ex) {
                    log.warn("Could not regenerate checkout url for order: {}", existingOrder.getOrderCode(), ex);
                }
            }
            return TopUpOrderResponse.from(existingOrder, checkoutUrl);
        }

        // Otherwise -> HTTP 409 TOP_UP_IDEMPOTENCY_CONFLICT
        throw new BusinessException(
                ErrorCode.TOP_UP_IDEMPOTENCY_CONFLICT,
                "Idempotency key '" + existingOrder.getIdempotencyKey() + "' was already used for a different package");
    }

    private boolean isIdempotencyConstraintViolation(DataIntegrityViolationException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        Throwable cause = ex.getRootCause();
        String rootMsg = cause != null && cause.getMessage() != null ? cause.getMessage().toLowerCase() : "";

        return msg.contains("uk_top_up_orders_user_idempotency")
                || msg.contains("23505")
                || rootMsg.contains("uk_top_up_orders_user_idempotency")
                || rootMsg.contains("23505");
    }
}
