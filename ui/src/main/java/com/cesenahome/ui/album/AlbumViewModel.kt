package com.cesenahome.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.cesenahome.domain.models.Album
import com.cesenahome.domain.usecases.GetPagedAlbumUseCase
import kotlinx.coroutines.flow.Flow

class AlbumViewModel(
    getPagedAlbumUseCase: GetPagedAlbumUseCase,
    artistId: String? = null
): ViewModel() {
    val pagedAlbums: Flow<PagingData<Album>> = getPagedAlbumUseCase(artistId).cachedIn(viewModelScope)
}