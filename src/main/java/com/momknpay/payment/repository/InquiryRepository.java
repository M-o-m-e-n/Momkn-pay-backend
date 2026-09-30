package com.momknpay.payment.repository;

import com.momknpay.payment.domain.Inquiry;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface InquiryRepository extends JpaRepository<Inquiry, String> {

    /** Row-locks the user's inquiry so concurrent confirms on it run one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inquiry i where i.id = :id and i.userId = :userId") // locks only the
    // inquiry row
    Optional<Inquiry> findForUpdate(String id, String userId);
}
