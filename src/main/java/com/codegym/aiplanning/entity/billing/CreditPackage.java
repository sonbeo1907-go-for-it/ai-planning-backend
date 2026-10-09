package com.codegym.aiplanning.entity.billing;

import com.codegym.aiplanning.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "credit_packages")
public class CreditPackage extends BaseEntity {

    @Column(name = "package_code", nullable = false, unique = true, length = 50)
    private String packageCode;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "price_vnd", nullable = false)
    private long priceVnd;

    @Column(name = "base_credits", nullable = false)
    private long baseCredits;

    @Column(name = "bonus_credits", nullable = false)
    private long bonusCredits;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CreditPackageStatus status;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected CreditPackage() {}

    public CreditPackage(
            String packageCode,
            String name,
            long priceVnd,
            long baseCredits,
            long bonusCredits,
            CreditPackageStatus status,
            int sortOrder) {
        this.packageCode = packageCode;
        this.name = name;
        this.priceVnd = priceVnd;
        this.baseCredits = baseCredits;
        this.bonusCredits = bonusCredits;
        this.status = status;
        this.sortOrder = sortOrder;
    }

    public String getPackageCode() {
        return packageCode;
    }

    public void setPackageCode(String packageCode) {
        this.packageCode = packageCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getPriceVnd() {
        return priceVnd;
    }

    public void setPriceVnd(long priceVnd) {
        this.priceVnd = priceVnd;
    }

    public long getBaseCredits() {
        return baseCredits;
    }

    public void setBaseCredits(long baseCredits) {
        this.baseCredits = baseCredits;
    }

    public long getBonusCredits() {
        return bonusCredits;
    }

    public void setBonusCredits(long bonusCredits) {
        this.bonusCredits = bonusCredits;
    }

    public CreditPackageStatus getStatus() {
        return status;
    }

    public void setStatus(CreditPackageStatus status) {
        this.status = status;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public long getTotalCredits() {
        return baseCredits + bonusCredits;
    }
}
