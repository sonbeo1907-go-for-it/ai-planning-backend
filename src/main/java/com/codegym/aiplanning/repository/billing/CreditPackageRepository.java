package com.codegym.aiplanning.repository.billing;

import com.codegym.aiplanning.entity.billing.CreditPackage;
import com.codegym.aiplanning.entity.billing.CreditPackageStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CreditPackageRepository extends JpaRepository<CreditPackage, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from CreditPackage p where p.id = :id")
    Optional<CreditPackage> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);

    @org.springframework.data.jpa.repository.Query("select p from CreditPackage p "
            + "where (:status is null or p.status = :status)")
    org.springframework.data.domain.Page<CreditPackage> findFiltered(
            @org.springframework.data.repository.query.Param("status") CreditPackageStatus status,
            org.springframework.data.domain.Pageable pageable);

    List<CreditPackage> findByStatusOrderBySortOrderAsc(CreditPackageStatus status);

    Optional<CreditPackage> findByPackageCode(String packageCode);
}
