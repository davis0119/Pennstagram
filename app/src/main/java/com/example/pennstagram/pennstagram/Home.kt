package com.example.pennstagram

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_home.*
import android.support.v7.widget.LinearLayoutManager
import android.util.Log
import android.widget.Toast
import com.example.pennstagram.pennstagram.NewPostActivity
import com.example.pennstagram.pennstagram.Post
import com.example.pennstagram.pennstagram.PostAdapter
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlin.collections.ArrayList


class Home : AppCompatActivity() {

    private val POSTS: ArrayList<Post> = ArrayList()

    lateinit var database: DatabaseReference
    lateinit var storage: StorageReference

    private val posts = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        post_recycler_view.layoutManager = LinearLayoutManager(this)
        post_recycler_view.adapter = PostAdapter(this, POSTS) { position ->
            val builder: AlertDialog.Builder = AlertDialog.Builder(this)
            builder.setTitle("Delete Post")
            builder.setMessage("Are you sure you want to delete this post?")
            builder.setPositiveButton(
                    "Delete!!!!! grr", DialogInterface.OnClickListener { dialog, id ->
                val postReference = FirebaseDatabase.getInstance().reference
                        .child("posts").child(POSTS[position].uuid)
                // Remove from Cloud Storage
                postReference.removeValue()
                val i = Intent(this, Home::class.java)
                startActivity(i)
                dialog.dismiss()
            })
            builder.setNegativeButton(
                    "No, sorry.", DialogInterface.OnClickListener { dialog, id ->
                Toast.makeText(this,
                        "Ok, then.",
                        Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            })
            val alertDialog: AlertDialog = builder.create()
            alertDialog.show()
        }


        // get reference to Firebase database and storage
        database = FirebaseDatabase.getInstance().reference
        storage = FirebaseStorage.getInstance().reference

        // take to create a post
        create_post.setOnClickListener {
            val i = Intent(this, NewPostActivity::class.java)
            startActivity(i)
        }

        // listener to retrieve new data from Firebase
        val postListener = object : ValueEventListener {
            // when data changes, add the new Profile to the adapter
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                dataSnapshot.children.forEach{
                    // for each new data piece, extract the Profile
                    val post = it.getValue(Post::class.java)
                    // check if profile is already in the adapter
                    if (!posts.contains(post!!.uuid)) {
                        Log.d("name", post.toString())
                        POSTS.add(post)
                        posts.add(post.uuid)
                        post_recycler_view.adapter?.notifyDataSetChanged()
                    }
                }

            }
            override fun onCancelled(databaseError: DatabaseError) {
                // Getting Post failed, log a message
                Log.w("TAG", "loadPost:onCancelled", databaseError.toException())
            }
        }
        database.child("posts").addValueEventListener(postListener)
    }

}
