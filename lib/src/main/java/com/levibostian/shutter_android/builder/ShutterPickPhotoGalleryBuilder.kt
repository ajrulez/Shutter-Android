package com.levibostian.shutter_android.builder

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.support.v4.content.FileProvider
import com.levibostian.shutter_android.Shutter
import com.levibostian.shutter_android.exception.ShutterUserCancelledOperation
import com.levibostian.shutter_android.vo.ShutterResult
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class ShutterPickPhotoGalleryBuilder(val companion: Shutter.ShutterCompanion): ShutterResultListener {

    private var fileName: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    private var directoryPath: File
    private var addPicToGallery: Boolean = false
    private var resultCallback: ShutterResultCallback? = null

    private var fileAbsolutePath: String? = null

    private val TAKE_PHOTO_REQUEST_CODE = 0

    init {
        directoryPath = getDirectoryPathInternalPrivateStorage()
    }

    /**
     * @param[name] Name to give for the file. By default, name is the date in the format: yyyyMMdd_HHmmss. *Note: filename cannot contain any characters not alphabetical or underscores.*
     *
     * @throws IllegalArgumentException If filename contains characters that are not alphabetical and underscores.
     */
    fun filename(name: String): ShutterPickPhotoGalleryBuilder {
        if (isValidFilename(name)) this.fileName = name
        return this
    }

    /**
     * Check if the given name is a valid filename. Good for checking user determined filenames before calling [fileName].
     *
     * @see fileName
     */
    fun isValidFilename(name: String): Boolean {
        return !name.isEmpty() && name.matches(Regex("\\w+"))
    }

    /**
     * If you wish to have your app use internal storage (compared to external storage) that is private to your app, use this method. Files saved to this directory *will* be deleted when the user uninstalls your app.
     *
     * Check out the Android [documentation about internal storage][https://developer.android.com/reference/android/content/Context.html#getFilesDir()] to learn more about this option.
     *
     * @see usePrivateAppExternalStorage
     */
    fun usePrivateAppInternalStorage(): ShutterPickPhotoGalleryBuilder {
        directoryPath = getDirectoryPathInternalPrivateStorage()
        return this
    }

    private fun getDirectoryPathInternalPrivateStorage(): File {
        return File("${companion.getContext()!!.filesDir.absolutePath}/Pictures/")
    }

    /**
     * If you wish to have your app use external storage (compared to internal storage) that is private to your app, use this method. Files saved to this directory *will* be deleted when the user uninstalls your app.
     *
     * Check out the Android [documentation about internal storage][https://developer.android.com/reference/android/content/Context.html#getExternalFilesDir(java.lang.String)] to learn more about this option.
     *
     * @see usePrivateAppInternalStorage
     */
    fun usePrivateAppExternalStorage(): ShutterPickPhotoGalleryBuilder {
        directoryPath = companion.getContext()!!.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return this
    }

    // for now, we are removing this. it requires read/write permissions and we do not want to have the user require that.
//        fun usePublicExternalStorage(): ShutterPickPhotoGalleryBuilder {
//            directoryPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
//            return this
//        }

    fun snap(callback: ShutterResultCallback): ShutterResultListener {
        this.resultCallback = callback

        if (!isValidFilename(fileName)) {
            callback.onError("You did not enter a valid file name for the photo. Name must contain only alphanumeric characters and underscores.", RuntimeException("User entered invalid filename: $fileName it can only contain alphanumeric characters and underscores."))
            return this
        }

        val getPhotoGalleryIntent = Intent(Intent.ACTION_PICK)
        getPhotoGalleryIntent.type = "image/*"
        if (getPhotoGalleryIntent.resolveActivity(companion.getContext()!!.packageManager) == null) {
            callback.onError("You do not have an app installed on your device view photos.", RuntimeException("You do not have an app installed on your device view photos."))
            return this
        }

        companion.activity?.startActivityForResult(getPhotoGalleryIntent, TAKE_PHOTO_REQUEST_CODE)
        companion.appCompatActivity?.startActivityForResult(getPhotoGalleryIntent, TAKE_PHOTO_REQUEST_CODE)
        companion.fragment?.startActivityForResult(getPhotoGalleryIntent, TAKE_PHOTO_REQUEST_CODE)
        companion.supportFragment?.startActivityForResult(getPhotoGalleryIntent, TAKE_PHOTO_REQUEST_CODE)

        return this
    }

    /**
     * In the Fragment or Activity that you provided to Shutter via it's constructor, call [onActivityResult] on the return value of [snap].
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?): Boolean {
        if (requestCode == TAKE_PHOTO_REQUEST_CODE) {
            if (resultCode != Activity.RESULT_OK) {
                resultCallback?.onError("You cancelled taking a photo.", ShutterUserCancelledOperation("User cancelled taking a photo."))
            }

            val contentUriPhoto = intent!!.data
            val photoInputStream = companion.getContext()!!.contentResolver.openInputStream(contentUriPhoto)

            if (android.os.Environment.getExternalStorageState() != android.os.Environment.MEDIA_MOUNTED) {
                resultCallback?.onError("Error getting image from gallery. Unmount for device external storage and try again.", RuntimeException("User has mounted their device storage: ${directoryPath.absolutePath} with filename: $fileName"))
                return true
            }

            var outputStream: OutputStream? = null
            try {
                val nameOfApp = companion.getContext()!!.packageName.split(".").last()
                directoryPath = File("${directoryPath.absolutePath}/$nameOfApp")
                directoryPath.mkdirs()

                val imageFile: File = File(directoryPath, fileName + ".jpg")

                if (!imageFile.createNewFile()) {
                    resultCallback?.onError("Error getting image from gallery.", RuntimeException("Error creating new image where image will save: ${directoryPath.absolutePath} with filename: $fileName"))
                    return true
                }

                fileAbsolutePath = imageFile.absolutePath

                outputStream = FileOutputStream(imageFile)

                val bytes = ByteArray(1024)
                var read = photoInputStream!!.read(bytes)
                while (read != -1) {
                    outputStream.write(bytes, 0, read)
                    read = photoInputStream.read(bytes)
                }
            } catch (e: IOException) {
                resultCallback?.onError("Error getting image from gallery.", e)
            } finally {
                try {
                    photoInputStream?.close()
                    // outputStream?.flush()
                    outputStream?.close()
                } catch (e: IOException) {
                    resultCallback?.onError("Error getting image from gallery.", e)
                }
            }

            resultCallback?.onComplete(ShutterResult(fileAbsolutePath))

            return true
        } else {
            return false
        }
    }

}