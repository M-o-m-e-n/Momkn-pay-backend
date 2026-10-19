package com.momknpay.ad.repository;

import com.momknpay.ad.domain.Ad;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdRepository extends JpaRepository<Ad, String> {

    /** The banners to show, in display order. */
    List<Ad> findByActiveTrueOrderBySortOrderAscIdAsc();
}
