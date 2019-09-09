/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dab.dabmail

import android.Manifest
import android.annotation.TargetApi
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.Intent.getIntent
import android.content.Intent.getIntentOld
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.LayoutInflater
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import dab.dabmail.BodyPart.*
import java.io.ByteArrayOutputStream
import java.lang.Math.atan2
import java.lang.Math.toDegrees
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.pow

class PosenetActivity :
  Fragment(),
  ActivityCompat.OnRequestPermissionsResultCallback {

  /** List of body joints that should be connected.    */
  private val bodyJoints = listOf(
    Pair(LEFT_WRIST, LEFT_ELBOW),
    Pair(LEFT_ELBOW, LEFT_SHOULDER),
    Pair(LEFT_SHOULDER, RIGHT_SHOULDER),
    Pair(RIGHT_SHOULDER, RIGHT_ELBOW),
    Pair(RIGHT_ELBOW, RIGHT_WRIST),
    Pair(LEFT_SHOULDER, LEFT_HIP),
    Pair(LEFT_HIP, RIGHT_HIP),
    Pair(RIGHT_HIP, RIGHT_SHOULDER),
    Pair(LEFT_HIP, LEFT_KNEE),
    Pair(LEFT_KNEE, LEFT_ANKLE),
    Pair(RIGHT_HIP, RIGHT_KNEE),
    Pair(RIGHT_KNEE, RIGHT_ANKLE)
  )

  private var jointsToInclude = listOf(
          LEFT_WRIST,
          LEFT_ELBOW,
          RIGHT_WRIST,
          RIGHT_ELBOW
  )

  private var to_email: String? = ""
  private var from_email: String? = ""
  private var body_email: String? = ""

  /** Model input shape for images.   */
  private val MODEL_WIDTH = 257
  private val MODEL_HEIGHT = 257

  private val REQUEST_CAMERA_PERMISSION = 1

  /** Threshold for confidence score. */
  private val minConfidence = 0.5

  /** Radius of circle used to draw keypoints.  */
  private val circleRadius = 8.0f

  /** Paint class holds the style and color information to draw geometries,text and bitmaps. */
  private var paint = Paint()

  /** A shape for extracting frame data.   */
  private val PREVIEW_WIDTH = 640
  private val PREVIEW_HEIGHT = 480

  /** An object for the Posenet library.    */
  private lateinit var posenet: Posenet

  /** ID of the current [CameraDevice].   */
  private var cameraId: String? = null

  /** A [SurfaceView] for camera preview.   */
  private var surfaceView: SurfaceView? = null

  /** A [CameraCaptureSession] for camera preview.   */
  private var captureSession: CameraCaptureSession? = null

  /** A reference to the opened [CameraDevice].    */
  private var cameraDevice: CameraDevice? = null

  /** The [android.util.Size] of camera preview.  */
  private var previewSize: Size? = null

  /** The [android.util.Size.getWidth] of camera preview. */
  private var previewWidth = 0

  /** The [android.util.Size.getHeight] of camera preview.  */
  private var previewHeight = 0

  /** A counter to keep count of total frames.  */
  private var frameCounter = 0

  /** An IntArray to save image data in ARGB8888 format  */
  private lateinit var rgbBytes: IntArray

  /** A ByteArray to save image data in YUV format  */
  private var yuvBytes = arrayOfNulls<ByteArray>(3)

  /** An additional thread for running tasks that shouldn't block the UI.   */
  private var backgroundThread: HandlerThread? = null

  /** A [Handler] for running tasks in the background.    */
  private var backgroundHandler: Handler? = null

  /** An [ImageReader] that handles preview frame capture.   */
  private var imageReader: ImageReader? = null

  /** [CaptureRequest.Builder] for the camera preview   */
  private var previewRequestBuilder: CaptureRequest.Builder? = null

  /** [CaptureRequest] generated by [.previewRequestBuilder   */
  private var previewRequest: CaptureRequest? = null

  /** A [Semaphore] to prevent the app from exiting before closing the camera.    */
  private val cameraOpenCloseLock = Semaphore(1)

  /** Whether the current camera device supports Flash or not.    */
  private var flashSupported = false

  /** Orientation of the camera sensor.   */
  private var sensorOrientation: Int? = null

  /** Abstract interface to someone holding a display surface.    */
  private var surfaceHolder: SurfaceHolder? = null

  /** [CameraDevice.StateCallback] is called when [CameraDevice] changes its state.   */
  private val stateCallback = @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  object : CameraDevice.StateCallback() {

    override fun onOpened(cameraDevice: CameraDevice) {
      cameraOpenCloseLock.release()
      this@PosenetActivity.cameraDevice = cameraDevice
      createCameraPreviewSession()
    }

    override fun onDisconnected(cameraDevice: CameraDevice) {
      cameraOpenCloseLock.release()
      cameraDevice.close()
      this@PosenetActivity.cameraDevice = null
    }

    override fun onError(cameraDevice: CameraDevice, error: Int) {
      onDisconnected(cameraDevice)
      this@PosenetActivity.activity?.finish()
    }
  }

  /**
   * A [CameraCaptureSession.CaptureCallback] that handles events related to JPEG capture.
   */
  private val captureCallback = @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  object : CameraCaptureSession.CaptureCallback() {
    override fun onCaptureProgressed(
      session: CameraCaptureSession,
      request: CaptureRequest,
      partialResult: CaptureResult
    ) {
    }

    override fun onCaptureCompleted(
      session: CameraCaptureSession,
      request: CaptureRequest,
      result: TotalCaptureResult
    ) {
    }
  }

  /**
   * Shows a [Toast] on the UI thread.
   *
   * @param text The message to show
   */
  private fun showToast(text: String) {
    val activity = activity
    activity?.runOnUiThread { Toast.makeText(activity, text, Toast.LENGTH_SHORT).show() }
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? = inflater.inflate(R.layout.activity_posenet, container, false)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    surfaceView = view.findViewById(R.id.surfaceView)
    surfaceHolder = surfaceView!!.holder
  }

  override fun onResume() {
    super.onResume()
    startBackgroundThread()
  }

  override fun onStart() {
    super.onStart()
    Log.d(TAG, body_email)
    Log.d(TAG, to_email)
    Log.d(TAG, from_email)
    openCamera()
    posenet = Posenet(this.context!!)
  }

  override fun onPause() {
    closeCamera()
    stopBackgroundThread()
    super.onPause()
  }

  /**
   * Sets up member variables related to camera.
   */
  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private fun setUpCameraOutputs() {

    val activity = activity
    val manager = activity!!.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    try {
      for (cameraId in manager.cameraIdList) {
        val characteristics = manager.getCameraCharacteristics(cameraId)

        // We don't use a front facing camera in this sample.
        val cameraDirection = characteristics.get(CameraCharacteristics.LENS_FACING)
        if (cameraDirection != null &&
                cameraDirection == CameraCharacteristics.LENS_FACING_FRONT
        ) {
          continue
        }

        previewSize = Size(PREVIEW_WIDTH, PREVIEW_HEIGHT)

        this.imageReader = ImageReader.newInstance(
                PREVIEW_WIDTH, PREVIEW_HEIGHT,
                ImageFormat.YUV_420_888, /*maxImages*/ 2
        )

        sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!

        previewHeight = previewSize!!.height
        previewWidth = previewSize!!.width

        // Initialize the storage bitmaps once when the resolution is known.
        rgbBytes = IntArray(previewWidth * previewHeight)

        // Check if the flash is supported.
        flashSupported =
                characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true

        this.cameraId = cameraId

        // We've found a viable camera and finished setting up member variables,
        // so we don't need to iterate through other available cameras.
        return
      }
    } catch (e: NullPointerException) {
      // Currently an NPE is thrown when the Camera2API is used but not supported on the
      // device this code runs.
    }
  }

  /**
   * Opens the camera specified by [PosenetActivity.cameraId].
   */
  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private fun openCamera() {
    val permissionCamera = ContextCompat.checkSelfPermission(activity!!, Manifest.permission.CAMERA)
    if (permissionCamera == PackageManager.PERMISSION_GRANTED) {
      setUpCameraOutputs()
      val manager = activity!!.getSystemService(Context.CAMERA_SERVICE) as CameraManager
      try {
        // Wait for camera to open - 2.5 seconds is sufficient
        if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
          throw RuntimeException("Time out waiting to lock camera opening.")
        }
        manager.openCamera(cameraId!!, stateCallback, backgroundHandler)
      } catch (e: InterruptedException) {
        throw RuntimeException("Interrupted while trying to lock camera opening.", e)
      }
    }
  }

  /**
   * Closes the current [CameraDevice].
   */
  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private fun closeCamera() {
    if (captureSession == null) {
      return
    }

    try {
      cameraOpenCloseLock.acquire()
      captureSession!!.close()
      captureSession = null
      cameraDevice!!.close()
      cameraDevice = null
      imageReader!!.close()
      imageReader = null
    } catch (e: InterruptedException) {
      throw RuntimeException("Interrupted while trying to lock camera closing.", e)
    } finally {
      cameraOpenCloseLock.release()
    }
  }

  /**
   * Starts a background thread and its [Handler].
   */
  private fun startBackgroundThread() {
    backgroundThread = HandlerThread("imageAvailableListener").also { it.start() }
    backgroundHandler = Handler(backgroundThread!!.looper)
  }

  /**
   * Stops the background thread and its [Handler].
   */
  @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
  private fun stopBackgroundThread() {
    backgroundThread?.quitSafely()
    try {
      backgroundThread?.join()
      backgroundThread = null
      backgroundHandler = null
    } catch (e: InterruptedException) {
      Log.e(TAG, e.toString())
    }
  }

  /** Fill the yuvBytes with data from image planes.   */
  @TargetApi(Build.VERSION_CODES.KITKAT)
  private fun fillBytes(planes: Array<Image.Plane>, yuvBytes: Array<ByteArray?>) {
    // Row stride is the total number of bytes occupied in memory by a row of an image.
    // Because of the variable row stride it's not possible to know in
    // advance the actual necessary dimensions of the yuv planes.
    for (i in planes.indices) {
      val buffer = planes[i].buffer
      if (yuvBytes[i] == null) {
        yuvBytes[i] = ByteArray(buffer.capacity())
      }
      buffer.get(yuvBytes[i]!!)
    }
  }

  /** A [OnImageAvailableListener] to receive frames as they are available.  */
  private var imageAvailableListener = @TargetApi(Build.VERSION_CODES.KITKAT)
  object : OnImageAvailableListener {
    override fun onImageAvailable(imageReader: ImageReader) {
      // We need wait until we have some size from onPreviewSizeChosen
      if (previewWidth == 0 || previewHeight == 0) {
        return
      }

      val image = imageReader.acquireLatestImage() ?: return
      fillBytes(image.planes, yuvBytes)

      ImageUtils.convertYUV420ToARGB8888(
        yuvBytes[0]!!,
        yuvBytes[1]!!,
        yuvBytes[2]!!,
        previewWidth,
        previewHeight,
        /*yRowStride=*/ image.planes[0].rowStride,
        /*uvRowStride=*/ image.planes[1].rowStride,
        /*uvPixelStride=*/ image.planes[1].pixelStride,
        rgbBytes
      )

      // Create bitmap from int array
      val imageBitmap = Bitmap.createBitmap(
        rgbBytes, previewWidth, previewHeight,
        Bitmap.Config.ARGB_8888
      )

      // Create rotated version for portrait display
      val rotateMatrix = Matrix()
      rotateMatrix.postRotate(90.0f)

      val rotatedBitmap = Bitmap.createBitmap(
        imageBitmap, 0, 0, previewWidth, previewHeight,
        rotateMatrix, true
      )
      image.close()

      // Process an image for analysis in every 3 frames.
      frameCounter = (frameCounter + 1) % 3
      if (frameCounter == 0) {
        processImage(rotatedBitmap)
      }
    }
  }

  /** Crop Bitmap to maintain aspect ratio of model input.   */
  private fun cropBitmap(bitmap: Bitmap): Bitmap {
    // Rotated bitmap has previewWidth as its height and previewHeight as width.
    val previewRatio = previewWidth.toFloat() / previewHeight
    val modelInputRatio = MODEL_HEIGHT.toFloat() / MODEL_WIDTH
    var croppedBitmap = bitmap

    // Acceptable difference between the modelInputRatio and previewRatio to skip cropping.
    val maxDifference = 1.0f.pow(-5)

    // Checks if the previewing bitmap has similar aspect ratio as the required model input.
    when {
      abs(modelInputRatio - previewRatio) < maxDifference -> return croppedBitmap
      modelInputRatio > previewRatio -> {
        // New image is taller so we are height constrained.
        val cropHeight = previewHeight - (previewWidth.toFloat() / modelInputRatio)
        croppedBitmap = Bitmap.createBitmap(
          bitmap,
          0,
          (cropHeight / 2).toInt(),
          previewHeight,
          (previewWidth - (cropHeight / 2)).toInt()
        )
      }
      else -> {
        val cropWidth = previewWidth - (previewHeight.toFloat() * modelInputRatio)
        croppedBitmap = Bitmap.createBitmap(
          bitmap,
          (cropWidth / 2).toInt(),
          0,
          (previewHeight - (cropWidth / 2)).toInt(),
          previewWidth
        )
      }
    }
    return croppedBitmap
  }

  /** Set the paint color and size.    */
  private fun setPaint() {
    paint.color = Color.RED
    paint.textSize = 80.0f
    paint.strokeWidth = 8.0f
  }

  /** Draw bitmap on Canvas.   */
  private fun draw(canvas: Canvas, person: Person, bitmap: Bitmap) {
    val screenWidth: Int = canvas.width
    val screenHeight: Int = canvas.height
    setPaint()
    canvas.drawBitmap(
      bitmap,
      Rect(0, 0, previewHeight, previewWidth),
      Rect(0, 0, screenWidth, screenHeight),
      paint
    )

    val widthRatio = screenWidth.toFloat() / MODEL_WIDTH
    val heightRatio = screenHeight.toFloat() / MODEL_HEIGHT

    // Draw key points over the image.
    for (keyPoint in person.keyPoints) {
      if (keyPoint.score > minConfidence) {
        val position = keyPoint.position
        val adjustedX: Float = position.x.toFloat() * widthRatio
        val adjustedY: Float = position.y.toFloat() * heightRatio
        canvas.drawCircle(adjustedX, adjustedY, circleRadius, paint)
      }
    }

    for (line in bodyJoints) {
      if (
        (person.keyPoints[line.first.ordinal].score > minConfidence) and
        (person.keyPoints[line.second.ordinal].score > minConfidence)
      ) {
        canvas.drawLine(
          person.keyPoints[line.first.ordinal].position.x.toFloat() * widthRatio,
          person.keyPoints[line.first.ordinal].position.y.toFloat() * heightRatio,
          person.keyPoints[line.second.ordinal].position.x.toFloat() * widthRatio,
          person.keyPoints[line.second.ordinal].position.y.toFloat() * heightRatio,
          paint
        )
      }
    }

    // Draw confidence score of a person.
    val scoreMessage = "SCORE: " + "%.2f".format(person.score)
    canvas.drawText(
      scoreMessage,
      (15.0f * widthRatio),
      (243.0f * heightRatio),
      paint
    )

    // Draw!
    surfaceHolder!!.unlockCanvasAndPost(canvas)
  }

  /** Process image using Posenet library.   */
  private fun processImage(bitmap: Bitmap) {
    // Crop bitmap.
    val croppedBitmap = cropBitmap(bitmap)

    // Created scaled version of bitmap for model input.
    val scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, MODEL_WIDTH, MODEL_HEIGHT, true)

    // Perform inference.
    val person = posenet.estimateSinglePose(scaledBitmap)

    /*
  var bodyPart: BodyPart = BodyPart.NOSE
  var position: Position = Position()
  var score: Float = 0.0f
     */

    var foundJoints = HashMap<BodyPart, KeyPoint>()
    foundJoints.clear()
    for (kp in person.keyPoints) {
      if (kp.score > minConfidence && kp.bodyPart in jointsToInclude) {
        Log.e(TAG, "FOUND BODY PART: " + kp.bodyPart)
        foundJoints.put(kp.bodyPart, kp)
      }
    }

    if (foundJoints.size == jointsToInclude.size) {
      var leftElbowKP = foundJoints.get(BodyPart.LEFT_ELBOW)
      var leftWristKP = foundJoints.get(BodyPart.LEFT_WRIST)
      var rightElbowKP = foundJoints.get(BodyPart.RIGHT_ELBOW)
      var rightWristKP = foundJoints.get(BodyPart.RIGHT_WRIST)

      var leftArmAngle = toDegrees(atan2((leftWristKP!!.position.y - leftElbowKP!!.position.y).toDouble(), (leftWristKP.position.x - leftElbowKP.position.x).toDouble()))
      var rightArmAngle = toDegrees(Math.atan2((rightWristKP!!.position.y - rightElbowKP!!.position.y).toDouble(), (rightWristKP.position.x - rightElbowKP.position.x).toDouble()))

      var leftArmSector = getSectorFromAngle(leftArmAngle)
      var rightArmSector = getSectorFromAngle(rightArmAngle)
      Log.e(TAG, "Left Arm Angle: " + leftArmAngle)
      Log.e(TAG, "Right Arm Angle: " + rightArmAngle)
      Log.e(TAG, "Left Arm Sector: " + leftArmSector)
      Log.e(TAG, "Right Arm Sector: " + rightArmSector)
      Log.e(TAG, "FOUND ALL JOINTS")

      if ((leftArmSector == 1 && rightArmSector == 1) || (leftArmSector == 2 && rightArmSector == 2)) {
        var subject = "Get dabbed on"

        val out = ByteArrayOutputStream()
        var jpegImg = bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
        val intent = Intent(context,ConfettiActivity::class.java)
        intent.putExtra("to_email", to_email)
        startActivity(intent)

      }
    }

    val canvas: Canvas = surfaceHolder!!.lockCanvas()
    draw(canvas, person, bitmap)
  }

  private fun getSectorFromAngle(angle: Double) : Int {
    assert(-360 <= angle && angle <= 360)
    if (angle > 0) {
      if (angle < 90) {
        return 4
      }
      else if (angle >= 90 && angle < 180) {
        return 3
      }
      else if (angle >= 180 && angle < 270) {
        return 2
      }
      else {
        return 1
      }
    }
    else {
      if (angle > -90) {
        return 1
      }
      else if (angle <= -90 && angle > -180) {
        return 2
      }
      else if (angle <= -180 && angle > -270) {
        return 3
      }
      else {
        return 4
      }
    }
  }
  /**
   * Creates a new [CameraCaptureSession] for camera preview.
   */
  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private fun createCameraPreviewSession() {
    // We capture images from preview in YUV format.
    imageReader = ImageReader.newInstance(
      previewSize!!.width, previewSize!!.height, ImageFormat.YUV_420_888, 2
    )
    imageReader!!.setOnImageAvailableListener(imageAvailableListener, backgroundHandler)

    // This is the surface we need to record images for processing.
    val recordingSurface = imageReader!!.surface

    // We set up a CaptureRequest.Builder with the output Surface.
    previewRequestBuilder = cameraDevice!!.createCaptureRequest(
      CameraDevice.TEMPLATE_PREVIEW
    )
    previewRequestBuilder!!.addTarget(recordingSurface)

    // Here, we create a CameraCaptureSession for camera preview.
    cameraDevice!!.createCaptureSession(
      listOf(recordingSurface),
      object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
          // The camera is already closed
          if (cameraDevice == null) return

          // When the session is ready, we start displaying the preview.
          captureSession = cameraCaptureSession
          try {
            // Auto focus should be continuous for camera preview.
            previewRequestBuilder!!.set(
              CaptureRequest.CONTROL_AF_MODE,
              CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
            // Flash is automatically enabled when necessary.
            setAutoFlash(previewRequestBuilder!!)

            // Finally, we start displaying the camera preview.
            previewRequest = previewRequestBuilder!!.build()
            captureSession!!.setRepeatingRequest(
              previewRequest!!,
              captureCallback, backgroundHandler
            )
          } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
          }
        }

        override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
          showToast("Failed")
        }
      },
      null
    )
  }

  public fun setEmailArguments(to_email: String?, from_email: String?, body_email: String?) {
    this.to_email = to_email
    this.from_email = from_email
    this.body_email = body_email
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private fun setAutoFlash(requestBuilder: CaptureRequest.Builder) {
    if (flashSupported) {
      requestBuilder.set(
        CaptureRequest.CONTROL_AE_MODE,
        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
      )
    }
  }

  companion object {
    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private val ORIENTATIONS = SparseIntArray()
    private val FRAGMENT_DIALOG = "dialog"

    init {
      ORIENTATIONS.append(Surface.ROTATION_0, 90)
      ORIENTATIONS.append(Surface.ROTATION_90, 0)
      ORIENTATIONS.append(Surface.ROTATION_180, 270)
      ORIENTATIONS.append(Surface.ROTATION_270, 180)
    }

    /**
     * Tag for the [Log].
     */
    private const val TAG = "PosenetActivity"
  }
}
