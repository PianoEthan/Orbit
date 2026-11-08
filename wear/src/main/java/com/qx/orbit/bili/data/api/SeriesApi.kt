package com.qx.orbit.bili.data.api

import com.qx.orbit.bili.data.model.*
import com.qx.orbit.bili.data.remote.GsonConfig
import com.qx.orbit.bili.data.remote.Result
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SeriesApi {

    private val api by lazy { BiliApiService.create() }

    internal data class SeriesListData(
        @SerializedName("items_lists") val items_lists: SeriesItems? = null
    )

    internal data class SeriesItems(
        @SerializedName("series_list") val series_list: List<SeriesMeta>? = null,
        @SerializedName("page") val page: PageData? = null
    )

    internal data class SeriesMeta(
        @SerializedName("meta") val meta: SeriesMetaInfo? = null
    )

    internal data class SeriesMetaInfo(
        @SerializedName("series_id") val series_id: Int = 0,
        @SerializedName("name") val name: String? = null,
        @SerializedName("cover") val cover: String? = null,
        @SerializedName("description") val description: String? = null,
        @SerializedName("total") val total: Int = 0
    )

    internal data class PageData(
        @SerializedName("page_num") val page_num: Int = 0,
        @SerializedName("page_size") val page_size: Int = 0,
        @SerializedName("total") val total: Int = 0
    )

    internal data class SeriesArchivesData(
        @SerializedName("archives") val archives: List<SeriesArchive>? = null,
        @SerializedName("page") val page: PageData? = null
    )

    internal data class SeriesArchive(
        @SerializedName("aid") val aid: Long = 0,
        @SerializedName("bvid") val bvid: String? = null,
        @SerializedName("title") val title: String? = null,
        @SerializedName("pic") val pic: String? = null,
        @SerializedName("stat") val stat: SeriesStat? = null,
        @SerializedName("owner") val owner: SeriesOwner? = null
    )

    internal data class SeriesStat(
        @SerializedName("view") val view: Int = 0
    )

    internal data class SeriesOwner(
        @SerializedName("name") val name: String? = null
    )

    suspend fun getUserSeries(mid: Long, page: Int): Pair<List<Series>, PageInfo?> = withContext(Dispatchers.IO) {
        val params = WbiSigner.signParams(mapOf(
            "mid" to mid.toString(),
            "page_num" to page.toString(),
            "page_size" to "20"
        ))
        when (val resp = api.getUserSeries(params)) {
            is Result.Success -> {
                val type = object : TypeToken<ApiResponse<SeriesListData>>() {}.type
                val parsed: ApiResponse<SeriesListData>? = GsonConfig.gson.fromJson(resp.data, type)
                if (parsed == null || !parsed.isSuccess || parsed.data == null) return@withContext Pair(emptyList(), null)
                val items = parsed.data.items_lists
                val seriesList = items?.series_list?.map { meta ->
                    Series(
                        type = "series",
                        id = meta.meta?.series_id ?: 0,
                        title = meta.meta?.name ?: "",
                        cover = meta.meta?.cover ?: "",
                        intro = meta.meta?.description ?: "",
                        mid = mid,
                        total = (meta.meta?.total ?: 0).toString()
                    )
                } ?: emptyList()
                val pageInfo = items?.page?.let {
                    PageInfo(page_num = it.page_num, total = it.total, return_ps = it.page_size)
                }
                Pair(seriesList, pageInfo)
            }
            is Result.Error -> Pair(emptyList(), null)
        }
    }

    suspend fun getSeriesInfo(type: String, mid: Long, id: Int, page: Int): Pair<List<VideoCard>, PageInfo?> = withContext(Dispatchers.IO) {
        if (type == "series") {
            val params = WbiSigner.signParams(mapOf(
                "mid" to mid.toString(),
                "series_id" to id.toString(),
                "pn" to page.toString(),
                "ps" to "20"
            ))
            when (val resp = api.getSeriesArchives(params)) {
                is Result.Success -> {
                    val type2 = object : TypeToken<ApiResponse<SeriesArchivesData>>() {}.type
                    val parsed: ApiResponse<SeriesArchivesData>? = GsonConfig.gson.fromJson(resp.data, type2)
                    if (parsed == null || !parsed.isSuccess || parsed.data == null) return@withContext Pair(emptyList(), null)
                    parseArchives(parsed.data)
                }
                is Result.Error -> Pair(emptyList(), null)
            }
        } else {
            val params = WbiSigner.signParams(mapOf(
                "mid" to mid.toString(),
                "season_id" to id.toString(),
                "page_num" to page.toString(),
                "page_size" to "20"
            ))
            when (val resp = api.getSeasonsArchives(params)) {
                is Result.Success -> {
                    val type2 = object : TypeToken<ApiResponse<SeriesArchivesData>>() {}.type
                    val parsed: ApiResponse<SeriesArchivesData>? = GsonConfig.gson.fromJson(resp.data, type2)
                    if (parsed == null || !parsed.isSuccess || parsed.data == null) return@withContext Pair(emptyList(), null)
                    parseArchives(parsed.data)
                }
                is Result.Error -> Pair(emptyList(), null)
            }
        }
    }

    private fun parseArchives(data: SeriesArchivesData): Pair<List<VideoCard>, PageInfo?> {
        val cards = data.archives?.map { archive ->
            VideoCard(
                title = archive.title ?: "",
                upName = archive.owner?.name ?: "",
                view = StringUtil.toWan(archive.stat?.view?.toLong() ?: 0),
                cover = archive.pic ?: "",
                aid = archive.aid,
                bvid = archive.bvid ?: ""
            )
        } ?: emptyList()
        val pageInfo = data.page?.let {
            PageInfo(page_num = it.page_num, total = it.total, return_ps = it.page_size)
        }
        return Pair(cards, pageInfo)
    }
}
