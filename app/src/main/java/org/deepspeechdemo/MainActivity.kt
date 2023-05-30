package org.deepspeechdemo

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.android.synthetic.main.activity_main.*
import org.mozilla.deepspeech.libdeepspeech.DeepSpeechModel
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean


class MainActivity : AppCompatActivity() {
    private var model: DeepSpeechModel? = null

    private var transcriptionThread: Thread? = null
    private var isRecording: AtomicBoolean = AtomicBoolean(false)

    private val TFLITE_MODEL_FILENAME = "deepspeech-0.9.2-models.tflite"
    private val SCORER_FILENAME = "deepspeech-0.9.2-models.scorer"

    var saveStorage = "" //저장된 파일 경로
    var saveData = "" //저장된 파일 내용


    private fun checkPermission() {
        val permission = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE)

        if ((checkSelfPermission(permission.get(0)) != PackageManager.PERMISSION_GRANTED)
            || (checkSelfPermission(permission.get(1)) != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this, permission, 3)
        }
    }

    private fun transcribe() {
        // We read from the recorder in chunks of 2048 shorts. With a model that expects its input
        // at 16000Hz, this corresponds to 2048/16000 = 0.128s or 128ms.
        val audioBufferSize = 2048
        val audioData = ShortArray(audioBufferSize)

        runOnUiThread { btnStartInference.text = "Stop Recording" }

        model?.let { model ->
            val streamContext = model.createStream()

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                model.sampleRate(),
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                audioBufferSize
            )
            recorder.startRecording()

            while (isRecording.get()) {
                recorder.read(audioData, 0, audioBufferSize)
                model.feedAudioContent(streamContext, audioData, audioData.size)
                val decoded = model.intermediateDecode(streamContext)
                runOnUiThread { transcription.text = decoded }
            }

            val decoded = model.finishStream(streamContext)

            runOnUiThread {
                btnStartInference.text = "Start Recording"
                transcription.text = decoded
                writeTextFile(decoded) // 텍스트 파일 저장
            }

            recorder.stop()
            recorder.release()
        }
    }

    private fun writeTextFile(data: String) {
        try {
            saveData = data //TODO 변수에 값 대입
            val currentTime : Long = System.currentTimeMillis() //TODO 현재시간 받아오기
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale("ko", "KR"))
            val nowTime = sdf.format(currentTime)
            val textFileName = "/$nowTime.txt" //TODO 파일 생성
            val storageDir =
                File(getExternalFilesDir(null).toString() + "/SaveStorage") //TODO 저장 경로
            //TODO 폴더 생성
            if (!storageDir.exists()) { //TODO 폴더 없을 경우
                storageDir.mkdir() //TODO 폴더 생성
            }

            val buf = BufferedWriter(
                FileWriter(
                    storageDir.toString() + textFileName,
                    false
                )
            ) //TODO 덮어쓰기 (FALSE)
//            buf.append("[$nowTime]\n[$saveData]") //TODO 날짜 쓰기
//            buf.newLine() //TODO 개행
            buf.write(saveData)
            buf.close()
            saveStorage = storageDir.toString() + textFileName //TODO 경로 저장 /storage 시작
            //saveStorage = String.valueOf(storageDir.toURI()+textFileName); //TODO 경로 저장 file:/ 시작
//            S_Preference.setString(application, "saveStorage", saveStorage) //TODO 프리퍼런스에 경로 저장한다
            Log.d("---", "---")
            Log.w("//===========//", "================================================")
            Log.d("","\n"+"[A_TextFile > 저장한 텍스트 파일 확인 실시]")
            Log.d("", "\n[경로 : $saveStorage]")
//            Log.d("", "\n[제목 : $nowTime]")
            Log.d("", "\n[내용 : $saveData]")
            Log.w("//===========//", "================================================")
            Log.d("---", "---")
            Toast.makeText(application, "텍스트 파일이 저장되었습니다", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createModel(): Boolean {
        val modelsPath = getExternalFilesDir(null).toString()
        val tfliteModelPath = "$modelsPath/$TFLITE_MODEL_FILENAME"
        val scorerPath = "$modelsPath/$SCORER_FILENAME"

        for (path in listOf(tfliteModelPath, scorerPath)) {
            if (!File(path).exists()) {
                status.append("Model creation failed: $path does not exist.\n")
                return false
            }
        }

        model = DeepSpeechModel(tfliteModelPath)
        model?.enableExternalScorer(scorerPath)

        return true
    }

    private fun startListening() {
        if (isRecording.compareAndSet(false, true)) {
            transcriptionThread = Thread(Runnable { transcribe() }, "Transcription Thread")
            transcriptionThread?.start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        checkPermission()

        // Create application data directory on the device
        val modelsPath = getExternalFilesDir(null).toString()

        status.text = "Ready. Copy model files to \"$modelsPath\" if running for the first time.\n"
    }

    private fun stopListening() {
        isRecording.set(false)
    }

    fun onRecordClick(v: View?) {
        if (model == null) {
            if (!createModel()) {
                return
            }
            status.append("Created model.\n")
        }

        if (isRecording.get()) {
            stopListening()
        } else {
            startListening()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (model != null) {
            model?.freeModel()
        }
    }
}
