package com.cesenahome.domain.repository

import com.cesenahome.domain.models.homepage.HomeMenuItem
import com.cesenahome.domain.models.homepage.LibraryCounts

interface HomepageRepository {
    suspend fun getHomepageMenu(): List<HomeMenuItem>
    suspend fun getLibraryCounts(): Result<LibraryCounts>
}