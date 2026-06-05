package com.samedtevin.textocrapp

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.method.ScrollingMovementMethod
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var cameraImage: ImageView
    private lateinit var captureImageButton: Button
    private lateinit var resultText: TextView
    private lateinit var copyTextButton: Button
    private lateinit var getImageButton: Button
    private lateinit var languageSpinner: Spinner

    private var currentPhotoPath: String? = null
    private var lastBitmap: Bitmap? = null
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var takePictureLauncher: ActivityResultLauncher<Uri>
    private lateinit var pickImageLauncher: ActivityResultLauncher<String>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cameraImage = findViewById(R.id.cameraImage)
        captureImageButton = findViewById(R.id.captureImageButton)
        resultText = findViewById(R.id.resultText)
        copyTextButton = findViewById(R.id.copyTextBtn)
        getImageButton = findViewById(R.id.getImageButton)
        languageSpinner = findViewById(R.id.languageSpinner)

        val adapter = ArrayAdapter.createFromResource(this,R.array.languages,android.R.layout.simple_spinner_item)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        languageSpinner.adapter = adapter

        languageSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    lastBitmap?.let {
                        recognizeText(it, languageSpinner.selectedItem.toString())
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }


        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()){
            isGranted ->
            if(isGranted){
                captureImage()
            }
            else{
                Toast.makeText(this, "Camera Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }

        takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()){
            success ->
            if(success){
                currentPhotoPath?.let{path ->
                    val bitmap = BitmapFactory.decodeFile(path)
                    lastBitmap = bitmap
                    cameraImage.setImageBitmap(bitmap)
                    recognizeText(bitmap, languageSpinner.selectedItem.toString())
                }
            }
        }

        pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                val inputStream = contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)

                lastBitmap = bitmap
                cameraImage.setImageBitmap(bitmap)

                recognizeText(bitmap, languageSpinner.selectedItem.toString())
            }
        }

        captureImageButton.setOnClickListener {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        getImageButton.setOnClickListener {
            getImageFromGallery()
        }

    }

    private fun getImageFromGallery(){
        pickImageLauncher.launch("image/*")
    }


    private fun createImageFile(): File{
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss",  Locale.getDefault()).format(
            Date()
        )

        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)

        return File.createTempFile("JPEG_${timeStamp}_",".jpg",storageDir).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun captureImage(){
        val photoFile: File? = try{
            createImageFile()
        }catch(ex: IOException){
            Toast.makeText(this, "Error occurred while creating the file", Toast.LENGTH_SHORT).show()
            null
        }
        photoFile?.also{
            val photoUri: Uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider",it)
            takePictureLauncher.launch(photoUri)
        }
    }

    private fun recognizeText(bitmap: Bitmap, selectedLanguage: String){
        val image = InputImage.fromBitmap(bitmap, 0)

        val recognizer = when(selectedLanguage){
            "Latin" -> {
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            }
            "Chinese" -> {
                TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            }
            "Japanese" -> {
                TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())}
            "Korean" -> {
                TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            }
            "Devanagari" -> {
                TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
            }
            else -> {
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            }
        }

        recognizer.process(image).addOnSuccessListener { ocrText ->
            resultText.text = ocrText.text
            resultText.movementMethod = ScrollingMovementMethod()
            copyTextButton.visibility = Button.VISIBLE
            copyTextButton.setOnClickListener {
                val clipboard = ContextCompat.getSystemService(this,android.content.ClipboardManager::class.java)
                val clip = android.content.ClipData.newPlainText("recognized text", ocrText.text)
                clipboard?.setPrimaryClip(clip)
                Toast.makeText(this,"Text Copied!",Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Failed to recognize text: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}