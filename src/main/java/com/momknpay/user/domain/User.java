package com.momknpay.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A seeded user. There is no password: users never log in (ADR-001, ADR-005). */
@Entity
@Table(name = "users")
public class User {

    @Id private String id;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, updatable = false, length = 11)
    private String mobile;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "pin_hash", nullable = false, length = 72)
    private String pinHash;

    @Column(name = "member_since", nullable = false, updatable = false)
    private Instant memberSince;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {}

    public User(
            String id,
            String fullName,
            String mobile,
            String email,
            String pinHash,
            Instant memberSince) {
        this.id = id;
        this.fullName = fullName;
        this.mobile = mobile;
        this.email = email;
        this.pinHash = pinHash;
        this.memberSince = memberSince;
        this.updatedAt = memberSince;
    }

    public void updateProfile(String fullName, String email, Instant now) {
        if (fullName != null) {
            this.fullName = fullName;
        }
        if (email != null) {
            this.email = email;
        }
        this.updatedAt = now;
    }

    public String getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getMobile() {
        return mobile;
    }

    public String getEmail() {
        return email;
    }

    public String getPinHash() {
        return pinHash;
    }

    public Instant getMemberSince() {
        return memberSince;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
