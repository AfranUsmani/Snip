package io.github.afranusmani.urlshortener.repository;

import io.github.afranusmani.urlshortener.model.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UrlRepository extends JpaRepository<UrlMapping, Long> {

    Optional<UrlMapping> findByShortCode(String shortCode);

    /** Used to reject a custom alias before insert with a friendly 409. */
    boolean existsByShortCode(String shortCode);

    /** Removes a link by its short code (used by the delete endpoint). */
    long deleteByShortCode(String shortCode);

    /**
     * Atomically increments the hit counter in a single statement, avoiding a
     * read-modify-write race on the hot redirect path.
     */
    @Modifying
    @Query("update UrlMapping u set u.hitCount = u.hitCount + 1 where u.shortCode = :shortCode")
    int incrementHitCount(@Param("shortCode") String shortCode);
}
