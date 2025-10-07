package com.cesenahome.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.cesenahome.data.remote.JellyfinApiClient
import com.cesenahome.data.remote.toPlaylist
import com.cesenahome.domain.models.playlist.Playlist
import com.cesenahome.domain.models.playlist.PlaylistPagingRequest
import org.jellyfin.sdk.model.api.BaseItemDto

class PlaylistPagingSource(
    private val apiClient: JellyfinApiClient,
    private val pageSize: Int,
    private val request: PlaylistPagingRequest,
) : PagingSource<Int, Playlist>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Playlist> {
        return try {
            val startIndex = params.key ?: 0
            val limit = params.loadSize.coerceAtMost(pageSize)

            val playlistDtoList: List<BaseItemDto> = apiClient.fetchPlaylists(
                startIndex = startIndex,
                limit = limit,
                request = request,
            )

            val playlistList = playlistDtoList.map { it.toPlaylist(apiClient) }

            val nextKey = if (playlistDtoList.size < limit) null else startIndex + playlistDtoList.size
            val prevKey = if (startIndex == 0) null else (startIndex - pageSize).coerceAtLeast(0)

            LoadResult.Page(
                data = playlistList,
                prevKey = prevKey,
                nextKey = nextKey,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Playlist>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(pageSize) ?: anchorPage?.nextKey?.minus(pageSize)
        }
    }
}