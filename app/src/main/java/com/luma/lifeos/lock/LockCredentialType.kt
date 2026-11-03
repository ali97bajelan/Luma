package com.luma.lifeos.lock

enum class LockCredentialType {
    PIN,
    PASSWORD,
    PATTERN,
}

data class LockCredentialDescriptor(
    val type: LockCredentialType,
    val generation: Long,
)

sealed interface CredentialDescriptorResult {
    data class Ready(val descriptor: LockCredentialDescriptor) : CredentialDescriptorResult
    data object Disabled : CredentialDescriptorResult
    data object Unavailable : CredentialDescriptorResult
}
