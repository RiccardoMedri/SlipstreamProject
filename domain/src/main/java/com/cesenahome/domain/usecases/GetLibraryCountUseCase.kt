package com.cesenahome.domain.usecases

import com.cesenahome.domain.models.homepage.LibraryCounts
import com.cesenahome.domain.repository.HomepageRepository

fun interface GetLibraryCountsUseCase {
    suspend operator fun invoke(): Result<LibraryCounts>
}

class GetLibraryCountsUseCaseImpl(
    private val homepageRepository: HomepageRepository
) : GetLibraryCountsUseCase {
    override suspend fun invoke(): Result<LibraryCounts> = homepageRepository.getLibraryCounts()
}