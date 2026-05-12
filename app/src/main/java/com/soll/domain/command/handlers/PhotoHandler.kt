package com.soll.domain.command.handlers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.ContextCompat
import com.soll.data.api.model.Message
import com.soll.data.repository.TelegramRepository
import com.soll.domain.command.CommandHandler
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PhotoHandler(
    context: Context,
    telegramRepository: TelegramRepository
) : CommandHandler(context, telegramRepository) {

    override val command = "photo"
    override val description = "Сделать фото: /photo [передняя|задняя]"

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    override suspend fun execute(message: Message, args: String?) {
        if (!hasPermission()) {
            reply(message, "Нет разрешения на камеру. Выдайте разрешение камеры в настройках приложения.")
            return
        }

        val useFrontCamera = args?.trim()?.lowercase() in setOf("front", "передняя", "фронтальная", "selfie")
        val cameraId = getCameraId(useFrontCamera)

        if (cameraId == null) {
            reply(message, "❌ ${if (useFrontCamera) "Фронтальная" else "Основная"} камера не найдена.")
            return
        }

        reply(message, "📷 Делаю фото через ${if (useFrontCamera) "фронтальную" else "основную"} камеру...")

        try {
            val photoFile = takePhoto(cameraId)

            if (photoFile != null && photoFile.exists()) {
                // Send photo via Telegram
                telegramRepository.sendPhoto(
                    chatId = message.chat.id,
                    file = photoFile,
                    caption = "Фото сделано ${SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date())}"
                )

                // Clean up temp file
                photoFile.delete()
            } else {
                reply(message, "❌ Не удалось сделать фото.")
            }

        } catch (e: Exception) {
            Timber.e(e, "Error taking photo")
            reply(message, "❌ Ошибка при съемке фото: ${e.message}")
        }
    }

    private fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getCameraId(front: Boolean): String? {
        val facing = if (front) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }

        return cameraManager.cameraIdList.find { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            characteristics.get(CameraCharacteristics.LENS_FACING) == facing
        }
    }

    @Suppress("MissingPermission")
    private suspend fun takePhoto(cameraId: String): File? {
        val handlerThread = HandlerThread("CameraThread").apply { start() }
        val handler = Handler(handlerThread.looper)

        try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: throw IllegalStateException("Не удалось получить конфигурацию камеры")

            // Get largest available size
            val sizes = map.getOutputSizes(ImageFormat.JPEG)
            val size = sizes.maxByOrNull { it.width * it.height }
                ?: throw IllegalStateException("Нет доступных размеров изображения")

            val imageReader = ImageReader.newInstance(
                size.width,
                size.height,
                ImageFormat.JPEG,
                1
            )

            val camera = openCamera(cameraId, handler)
            val captureSession = createCaptureSession(camera, imageReader, handler)
            val imageBytes = captureImage(camera, captureSession, imageReader, handler)

            camera.close()

            if (imageBytes != null) {
                val photoFile = File(
                    context.cacheDir,
                    "photo_${dateFormat.format(Date())}.jpg"
                )
                FileOutputStream(photoFile).use { fos ->
                    fos.write(imageBytes)
                }
                return photoFile
            }

            return null
        } finally {
            handlerThread.quitSafely()
        }
    }

    @Suppress("MissingPermission")
    private suspend fun openCamera(cameraId: String, handler: Handler): CameraDevice {
        return suspendCancellableCoroutine { continuation ->
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    continuation.resume(camera)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    continuation.resumeWithException(IllegalStateException("Камера отключена"))
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    continuation.resumeWithException(IllegalStateException("Ошибка камеры: $error"))
                }
            }, handler)
        }
    }

    private suspend fun createCaptureSession(
        camera: CameraDevice,
        imageReader: ImageReader,
        handler: Handler
    ): CameraCaptureSession {
        return suspendCancellableCoroutine { continuation ->
            @Suppress("DEPRECATION")
            camera.createCaptureSession(
                listOf(imageReader.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        continuation.resume(session)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        continuation.resumeWithException(IllegalStateException("Не удалось настроить сессию камеры"))
                    }
                },
                handler
            )
        }
    }

    private suspend fun captureImage(
        camera: CameraDevice,
        session: CameraCaptureSession,
        imageReader: ImageReader,
        handler: Handler
    ): ByteArray? {
        return suspendCancellableCoroutine { continuation ->
            val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(imageReader.surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }

            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    image.close()
                    continuation.resume(bytes)
                } else {
                    continuation.resume(null)
                }
            }, handler)

            session.capture(captureRequest.build(), null, handler)
        }
    }
}
