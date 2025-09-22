package com.cesenahome.domain.usecases

import com.cesenahome.domain.models.HomeMenuItem
import com.cesenahome.domain.repository.HomepageRepository

fun interface GetHomepageMenuUseCase {
    suspend operator fun invoke(): List<HomeMenuItem>
}

class GetHomepageMenuUseCaseImpl(
    private val homepageRepository: HomepageRepository
) : GetHomepageMenuUseCase {
    override suspend fun invoke(): List<HomeMenuItem> = homepageRepository.getHomepageMenu()
}