package com.example.pennstagram.pennstagram

import com.example.pennstagram.R
import kotlinx.android.synthetic.main.activity_new_post.*

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.support.v7.app.AppCompatActivity
import android.widget.Toast
import java.io.IOException
import android.support.v4.app.ActivityCompat
import android.content.pm.PackageManager
import android.icu.text.SimpleDateFormat
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.support.v4.content.ContextCompat
import android.util.Log
import com.example.pennstagram.Home
import com.google.android.gms.tasks.Continuation
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.*

class NewPostActivity : AppCompatActivity() {
    private val GALLERY = 1
    private val CAMERA = 2

    lateinit var database: DatabaseReference
    lateinit var storage: StorageReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_post)

        submit.setOnClickListener {
            if (image.drawable == null) {
                Toast.makeText(this@NewPostActivity,
                        "You must pick an image to post!",
                        Toast.LENGTH_SHORT).show()
            } else if (description.text.toString() == "") {
                Toast.makeText(this@NewPostActivity,
                        "You must write a description!",
                        Toast.LENGTH_SHORT).show()
            } else {
                val i = Intent(this, Home::class.java)
                startActivity(i)
                saveToFireBase()
            }
        }
        cancel.setOnClickListener {
            val i = Intent(this, Home::class.java)
            startActivity(i)
        }

        val sdf = SimpleDateFormat("dd/M/yyyy hh:mm:ss")
        val currentDate = sdf.format(Date())

        // clear all fields
        date.text = currentDate

        // set on click listeners for buttons
        gallery.setOnClickListener { choosePhotoFromGallery() }
        camera.setOnClickListener { takePhotoFromCamera() }

        database = FirebaseDatabase.getInstance().reference
        storage = FirebaseStorage.getInstance().reference
    }

    val dataReference = FirebaseDatabase.getInstance().getReference("images")

    // This method saves data to firebase; is ran when you click "submit"
    private fun saveToFireBase() {
        // Step 1: Save image to Storage
        var file = Uri.fromFile(File(image.tag.toString()))
        val imageRef = storage.child("images/${file.lastPathSegment}")
        val path = "images/${file.lastPathSegment}"
        val uploadTask = imageRef.putFile(file)

        // Step 2: Get imageURL and save entire Post to Database
        uploadTask.continueWithTask(Continuation<
                com.google.firebase.storage.UploadTask.TaskSnapshot,
                com.google.android.gms.tasks.Task<android.net.Uri>> { task ->
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
                val post = Post(date.text.toString(), description.text.toString(), downloadUri.toString())

                val key = database.child("posts").push().key!!

                post.uuid = key
                post.ref = path
                database.child("posts").child(key).setValue(post)

                val sdf = SimpleDateFormat("dd/M/yyyy hh:mm:ss")
                val currentDate = sdf.format(Date())

                // clear all fields
                date.text = currentDate
                description.text.clear()
                image.setImageDrawable(getDrawable(R.mipmap.ic_launcher))
                val i = Intent(this, Home::class.java)
                startActivity(i)
            }
        }
    }

    // Checks is permissions have been granted
    private fun checkPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
                                                        PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED) {
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
                    image!!.setImageBitmap(bitmap)
                    image.tag = getRealPathFromURI(contentURI) //saving file path to tag so we can get it later for firebase
                    Log.d("gallery url", image.tag.toString())
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
            image!!.setImageBitmap(thumbnail)
            image.tag = saveImage(thumbnail)
            Log.d("camera url", image.tag.toString())
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

            fo.close()
            Log.d("TAG", "File Saved::--->" + f.absolutePath)

            return f.absolutePath
        }
        catch (e1: IOException) {
            e1.printStackTrace()
        }

        return ""
    }
}