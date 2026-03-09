package com.finance.financeservice.mysql.repository;

import com.finance.financeservice.mysql.entity.SepayOAuth2Token;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SepayOAuth2TokenRepository extends JpaRepository<SepayOAuth2Token, Long> {

    Optional<SepayOAuth2Token> findByUserId(String userId);

    List<SepayOAuth2Token> findByConnectedTrue();

    void deleteByUserId(String userId);
}
