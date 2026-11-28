package com.qx.orbit.bili.core.api

enum class OrbitContributorId {
    QINXIAN,
    IAM_RJ,
}

enum class OrbitContributorRole(val label: String) {
    DEVELOPER("开发者"),
    CONTRIBUTOR("贡献者"),
}

data class OrbitContributor(
    val id: OrbitContributorId,
    val displayName: String,
    val role: OrbitContributorRole,
)

val orbitContributors: List<OrbitContributor> = listOf(
    OrbitContributor(OrbitContributorId.QINXIAN, "琴弦上的小医生", OrbitContributorRole.DEVELOPER),
    OrbitContributor(OrbitContributorId.IAM_RJ, "iamRJ", OrbitContributorRole.CONTRIBUTOR),
)
