package com.example.pennstagram

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.support.design.widget.BottomNavigationView
import android.support.v7.app.AppCompatActivity
import android.util.Half.toFloat
import android.view.View
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_home.*
import com.kidach1.tinderswipe.model.CardModel
import com.kidach1.tinderswipe.view.SimpleCardStackAdapter
import com.kidach1.tinderswipe.view.CardContainer
import java.io.IOException
import android.support.v4.app.ActivityCompat
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.support.v4.content.ContextCompat
import android.util.Log
import com.google.android.gms.tasks.Continuation
import com.google.android.gms.tasks.Task
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlin.collections.ArrayList


class Home : AppCompatActivity() {
    private val GALLERY = 1
    private val CAMERA = 2

    lateinit var database: DatabaseReference
    lateinit var storage: StorageReference

    private val profiles = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // sets listener for navigation bar
        nav_view.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener)

        // create sample card models
        val cardModel = CardModel("Pupper",
                "6 months and cute",
                "https://static.scientificamerican.com/sciam/cache/file/D059BC4A-CCF3-4495-849ABBAFAED10456_source.jpg?w=590&h=800&526ED1E1-34FF-4472-B348B8B4769AB2A1") // title, desc, imgUrl
        val cardModel2 = CardModel("Boo",
                "12 years old",
                "https://imagesvc.timeincapp.com/v3/mm/image?url=https%3A%2F%2Ffortunedotcom.files.wordpress.com%2F2019%2F01%2Fboo.jpg") // title, desc, imgUrl
        val cardModel3 = CardModel("TinderSwipe",
                "Description for card.",
                "https://tinder.com/static/tinder.png"); // title, desc, imgUrl ※

        // sets up the cardView (very similar to recycler view setup)
        val cardAdapter = SimpleCardStackAdapter(this)
        cardAdapter.add(cardModel)
        cardAdapter.add(cardModel2)
        cardAdapter.add(cardModel3)

        val cardContainer = findViewById<View>(R.id.cardContainer) as CardContainer
        cardContainer.adapter = cardAdapter //sets adapter

        addSwipeListener(cardContainer) //listens to swipes (scroll down to see the method)

        // get reference to Firebase database and storage
        database = FirebaseDatabase.getInstance().reference
        storage = FirebaseStorage.getInstance().reference

        // set onClickListeners for buttons
        gallery_upload.setOnClickListener { choosePhotoFromGallery() }
        camera_upload.setOnClickListener { takePhotoFromCamera() }
        new_post.setOnClickListener { saveToFireBase() }

        // listener to retrieve new data from Firebase
        val postListener = object : ValueEventListener {
            // when data changes, add the new Profile to the adapter
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                dataSnapshot.children.forEach{
                    // for each new data piece, extract the Profile
                    val profile = it.getValue<Profile>(Profile::class.java)

                    // check if profile is already in the adapter
                    if (!profiles.contains(profile!!.uuid)) {
                        Log.d("name", profile.toString())
                        cardAdapter.add(CardModel(profile.name, profile.description, profile.imageUrl))
                        cardContainer.adapter = cardAdapter
                        addSwipeListener(cardContainer)
                        profiles.add(profile.uuid)
                    }
                }

            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Getting Post failed, log a message
                Log.w("TAG", "loadPost:onCancelled", databaseError.toException())
            }
        }

        database.child("profiles").addValueEventListener(postListener)
    }

    val dataReference = FirebaseDatabase.getInstance().getReference("images")
    private fun writeNewImageInfoToDB(description: String, date: String, url: String) {
        val info = Profile(description, date, url)
        val key = dataReference!!.push().key
        dataReference!!.child(key!!).setValue(info)
    }

    // This method saves data to firebase; is ran when you click "submit"
    private fun saveToFireBase() {
        // Step 1: Save image to Storage
        var file = Uri.fromFile(File(your_image.tag.toString()))
        val imageRef = storage.child("images/${file.lastPathSegment}")
        val uploadTask = imageRef.putFile(file)

        // Step 2: Get imageURL and save entire Profile to Database
        uploadTask.continueWithTask(Continuation<UploadTask.TaskSnapshot, Task<Uri>> { task ->
            if (!task.isSuccessful) {
                task.exception?.let {
                    throw it
                }
            }
            return@Continuation imageRef.downloadUrl
        }).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val downloadUri = task.result
                Log.d("download url", downloadUri.toString())
                // Create Profile with this Url and save to Database
                val profile = Profile(my_name.text.toString(), my_description.text.toString(), downloadUri.toString())

                val key = database.child("profiles").push().key!!

                profile.uuid = key
                database.child("profiles").child(key).setValue(profile)

                // clear all fields
                my_name.text.clear()
                my_description.text.clear()
                your_image.setImageDrawable(getDrawable(R.mipmap.ic_launcher))
            } else {
                // Handle failures
            }
        }
    }

    // Checks is permissions have been granted
    private fun checkPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            return false
        }
        return true
    }

    // Request for permissions
    private fun requestPermission() {
        ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                200)
        //request code will let us know what was requested; will be useful in onRequestPermissionsResult
    }

    // if we receive permission to use camera, then run takePhotoFromCamera again
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 200) {
            takePhotoFromCamera()
        }
    }

    // is ran when we press the gallery button to choose photo from gallery
    private fun choosePhotoFromGallery() {
        val galleryIntent = Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

        // GALLERY is a result code that will tell us we came from the gallery activity
        startActivityForResult(galleryIntent, GALLERY)
    }

    // is ran when we press the camera button to choose photo from camera
    private fun takePhotoFromCamera() {
        if (!checkPermission()) {
            requestPermission()
        } else {
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

            // CAMERA is a result code that will tell us we came from the camera activity
            startActivityForResult(cameraIntent, CAMERA)
        }

    }

    // this gets run after you return from the gallery or camera
    public override fun onActivityResult(requestCode:Int, resultCode:Int, data: Intent?) {

        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == GALLERY) // if we return from the gallery, then set the ImageView
        {
            if (data != null)
            {
                val contentURI = data.data
                try
                {
                    val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, contentURI)
                    your_image!!.setImageBitmap(bitmap)
                    your_image.tag = getRealPathFromURI(contentURI) //saving file path to tag so we can get it later for firebase
                    Log.d("gallery url", your_image.tag.toString())
                }
                catch (e: IOException) {
                    e.printStackTrace()
                    Toast.makeText(this, "Failed!", Toast.LENGTH_SHORT).show()
                }
            }
        }
        else if (requestCode == CAMERA) // if we return from the camera, then save the image
        {
            val thumbnail = data!!.extras!!.get("data") as Bitmap
            your_image!!.setImageBitmap(thumbnail)
            your_image.tag = saveImage(thumbnail)
            Log.d("camera url", your_image.tag.toString())
        }
    }

    // Convert the image URI to the direct file system path of the image file
    private fun getRealPathFromURI(contentUri: Uri): String {

        // can post image
        val proj = arrayOf(MediaStore.Images.Media.DATA)
        val cursor = getContentResolver().query(contentUri,
                proj, // WHERE clause selection arguments (none)
                null, null, null)// Which columns to return
        // WHERE clause; which rows to return (all rows)
        // Order-by clause (ascending by name)
        val column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
        cursor.moveToFirst()

        return cursor.getString(column_index)
    }

    // saves image to gallery (when given a bitmap)
    private fun saveImage(myBitmap: Bitmap):String {
        val bytes = ByteArrayOutputStream()
        myBitmap.compress(Bitmap.CompressFormat.JPEG, 90, bytes)
        val wallpaperDirectory = File(
                (Environment.getExternalStorageDirectory()).toString())
        // have the object build the directory structure, if needed.
        if (!wallpaperDirectory.exists())
        {
            wallpaperDirectory.mkdirs()
        }

        try
        {
            val f = File(wallpaperDirectory, ((Calendar.getInstance()
                    .timeInMillis).toString() + ".jpg"))
            f.createNewFile()
            val fo = FileOutputStream(f)
            fo.write(bytes.toByteArray())
            MediaScannerConnection.scanFile(this,
                    arrayOf(f.path),
                    arrayOf("image/jpeg"), null)

            // TODO: convert to bitmap
            fo.close()
            Log.d("TAG", "File Saved::--->" + f.absolutePath)

            return f.absolutePath
        }
        catch (e1: IOException) {
            e1.printStackTrace()
        }

        return ""
    }

    // listens for card swipes
    private fun addSwipeListener(cardContainer: CardContainer) {
        cardContainer.setOnSwipeListener(object : CardContainer.onSwipeListener {
            override fun onSwipe(scrollProgressPercent: Float) {
                val view = cardContainer.selectedView

                // if we're swiping to the left
                if (scrollProgressPercent < 0) {
                    view.findViewById<View>(R.id.item_swipe_right_indicator).alpha = -scrollProgressPercent
                    Log.d("leftswipe", "swiped left")
                } else {
                    view.findViewById<View>(R.id.item_swipe_right_indicator).alpha = 0.toFloat()
                }

                // if we're swiping to the right
                if (scrollProgressPercent > 0) {
                    view.findViewById<View>(R.id.item_swipe_left_indicator).alpha = scrollProgressPercent
                    Log.d("right swipe", "swiped right")
                } else {
                    view.findViewById<View>(R.id.item_swipe_left_indicator).alpha = 0.toFloat()
                }

            }
        })
    }

    // navigation bar listener
    private val mOnNavigationItemSelectedListener = BottomNavigationView.OnNavigationItemSelectedListener { item ->
        when (item.itemId) {
            R.id.navigation_home -> {
                cardContainer.visibility = View.VISIBLE
                profile.visibility = View.INVISIBLE
                return@OnNavigationItemSelectedListener true
            }
            R.id.navigation_dashboard -> {
                cardContainer.visibility = View.INVISIBLE
                profile.visibility = View.VISIBLE
                return@OnNavigationItemSelectedListener true
            }
            R.id.navigation_notifications -> {
                cardContainer.visibility = View.INVISIBLE
                profile.visibility = View.INVISIBLE
                return@OnNavigationItemSelectedListener true
            }
        }
        false
    }

}
