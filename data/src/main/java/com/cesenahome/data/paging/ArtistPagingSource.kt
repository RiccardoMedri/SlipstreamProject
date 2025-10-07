package com.cesenahome.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.cesenahome.data.remote.JellyfinApiClient
import com.cesenahome.data.remote.toArtist
import com.cesenahome.domain.models.artist.Artist
import com.cesenahome.domain.models.artist.ArtistPagingRequest
import org.jellyfin.sdk.model.api.BaseItemDto

class ArtistPagingSource(
    private val apiClient: JellyfinApiClient,
    private val pageSize: Int,
    private val request: ArtistPagingRequest
) : PagingSource<Int, Artist>() {
    
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Artist> {
        return try {
            val startIndex = params.key ?: 0
            val limit = params.loadSize.coerceAtMost(pageSize)

            val artistDtoList: List<BaseItemDto> = apiClient.fetchArtists(
                startIndex = startIndex,
                limit = limit,
                request = request
            )

            val artistList = artistDtoList.map { it.toArtist(apiClient) }

            val nextKey = if (artistDtoList.size < limit) null else startIndex + artistDtoList.size
            val prevKey = if (startIndex == 0) null else (startIndex - pageSize).coerceAtLeast(0)

            LoadResult.Page(
                data = artistList,
                prevKey = prevKey,
                nextKey = nextKey
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }


    override fun getRefreshKey(state: PagingState<Int, Artist>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(pageSize) ?: anchorPage?.nextKey?.minus(pageSize)
        }
    }
}
