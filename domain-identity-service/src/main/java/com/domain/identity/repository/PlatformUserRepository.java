package com.domain.identity.repository;

import com.domain.identity.domain.PlatformUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository boundary for identity-owned user accounts. */
public interface PlatformUserRepository extends JpaRepository<PlatformUser, UUID> {
    /** Finds one account by its normalized email login. */
    Optional<PlatformUser> findByEmailIgnoreCase(String email);
}
