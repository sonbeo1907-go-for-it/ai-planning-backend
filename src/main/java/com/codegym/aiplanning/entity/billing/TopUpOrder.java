package com.codegym.aiplanning.entity.billing;

import com.codegym.aiplanning.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "top_up_orders",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_top_up_orders_user_idempotency",
                    columnNames = {"user_id", "idempotency_key"}),
            @UniqueConstraint(
                    name = "uk_top_up_orders_order_code",
                    columnNames = {"order_code"}),
            @UniqueConstraint(
                    name = "uk_top_up_orders_external_tx",
                    columnNames = {"external_transaction_id"})
        })
public class TopUpOrder extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "order_code", nullable = false, unique = true, length = 64)
    private String orderCode;

    @Column(name = "package_id")
    private UUID packageId;

    @Column(name = "package_code_snapshot", nullable = false, length = 50)
    private String packageCodeSnapshot;

    @Column(name = "package_name_snapshot", nullable = false, length = 100)
    private String packageNameSnapshot;

    @Column(name = "price_vnd_snapshot", nullable = false)
    private long priceVndSnapshot;

    @Column(name = "base_credits_snapshot", nullable = false)
    private long baseCreditsSnapshot;

    @Column(name = "bonus_credits_snapshot", nullable = false)
    private long bonusCreditsSnapshot;

    @Column(name = "total_credits_snapshot", nullable = false)
    private long totalCreditsSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TopUpOrderStatus status;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "payment_provider", nullable = false, length = 50)
    private String paymentProvider;

    @Column(name = "checkout_reference")
    private String checkoutReference;

    @Column(name = "external_transaction_id")
    private String externalTransactionId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    protected TopUpOrder() {}

    public TopUpOrder(
            UUID userId,
            String orderCode,
            UUID packageId,
            String packageCodeSnapshot,
            String packageNameSnapshot,
            long priceVndSnapshot,
            long baseCreditsSnapshot,
            long bonusCreditsSnapshot,
            long totalCreditsSnapshot,
            TopUpOrderStatus status,
            String currency,
            String paymentProvider,
            String checkoutReference,
            String idempotencyKey,
            Instant expiresAt) {
        this.userId = userId;
        this.orderCode = orderCode;
        this.packageId = packageId;
        this.packageCodeSnapshot = packageCodeSnapshot;
        this.packageNameSnapshot = packageNameSnapshot;
        this.priceVndSnapshot = priceVndSnapshot;
        this.baseCreditsSnapshot = baseCreditsSnapshot;
        this.bonusCreditsSnapshot = bonusCreditsSnapshot;
        this.totalCreditsSnapshot = totalCreditsSnapshot;
        this.status = status;
        this.currency = currency;
        this.paymentProvider = paymentProvider;
        this.checkoutReference = checkoutReference;
        this.idempotencyKey = idempotencyKey;
        this.expiresAt = expiresAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getOrderCode() {
        return orderCode;
    }

    public UUID getPackageId() {
        return packageId;
    }

    public String getPackageCodeSnapshot() {
        return packageCodeSnapshot;
    }

    public String getPackageNameSnapshot() {
        return packageNameSnapshot;
    }

    public long getPriceVndSnapshot() {
        return priceVndSnapshot;
    }

    public long getBaseCreditsSnapshot() {
        return baseCreditsSnapshot;
    }

    public long getBonusCreditsSnapshot() {
        return bonusCreditsSnapshot;
    }

    public long getTotalCreditsSnapshot() {
        return totalCreditsSnapshot;
    }

    public TopUpOrderStatus getStatus() {
        return status;
    }

    public void setStatus(TopUpOrderStatus status) {
        this.status = status;
    }

    public String getCurrency() {
        return currency;
    }

    public String getPaymentProvider() {
        return paymentProvider;
    }

    public void setPaymentProvider(String paymentProvider) {
        this.paymentProvider = paymentProvider;
    }

    public String getCheckoutReference() {
        return checkoutReference;
    }

    public void setCheckoutReference(String checkoutReference) {
        this.checkoutReference = checkoutReference;
    }

    public String getExternalTransactionId() {
        return externalTransactionId;
    }

    public void setExternalTransactionId(String externalTransactionId) {
        this.externalTransactionId = externalTransactionId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Instant paidAt) {
        this.paidAt = paidAt;
    }
}
