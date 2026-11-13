package com.privatedrop.repository;

import com.privatedrop.entity.Secret;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SecretRepository  extends JpaRepository<Secret, UUID> {
}
