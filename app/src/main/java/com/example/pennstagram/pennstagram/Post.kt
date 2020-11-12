package com.example.pennstagram.pennstagram

data class Post (
        val date: String = "",
        val description: String = "",
        val image: String = "",
        var uuid: String = "",
        var ref: String = ""
)