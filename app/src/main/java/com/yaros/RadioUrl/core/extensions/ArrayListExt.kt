package com.yaros.RadioUrl.core.extensions

fun ArrayList<Long>.copy(): ArrayList<Long> {
    val copy: ArrayList<Long> = ArrayList()
    this.forEach { copy.add(it) }
    return copy
}
