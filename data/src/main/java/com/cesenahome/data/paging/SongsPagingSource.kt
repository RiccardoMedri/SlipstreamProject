package com.cesenahome.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.cesenahome.data.remote.JellyfinApiClient
import com.cesenahome.domain.models.Song
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType

class SongsPagingSource(
    private val api: JellyfinApiClient,
    private val pageSize: Int
) : PagingSource<Int, Song>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Song> {
        return try {
            val startIndex = params.key ?: 0
            val limit = params.loadSize.coerceAtMost(pageSize)

            val page: List<BaseItemDto> = api.fetchSongsAlphabetical(
                startIndex = startIndex,
                limit = limit
            )

            val data = page.map { it.toDomain() }

            val nextKey = if (page.size < limit) null else startIndex + page.size
            val prevKey = if (startIndex == 0) null else (startIndex - pageSize).coerceAtLeast(0)

            LoadResult.Page(
                data = data,
                prevKey = prevKey,
                nextKey = nextKey
            )
        } catch (t: Throwable) {
            LoadResult.Error(t)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Song>): Int? {
        // Return a key near the current anchor position
        val anchor = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchor) ?: return null
        return page.prevKey?.plus(pageSize) ?: page.nextKey?.minus(pageSize)
    }

    private fun BaseItemDto.toDomain(): Song {
        val ticks = runTimeTicks
        val idStr = id?.toString().orEmpty()
        val primaryTag = imageTags?.get(ImageType.PRIMARY) ?: albumPrimaryImageTag

        return Song(
            id = idStr,
            title = name.orEmpty(),
            album = album,
            artist = artists?.firstOrNull(),
            durationMs = ticks?.let { it / 10_000L },
            artworkUrl = api.buildImageUrl(itemId = idStr, imageTag = primaryTag, maxSize = 256)
        )
    }
}