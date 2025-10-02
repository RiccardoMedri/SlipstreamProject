package com.cesenahome.ui.common

import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import androidx.appcompat.widget.SearchView
import com.cesenahome.ui.R
import com.google.android.material.appbar.MaterialToolbar

fun MaterialToolbar.setupSearchMenu(
    queryHint: String,
    initialQuery: String,
    onQueryChanged: (String) -> Unit
) {
    menu.clear()
    inflateMenu(R.menu.menu_toolbar_search)
    val searchItem = menu.findItem(R.id.action_search)
    val searchView = searchItem.actionView as SearchView

    searchView.queryHint = queryHint
    searchView.maxWidth = Int.MAX_VALUE
    searchView.imeOptions = EditorInfo.IME_ACTION_SEARCH
    searchView.setIconifiedByDefault(true)

    var lastQuery = initialQuery
    searchView.setQuery(initialQuery, false)
    if (initialQuery.isNotEmpty()) {
        searchItem.expandActionView()
        searchView.clearFocus()
    }

    searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
        override fun onQueryTextSubmit(query: String?): Boolean {
            val newQuery = query.orEmpty()
            if (newQuery != lastQuery) {
                lastQuery = newQuery
                onQueryChanged(newQuery)
            }
            searchView.clearFocus()
            return true
        }

        override fun onQueryTextChange(newText: String?): Boolean {
            val text = newText.orEmpty()
            if (text != lastQuery) {
                lastQuery = text
                onQueryChanged(text)
            }
            return true
        }
    })

    searchView.setOnCloseListener {
        if (lastQuery.isNotEmpty()) {
            lastQuery = ""
            onQueryChanged("")
            searchView.setQuery("", false)
        }
        false
    }

    searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
        override fun onMenuItemActionExpand(item: MenuItem): Boolean = true

        override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
            if (lastQuery.isNotEmpty()) {
                lastQuery = ""
                onQueryChanged("")
                searchView.setQuery("", false)
            }
            return true
        }
    })
}