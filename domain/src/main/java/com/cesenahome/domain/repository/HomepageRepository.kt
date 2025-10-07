package com.cesenahome.domain.repository

import com.cesenahome.domain.models.homepage.HomeMenuItem
import com.cesenahome.domain.models.homepage.LibraryCounts

interface HomepageRepository {
    /**
     * Basic menu for the homepage. Counts are NOT required.
     * If you want counts badges, call [getLibraryCounts] and merge them in the VM.
     */
    suspend fun getHomepageMenu(): List<HomeMenuItem>

    /**
     * Optional: fetch library counts to show on badges.
     * Not required for navigation itself.
     */
    suspend fun getLibraryCounts(): Result<LibraryCounts>
}