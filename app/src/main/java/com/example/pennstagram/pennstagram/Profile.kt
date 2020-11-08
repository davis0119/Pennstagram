package com.example.pennstagram
/**
 * Created by Angel on 3/25/2019.
 */
data class Profile (
        val name: String = "",
        val description: String = "",
        val imageUrl: String = "",
        // TODO: change imageURL to bitmap encoding
        var uuid: String = "",
        var liked: Boolean = false
)