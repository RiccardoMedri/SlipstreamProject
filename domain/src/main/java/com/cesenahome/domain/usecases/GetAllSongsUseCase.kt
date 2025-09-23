package com.cesenahome.domain.usecases

import com.cesenahome.domain.models.Song
import com.cesenahome.domain.repository.SongsRepository

fun interface GetAllSongsUseCase {
    suspend operator fun invoke(): List<Song>
}

class GetAllSongsUseCaseImpl(
    private val repo: SongsRepository
) : GetAllSongsUseCase {
    override suspend fun invoke(): List<Song> = repo.getAllSongsAlphabetical()
}