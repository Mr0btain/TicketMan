package org.mr0btain.ticketman.ui.screens.main.components.viewmodel

import org.mr0btain.ticketman.data.MainDataItem

interface DataHandler {
    val TAG: String
    fun setSelectedIdentifier(identifier: Int)
    fun getNextIdentifier(): Int?
    fun getForIdentifier(identifier: Int): MainDataItem?
    fun updateOrAdd(item: MainDataItem)
    fun getAll(): List<MainDataItem>
    fun remove(item: MainDataItem)
}