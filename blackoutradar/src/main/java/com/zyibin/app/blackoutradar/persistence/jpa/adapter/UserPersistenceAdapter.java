package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import com.zyibin.app.blackoutradar.domain.identity.RegistrationResult;
import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.port.UserPort;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.UserEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.mapper.UserMapper;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.UserJpaRepository;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UserPersistenceAdapter implements UserPort {

    private final UserJpaRepository repository;
    private final UserMapper mapper;

    public UserPersistenceAdapter(UserJpaRepository repository, UserMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(email).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public User save(User user) {
        UserEntity entity = mapper.toEntity(user);
        preservePasswordHash(user.id(), entity);
        return mapper.toDomain(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findPasswordHash(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return repository.findById(userId)
                .flatMap(entity -> Optional.ofNullable(entity.getPasswordHash()));
    }

    @Override
    @Transactional
    public RegistrationResult register(User user, String passwordHash) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        int inserted = repository.insertIgnore(user.id(), user.email(), passwordHash,
                user.role().name(), user.isActive(), user.nickname(), user.about(), user.avatar());
        if (inserted == 0) {
            return RegistrationResult.ALREADY_EXISTS;
        }
        return RegistrationResult.CREATED;
    }

    private void preservePasswordHash(java.util.UUID id, UserEntity entity) {
        repository.findById(id)
                .filter(existing -> existing.getPasswordHash() != null)
                .ifPresent(existing -> entity.setPasswordHash(existing.getPasswordHash()));
    }
}