package com.verbilume.app.data

fun WordDetail.primaryAudio(accent: Accent): String? =
    if (accent == Accent.UK) audioUk ?: audioUs else audioUs ?: audioUk

fun WordDetail.secondaryAudio(accent: Accent): String? =
    if (accent == Accent.UK) audioUs ?: audioUk else audioUk ?: audioUs

fun WordDetail.exampleAudio(index: Int): String? = if (index == 0) audioEx1 else audioEx2
