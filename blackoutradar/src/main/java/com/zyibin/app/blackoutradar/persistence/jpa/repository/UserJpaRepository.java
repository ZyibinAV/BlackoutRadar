package com.zyibin.app.blackoutradar.persistence.jpa.repository;

import com.zyibin.app.blackoutradar.persistence.jpa.entity.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByEmail(String email);

    @Modifying
    @Query(value = "INSERT INTO \"user\" (id, email, password_hash, role, is_active, nickname, about, avatar_key, created_at, updated_at) VALUES (:id, :email, :passwordHash, :role, :active, :nickname, :about, :avatar, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) ON CONFLICT (email) DO NOTHING", nativeQuery = true)
    int insertIgnore(@Param("id") UUID id,
                     @Param("email") String email,
                     @Param("passwordHash") String passwordHash,
                     @Param("role") String role,
                     @Param("active") boolean active,
                     @Param("nickname") String nickname,
                     @Param("about") String about,
                     @Param("avatar") String avatar);
}