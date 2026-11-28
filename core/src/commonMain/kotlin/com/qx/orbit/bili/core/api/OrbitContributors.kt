package com.qx.orbit.bili.core.api

enum class OrbitContributorId {
    QINXIAN,
    IAM_RJ,
    YOUSHEN2,
    HAICHUAN_WANG1,
    RECHOILLES,
    GFHDHYTGHD,
    STAR_ZER0,
}

enum class OrbitContributorRole(val label: String) {
    DEVELOPER("开发者"),
    CONTRIBUTOR("贡献者"),
}

data class OrbitContributor(
    val id: OrbitContributorId,
    val displayName: String,
    val githubLogin: String,
    val role: OrbitContributorRole,
)

val orbitContributors: List<OrbitContributor> = listOf(
    OrbitContributor(OrbitContributorId.QINXIAN, "琴弦上的小医生", "PianoEthan", OrbitContributorRole.DEVELOPER),
    OrbitContributor(OrbitContributorId.IAM_RJ, "iamRJ", "RJMultiDev", OrbitContributorRole.CONTRIBUTOR),
    OrbitContributor(OrbitContributorId.YOUSHEN2, "洛汐聚合物", "youshen2", OrbitContributorRole.CONTRIBUTOR),
    OrbitContributor(OrbitContributorId.HAICHUAN_WANG1, "超重氢", "HaichuanWang1", OrbitContributorRole.CONTRIBUTOR),
    OrbitContributor(OrbitContributorId.RECHOILLES, "RoseReveRiE_", "ReChoilles", OrbitContributorRole.CONTRIBUTOR),
    OrbitContributor(OrbitContributorId.GFHDHYTGHD, "lin", "gfhdhytghd", OrbitContributorRole.CONTRIBUTOR),
    OrbitContributor(OrbitContributorId.STAR_ZER0, "星澪Star_ZER0", "Star-ZER0", OrbitContributorRole.CONTRIBUTOR),
)
