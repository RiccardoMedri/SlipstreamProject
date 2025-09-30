package com.cesenahome.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.cesenahome.data.remote.JellyfinApiClient
import com.cesenahome.data.remote.toArtist // Corrected import
import com.cesenahome.domain.models.Artist
import org.jellyfin.sdk.model.api.BaseItemDto

class ArtistPagingSource(
    private val apiClient: JellyfinApiClient,
    private val pageSize: Int
): PagingSource<Int, Artist>() {
    
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Artist> {
        return try {
            val startIndex = params.key ?: 0
            val limit = params.loadSize.coerceAtMost(pageSize)

            val artistDtoList: List<BaseItemDto> = apiClient.fetchArtistAlphabetical(
                startIndex = startIndex,
                limit = limit
            )

            // Corrected to map to Artist objects using toArtist
            val artistList = artistDtoList.map { it.toArtist(apiClient) }

            val nextKey = if (artistDtoList.size < limit) null else startIndex + artistDtoList.size
            val prevKey = if (startIndex == 0) null else (startIndex - pageSize).coerceAtLeast(0)

            LoadResult.Page(
                data = artistList, // Corrected data type
                prevKey = prevKey,
                nextKey = nextKey
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }


    // Corrected PagingState type to Artist
    override fun getRefreshKey(state: PagingState<Int, Artist>): Int? {
        return state.anchorPosition?.let {
                anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(pageSize) ?: anchorPage?.nextKey?.minus(pageSize)
        }
    }
}
