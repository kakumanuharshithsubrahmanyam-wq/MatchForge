package com.matchforge.matchmaking.repository;

import java.util.UUID;

import com.matchforge.matchmaking.domain.MatchRecord;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRecordRepository extends JpaRepository<MatchRecord, UUID> {
}