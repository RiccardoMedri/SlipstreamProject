package com.cesenahome.domain.usecases

import com.cesenahome.domain.repository.SongRepository
import kotlin.Result

interface AddSongToFavouritesUseCase {
    suspend operator fun invoke(songId: String): Result<Unit>
}

class AddSongToFavouritesUseCaseImpl(
    private val songRepository: SongRepository,
) : AddSongToFavouritesUseCase {
    override suspend fun invoke(songId: String, ): Result<Unit> {
        return songRepository.addSongToFavourites(songId)
    }
}