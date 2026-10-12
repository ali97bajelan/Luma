package com.luma.lifeos.integration.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.luma.lifeos.domain.health.*

val stepReadPermissions: Set<String> = setOf(HealthPermission.getReadPermission(StepsRecord::class))

internal class HealthConnectPlatform(private val context: Context) : HealthPlatform {
    override suspend fun access(): HealthAccess = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_UNAVAILABLE -> HealthAccess.UNAVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAccess.UPDATE_REQUIRED
        HealthConnectClient.SDK_AVAILABLE -> if (HealthConnectClient.getOrCreate(context).permissionController
            .getGrantedPermissions().containsAll(stepReadPermissions)) HealthAccess.GRANTED else HealthAccess.REQUIRED
        else -> HealthAccess.UNAVAILABLE
    }
    override suspend fun steps(window: StepWindow): Long? = HealthConnectClient.getOrCreate(context).aggregate(
        AggregateRequest(metrics = setOf(StepsRecord.COUNT_TOTAL),
            timeRangeFilter = TimeRangeFilter.between(window.start, window.end))
    )[StepsRecord.COUNT_TOTAL]
}
