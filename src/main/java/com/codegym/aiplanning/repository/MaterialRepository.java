package com.codegym.aiplanning.repository;

import com.codegym.aiplanning.entity.material.Material;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MaterialRepository extends JpaRepository<Material, UUID> {
}
