package com.example.video_compress

import android.content.Context
import android.net.Uri
import android.util.Log
//import android.app.ProgressDialog
import android.widget.Toast
import com.otaliastudios.transcoder.Transcoder
import com.otaliastudios.transcoder.TranscoderListener
import com.otaliastudios.transcoder.source.TrimDataSource
//import com.otaliastudios.transcoder.source.UriDataSource
import com.otaliastudios.transcoder.strategy.DefaultAudioStrategy
import com.otaliastudios.transcoder.strategy.DefaultVideoStrategy
import com.otaliastudios.transcoder.strategy.RemoveTrackStrategy
import com.otaliastudios.transcoder.strategy.TrackStrategy
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.BinaryMessenger
//import com.otaliastudios.transcoder.internal.Logger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.PluginRegistry.Registrar
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.io.IOException
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONObject
import java.util.concurrent.Future
import android.media.MediaMetadataRetriever
import com.abedelazizshe.lightcompressorlibrary.CompressionListener
import com.abedelazizshe.lightcompressorlibrary.VideoCompressor
import com.abedelazizshe.lightcompressorlibrary.VideoQuality
import com.abedelazizshe.lightcompressorlibrary.config.Configuration
import com.abedelazizshe.lightcompressorlibrary.config.SaveLocation
import com.abedelazizshe.lightcompressorlibrary.config.SharedStorageConfiguration
import android.os.Handler
import android.os.Looper
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * VideoCompressPlugin
 */
class VideoCompressPlugin : MethodCallHandler, FlutterPlugin {


    private var _context: Context? = null
    private var _channel: MethodChannel? = null
    private val TAG = "VideoCompressPlugin"

    //    private val LOG = Logger(TAG)
    private var transcodeFuture: Future<Void>? = null
    var channelName = "video_compress"

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        val context = _context;
        val channel = _channel;

        if (context == null || channel == null) {
            Log.w(TAG, "Calling VideoCompress plugin before initialization")
            return
        }

