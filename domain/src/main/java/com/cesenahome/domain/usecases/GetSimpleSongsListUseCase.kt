package com.cesenahome.domain.usecases

import com.cesenahome.domain.models.song.Song
import com.cesenahome.domain.repository.SongRepository
import kotlin.Result

/**
 * Use case to fetch a simple list of songs for a given page and page size.
 * Suitable for direct list fetching rather than PagingData streams.
 */
interface GetSimpleSongsListUseCase {
    suspend operator fun invoke(page: Int, pageSize: Int): Result<List<Song>>
}

class GetSimpleSongsListUseCaseImpl(
    private val songRepository: SongRepository
) : GetSimpleSongsListUseCase {
    override suspend fun invoke(page: Int, pageSize: Int): Result<List<Song>> {
        return songRepository.getSongsList(page, pageSize)
    }
}
