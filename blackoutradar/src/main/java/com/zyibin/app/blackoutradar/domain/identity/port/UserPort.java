package com.zyibin.app.blackoutradar.domain.identity.port;

import com.zyibin.app.blackoutradar.domain.identity.User;
import java.util.Optional;

public interface UserPort {

    Optional<User> findByEmail(String email);

    User save(User user);
}