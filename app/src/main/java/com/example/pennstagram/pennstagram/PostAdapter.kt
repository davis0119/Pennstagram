package com.example.pennstagram.pennstagram

import android.app.Activity
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.example.pennstagram.R
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.post_item.view.*

class PostAdapter(
        private var activity: Activity,
        private var postList: List<Post>,
        private var getPosition: (Int) -> Unit) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    class PostViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var postImage : ImageView = view.post_image
        var postDate : TextView = view.post_date
        var postDescription : TextView = view.post_description
        var delButton : Button = view.delete
        // for binding contact info to view
        fun bind(item: Post, position: Int, getPosition: (Int) -> Unit) {
            // sets up the onClickListener to the button
            delButton.setOnClickListener{
                getPosition(position)
            }
        }
    }

    // create new contacts
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val imageView = LayoutInflater.from(activity)
                .inflate(R.layout.post_item, parent, false)
        return PostViewHolder(imageView)
    }
    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val key = postList[position] // postlist
        holder.postDate.text = key.date
        holder.postDescription.text = key.description
        holder.bind(postList[position], position, getPosition)
        Picasso.get()
                .load(key.image) // load the image
                .into(holder.postImage)
    }
    override fun getItemCount() = postList.size
}