        when (call.method) {
            "getByteThumbnail" -> {
                val path = call.argument<String>("path")
                val quality = call.argument<Int>("quality")!!
                val position = call.argument<Int>("position")!! // to long
                ThumbnailUtility(channelName).getByteThumbnail(
                    path!!,
                    quality,
                    position.toLong(),
                    result
                )
            }

            "getFileThumbnail" -> {
                val path = call.argument<String>("path")
                val quality = call.argument<Int>("quality")!!
                val position = call.argument<Int>("position")!! // to long
                ThumbnailUtility("video_compress").getFileThumbnail(
                    context, path!!, quality,
                    position.toLong(), result
                )
            }

            "getMediaInfo" -> {
                val path = call.argument<String>("path")
                result.success(
                    Utility(channelName).getMediaInfoJson(
                        context = context,
                        path = File(path!!),
                        mediaRetriever = MediaMetadataRetriever()
                    ).toString()
                )
            }

            "deleteAllCache" -> {
                result.success(Utility(channelName).deleteAllCache(context, result));
            }

            "setLogLevel" -> {
                val logLevel = call.argument<Int>("logLevel")!!
                Log.e("TranscodeFailed", "onFailed :::: ${logLevel}")
//                Logger.setLogLevel(logLevel)
                result.success(true);
            }

            "cancelCompression" -> {
                transcodeFuture?.cancel(true)
                result.success(false);
            }

            "compressVideo" -> {
                ///Sub Folder Create

                val storageFile = File(
                    Environment.getExternalStorageDirectory()
                        .toString() + "/" + Environment.DIRECTORY_MOVIES + "/video_compress"
                )
                if (!storageFile.exists()) {
                    storageFile.mkdirs()
                }


                val file = File(call.argument<String>("path")!!)
                val quality = call.argument<Int>("quality")!!
                val deleteOrigin = call.argument<Boolean>("deleteOrigin")!!
                val metaRetriever = android.media.MediaMetadataRetriever()
                metaRetriever.setDataSource(context!!, Uri.fromFile(file))
                val height =
                    metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                val width =
                    metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                Log.e("fail", "Height ::: ${height}")
                Log.e("fail", "Width ::: ${width}")

//
                val sourceUri: MutableList<Uri> = ArrayList()
                sourceUri.add(Uri.fromFile(file))
                val videoName: MutableList<String> = ArrayList()
                videoName.add("VID_${System.currentTimeMillis()}")

                val file_size = (file.length() / 1024).toString().toInt()
                Log.e("LL", "SIZE::" + file_size + "  PATH::" + file.absolutePath)
                Log.e("LL", "Quality $quality");


                val videoQuality = Utility("video_compress").getQuality(quality)

                val configuration = Configuration(

                    quality = videoQuality, false,
                    null, false, false, height!!.toDouble(), width!!.toDouble(),
                    videoName
                    )



                VideoCompressor.start(context, sourceUri, false,
                    sharedStorageConfiguration = SharedStorageConfiguration(
                        saveAt = SaveLocation.movies,
                        subFolderName = "video_compress"
                    ),
                    configureWith = configuration, listener = object : CompressionListener {
                        override fun onStart(i: Int) {
                        }

                        override fun onSuccess(i: Int, l: Long, s: String?) {
                            Log.e("is dir", "ON SUCCESSS:::" + s.toString())
//                            val videoFile = File(s)

                            val tempDir: String = context.getExternalFilesDir("video_compress")!!.absolutePath
                            val out = SimpleDateFormat("yyyy-MM-dd hh-mm-ss").format(Date())
                            val destPath: String =
                                tempDir + File.separator + "VID_" + out + file.hashCode() + ".mp4"

                            Log.e(TAG, "moveVideoFile: source path ::: $s", )
                            val sourceFile = File(s)
                            val destinationFile = File(destPath)

                            try {
                                // Check if destination directory exists, create it if not
                                val destinationDir = destinationFile.parentFile
                                if (!destinationDir.exists()) {
                                    destinationDir.mkdirs()
                                }

                                // Create input and output streams
                                val inputStream = FileInputStream(sourceFile)
                                val outputStream = FileOutputStream(destinationFile)

                                // Buffer for copying data
                                val buffer = ByteArray(1024)

                                var bytesRead: Int
                                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                    outputStream.write(buffer, 0, bytesRead)
                                }

                                // Close streams and delete source file (optional)
                                inputStream.close()
                                outputStream.close()
                                sourceFile.delete() // Be cautious with deletion

                                Log.e(TAG, "moveVideoFile: destPath ::: ${destinationFile.path}", )
                                // Handle success message or logic
                                Log.d("TAG", "Video file moved successfully!")
                            } catch (e: Exception) {
                                // Handle error message or logic
                                Log.e("TAG", "Error moving video file: ${e.message}")
                            }

                            val file = destinationFile
                            val file_size = (file.length() / 1024).toString().toInt()
                            Log.e(TAG, "onSuccess: Compressed File ::: ${file.path}")
                            Log.e("LL", "SIZE::" + file_size + "  PATH::" + file.absolutePath)
//                            Log.e("DataSource", "Json: ${ UriDataSource(context, Uri.parse(file.path))}")

//                                channel.invokeMethod("updateProgress", 100.00)
                            Handler(Looper.getMainLooper()).post {
                                channel.invokeMethod("updateProgress", 100.00)
                            }

//                                val retriever = android.media.MediaMetadataRetriever()
                            try {
                                metaRetriever.setDataSource(context!!, Uri.parse(file.path))
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Log.e("fail", "Try Exception ::: ${e.message}")

                            }

                            val json = Utility("video_compress").getMediaInfoJson(
                                context = context,
                                path = file,
                                mediaRetriever = metaRetriever,
                            )
//                                val durationStr = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
//                                val title = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: ""
//                                val author = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR) ?: ""
//                                val widthStr = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
//                                val heightStr = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
//                                val duration = java.lang.Long.parseLong(durationStr)
//                                var width = java.lang.Long.parseLong(widthStr)
//                                var height = java.lang.Long.parseLong(heightStr)
//                                val filesize = file.length()
////                                val orientation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
////                                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
////                                } else {
////                                    null
////                                }
////                                val ori = orientation?.toIntOrNull()
////                                if (ori != null && isLandscapeImage(ori)) {
////                                    val tmp = width
////                                    width = height
////                                    height = tmp
////                                }
//                            metaRetriever.release()
//                                val json = JSONObject()
//                                json.put("path", Uri.parse(file.absolutePath))
//                                json.put("title", title)
//                                json.put("author", author)
//                                json.put("width", width)
//                                json.put("height", height)
//                                json.put("duration", duration)
//                                json.put("filesize", filesize)
////                                if (ori != null) {
////                                    json.put("orientation", ori)
////                                }
//

                            Log.e("fail", "json value ::: ${json.toString()}")
                            json.put("isCancel", false)
                            result.success(json.toString())

                            if (deleteOrigin) {
                                File(file.path!!).delete()
                            }
                        }

                        override fun onFailure(i: Int, s: String) {
                            Log.e("fail", "Video Compress Failed ::: $s")
                            result.success(null)
                        }

                        override fun onProgress(i: Int, v: Float) {
                            Handler(Looper.getMainLooper()).post {
                                channel.invokeMethod("updateProgress", v)
                            }
                        }

                        override fun onCancelled(i: Int) {
                            Log.e("fail", "Video Compress Cancelled ::: ")
                            result.success(null)
                        }
                    }
                )


            }

//            "compressVideo" -> {
//                val path = call.argument<String>("path")!!
//                val quality = call.argument<Int>("quality")!!
//                val deleteOrigin = call.argument<Boolean>("deleteOrigin")!!
//                val startTime = call.argument<Int>("startTime")
//                val duration = call.argument<Int>("duration") q
//                val includeAudio = call.argument<Boolean>("includeAudio") ?: true
//                val frameRate = if (call.argument<Int>("frameRate")==null) 30 else call.argument<Int>("frameRate")
//
//                val tempDir: String = context.getExternalFilesDir("video_compress")!!.absolutePath
//                val out = SimpleDateFormat("yyyy-MM-dd hh-mm-ss").format(Date())
//                val destPath: String = tempDir + File.separator + "VID_" + out + path.hashCode() + ".mp4"
//
//                var videoTrackStrategy: TrackStrategy = DefaultVideoStrategy.atMost(340).build();
//                val audioTrackStrategy: TrackStrategy = DefaultAudioStrategy.builder().build()
//
//                when (quality) {
//
//                    0 -> {
//                        videoTrackStrategy = DefaultVideoStrategy.atMost(720).build()
//                    }
//
//                    1 -> {
//                        videoTrackStrategy = DefaultVideoStrategy.atMost(360).build()
//                    }
//                    2 -> {
//                        videoTrackStrategy = DefaultVideoStrategy.atMost(640).build()
//                    }
//                    3 -> {
//
//                        assert(value = frameRate != null)
//                        videoTrackStrategy = DefaultVideoStrategy.Builder()
//                            .keyFrameInterval(3f)
//                            .bitRate(1280 * 720 * 4.toLong())
//                            .frameRate(frameRate!!) // will be capped to the input frameRate
//                            .build()
//                    }
//                    4 -> {
//                        videoTrackStrategy = DefaultVideoStrategy.atMost(480, 640).build()
//                    }
//                    5 -> {
//                        videoTrackStrategy = DefaultVideoStrategy.atMost(540, 960).build()
//                    }
//                    6 -> {
//                        videoTrackStrategy = DefaultVideoStrategy.atMost(720, 1280).build()
//                    }
//                    7 -> {
//                        videoTrackStrategy = DefaultVideoStrategy.atMost(1080, 1920).build()
//                    }
//                }
//
//                audioTrackStrategy = if (includeAudio) {
//                    val sampleRate = DefaultAudioStrategy.SAMPLE_RATE_AS_INPUT
//                    val channels = DefaultAudioStrategy.CHANNELS_AS_INPUT
//
//                    DefaultAudioStrategy.builder()
//                        .channels(channels)
//                        .sampleRate(sampleRate)
//                        .build()
//
//                } else {
//                    RemoveTrackStrategy()
//                }
//
//                val dataSource = if (startTime != null || duration != null){
//                    val source = UriDataSource(context, Uri.parse(path))
//                    TrimDataSource(source, (1000 * 1000 * (startTime ?: 0)).toLong(), (1000 * 1000 * (duration ?: 0)).toLong())
//                }else{
//                    UriDataSource(context, Uri.parse(path))
//                }
//
//                Log.e("TranscodeFailed","Path is  :::: ${destPath}")
//                transcodeFuture = Transcoder.into(destPath!!)
//                    .addDataSource(dataSource)
//                    .setAudioTrackStrategy(audioTrackStrategy)
//                    .setVideoTrackStrategy(videoTrackStrategy)
//                    .setListener(object : TranscoderListener {
//                        override fun onTranscodeProgress(progress: Double) {
//                            channel.invokeMethod("updateProgress", progress * 100.0)
//                        }
//                        override fun onTranscodeCompleted(successCode: Int) {
//                            channel.invokeMethod("updateProgress", 100.00)
//                            val json = Utility(channelName).getMediaInfoJson(context!!, destPath)
//                            json.put("isCancel", false)
//                            result.success(json.toString())
//                            if (deleteOrigin) {
//                                File(path!!).delete()
//                            }
//                        }
//
//                        override fun onTranscodeCanceled() {
//                            result.success(null)
//                        }
//
//                        override fun onTranscodeFailed(exception: Throwable) {
//                            Log.e("TranscodeFailed","onFailed :::: ${exception.message}")
//                            result.success(null)
//                        }
//                    }).transcode()
//                Log.e("TranscodeFailed","transcodeFuture  :::: ${transcodeFuture}")
//            }
//            else -> {
//                result.notImplemented()
//            }
        }
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        init(binding.applicationContext, binding.binaryMessenger)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        _channel?.setMethodCallHandler(null)
        _context = null
        _channel = null
    }

    private fun init(context: Context, messenger: BinaryMessenger) {
        val channel = MethodChannel(messenger, channelName)
        channel.setMethodCallHandler(this)
        _context = context
        _channel = channel
    }

    companion object {
        private const val TAG = "video_compress"

        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val instance = VideoCompressPlugin()
            instance.init(registrar.context(), registrar.messenger())
        }
    }
}
                 