package com.finance.userservice.repository;

import com.finance.userservice.entity.User;
import feign.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);
    Optional<User> findByEmailHash(String emailHash);
    Optional<User> findByPhoneHash(String phoneHash);

    Page<User> findByFullNameContainingIgnoreCaseOrUsernameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrPhoneContainingIgnoreCase(
            String fullName, String username, String email, String phone, Pageable pageable);
    @Query("""
    SELECT u FROM User u
    WHERE :textSearch IS NULL
       OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :textSearch, '%'))
       OR LOWER(u.username) LIKE LOWER(CONCAT('%', :textSearch, '%'))
       OR LOWER(u.email) LIKE LOWER(CONCAT('%', :textSearch, '%'))
       OR LOWER(u.phone) LIKE LOWER(CONCAT('%', :textSearch, '%'))
""")
    Page<User> searchUsers(@Param("textSearch") String textSearch, Pageable pageable);

}
