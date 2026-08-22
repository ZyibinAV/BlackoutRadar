package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.port.UserPort;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.UserEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.mapper.UserMapper;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.UserJpaRepository;
import java.util.Optional;
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

    private void preservePasswordHash(java.util.UUID id, UserEntity entity) {
        repository.findById(id)
                .filter(existing -> existing.getPasswordHash() != null)
                .ifPresent(existing -> entity.setPasswordHash(existing.getPasswordHash()));
    }
}