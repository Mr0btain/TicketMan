package org.mr0btain.ticketman.data.cache

abstract class Cache(
    tag: String
) {
    protected val GET_TAG = "$tag:Get"
    protected val PUT_TAG = "$tag:Put"
    protected val REMOVE_TAG = "$tag:Remove"
    protected val CLEAR_TAG = "$tag:Clear"
}