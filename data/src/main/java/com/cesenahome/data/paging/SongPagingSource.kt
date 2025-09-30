package com.cesenahome.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.cesenahome.data.remote.JellyfinApiClient
import com.cesenahome.data.remote.toSong
import com.cesenahome.domain.models.Song
import org.jellyfin.sdk.model.api.BaseItemDto

class SongPagingSource(
    private val api: JellyfinApiClient,
    private val pageSize: Int,
    private val albumId: String? = null
) : PagingSource<Int, Song>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Song> {
        return try {
            val startIndex = params.key ?: 0
            val limit = params.loadSize.coerceAtMost(pageSize)

            val page: List<BaseItemDto> = if (albumId != null) {
                api.fetchSongsByAlbumId(albumId = albumId, startIndex = startIndex, limit = limit)
            } else {
                api.fetchSongsAlphabetical(startIndex = startIndex, limit = limit)
            }

            val data = page.map { it.toSong(api) }

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
        val anchor = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchor) ?: return null
        return page.prevKey?.plus(pageSize) ?: page.nextKey?.minus(pageSize)
    }
}
