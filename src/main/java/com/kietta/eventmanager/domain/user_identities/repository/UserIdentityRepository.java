package com.kietta.eventmanager.domain.user_identities.repository;

import com.kietta.eventmanager.domain.user_identities.entity.UserIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {
    Optional<UserIdentity> findByProviderAndProviderId(String provider, String providerId);
    Optional<UserIdentity> findByProviderIgnoreCaseAndProviderIdIgnoreCase(String provider, String providerId);

    // IF USER REGISTER BY LOCAL, AND SIGN IN WITH GOOGLE, THEN THIS METHOD WILL BE CALLED TO CHECK IF THE USER HAS SIGN IN WITH GOOGLE BEFORE
    boolean existsByProviderAndProviderId(String provider, String providerId);
    boolean existsByProviderIgnoreCaseAndProviderIdIgnoreCase(String provider, String providerId);
}
