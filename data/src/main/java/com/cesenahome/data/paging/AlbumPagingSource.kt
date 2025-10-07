package com.cesenahome.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.cesenahome.data.remote.JellyfinApiClient
import com.cesenahome.data.remote.toAlbum
import com.cesenahome.domain.models.album.Album
import com.cesenahome.domain.models.album.AlbumPagingRequest
import org.jellyfin.sdk.model.api.BaseItemDto

class AlbumPagingSource(
    private val apiClient: JellyfinApiClient,
    private val pageSize: Int,
    private val request: AlbumPagingRequest
) : PagingSource<Int, Album>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Album> {
        return try {
            val startIndex = params.key ?: 0
            val limit = params.loadSize.coerceAtMost(pageSize)

            val albumDtoList: List<BaseItemDto> = apiClient.fetchAlbums(
                startIndex = startIndex,
                limit = limit,
                request = request
            )

            val albumList = albumDtoList.map { it.toAlbum(apiClient) }

            val nextKey = if (albumDtoList.size < limit) null else startIndex + albumDtoList.size
            val prevKey = if (startIndex == 0) null else (startIndex - pageSize).coerceAtLeast(0)

            LoadResult.Page(
                data = albumList,
                prevKey = prevKey,
                nextKey = nextKey
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Album>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(pageSize) ?: anchorPage?.nextKey?.minus(pageSize)
        }
    }
}
