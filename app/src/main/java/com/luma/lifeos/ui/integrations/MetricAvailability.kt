package com.luma.lifeos.ui.integrations

import androidx.annotation.StringRes
import com.luma.lifeos.R

enum class MetricAbsence {
    LOADING, READ_ERROR, CONFIGURATION_ERROR, CHECKING_ACCESS, NO_CHECK_INS, NO_RATINGS,
    IMPORTS_OFF, ACCESS_NEEDED, DEVICE_LOCKED, PROVIDER_UPDATE, PROVIDER_UNAVAILABLE,
    ACCESS_CHECK_FAILED, IMPORT_FAILED, NO_SAVED_DATA,
}

enum class MetricPeriod { DAY, WEEK, GENERIC }

/** Returns null for any saved value, including a measured zero. */
fun metricAbsence(
    loading: Boolean,
    readFailed: Boolean,
    hasSavedData: Boolean,
    availability: IntegrationAvailability?,
): MetricAbsence? {
    if (loading) return MetricAbsence.LOADING
    if (readFailed) return MetricAbsence.READ_ERROR
    if (hasSavedData) return null
    if (availability == null) return MetricAbsence.NO_SAVED_DATA
    if (availability.configurationFailed) return MetricAbsence.CONFIGURATION_ERROR
    if (availability.enabled == null) return when (availability.access) {
        IntegrationAccessState.ERROR -> MetricAbsence.ACCESS_CHECK_FAILED
        IntegrationAccessState.UNAVAILABLE -> MetricAbsence.PROVIDER_UNAVAILABLE
        else -> MetricAbsence.LOADING
    }
    if (!availability.enabled) return MetricAbsence.IMPORTS_OFF
    return when (availability.access) {
        IntegrationAccessState.CHECKING -> MetricAbsence.CHECKING_ACCESS
        IntegrationAccessState.ALLOWED -> if (availability.importFailed) MetricAbsence.IMPORT_FAILED else MetricAbsence.NO_SAVED_DATA
        IntegrationAccessState.REQUIRED -> MetricAbsence.ACCESS_NEEDED
        IntegrationAccessState.LOCKED -> MetricAbsence.DEVICE_LOCKED
        IntegrationAccessState.UPDATE_REQUIRED -> MetricAbsence.PROVIDER_UPDATE
        IntegrationAccessState.UNAVAILABLE -> MetricAbsence.PROVIDER_UNAVAILABLE
        IntegrationAccessState.ERROR -> MetricAbsence.ACCESS_CHECK_FAILED
    }
}

@StringRes
fun MetricAbsence.label(period: MetricPeriod): Int = when (this) {
    MetricAbsence.LOADING -> R.string.metric_loading
    MetricAbsence.READ_ERROR -> R.string.metric_read_error
    MetricAbsence.CONFIGURATION_ERROR -> R.string.metric_configuration_error
    MetricAbsence.CHECKING_ACCESS -> R.string.metric_checking_access
    MetricAbsence.NO_CHECK_INS -> if (period == MetricPeriod.WEEK) R.string.metric_no_check_ins_week else R.string.metric_no_check_ins_day
    MetricAbsence.NO_RATINGS -> if (period == MetricPeriod.WEEK) R.string.metric_no_ratings_week else R.string.metric_no_ratings_day
    MetricAbsence.IMPORTS_OFF -> R.string.metric_imports_off
    MetricAbsence.ACCESS_NEEDED -> R.string.metric_access_needed
    MetricAbsence.DEVICE_LOCKED -> R.string.metric_device_locked
    MetricAbsence.PROVIDER_UPDATE -> R.string.metric_provider_update
    MetricAbsence.PROVIDER_UNAVAILABLE -> R.string.metric_provider_unavailable
    MetricAbsence.ACCESS_CHECK_FAILED -> R.string.metric_access_check_failed
    MetricAbsence.IMPORT_FAILED -> R.string.metric_import_failed
    MetricAbsence.NO_SAVED_DATA -> when (period) {
        MetricPeriod.DAY -> R.string.metric_no_saved_day
        MetricPeriod.WEEK -> R.string.metric_no_saved_week
        MetricPeriod.GENERIC -> R.string.metric_no_saved_measurements
    }
}

fun IntegrationAvailability.needsAccessSecondary(): Boolean =
    enabled == false && access == IntegrationAccessState.REQUIRED
