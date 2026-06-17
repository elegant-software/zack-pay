package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.persistence.model.MonitoringRecord
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MonitoringRecordRepository : JpaRepository<MonitoringRecord, UUID>
