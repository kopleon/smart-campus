/* Copyright 2022 Lin Yi. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================
*/

/** 本应用主要对 Tensorflow Lite Pose Estimation 示例项目的 MainActivity.kt
 *  文件进行了重写，示例项目中其余文件除了包名调整外基本无改动，原版权归
 *  The Tensorflow Authors 所有 */

package lyi.AIworkout.posemon

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.pm.PackageManager
import android.graphics.PointF
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.MediaController
import android.widget.Spinner
import android.widget.TextView
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import lyi.AIworkout.posemon.camera.CameraSource
import lyi.AIworkout.posemon.data.Camera
import lyi.AIworkout.posemon.data.Device
import lyi.AIworkout.posemon.data.Person
import lyi.AIworkout.posemon.ml.ModelType
import lyi.AIworkout.posemon.ml.MoveNet
import lyi.AIworkout.posemon.ml.PoseClassifier
import java.lang.System.currentTimeMillis
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2


fun calculateAngle(point1: Array<Double>, point2: Array<Double>, point3: Array<Double>): Double {
    val point1Array = point1.toDoubleArray()
    val point2Array = point2.toDoubleArray()
    val point3Array = point3.toDoubleArray()

    val radians = (atan2(point3Array[1] - point2Array[1], point3Array[0] - point2Array[0])
            - atan2(point1Array[1] - point2Array[1], point1Array[0] - point2Array[0]))

    var angle = abs(radians * 180 / PI)
    if (angle < 180) {
        return angle
    } else {
        angle = 360 - angle
        return angle
    }
}

fun pointFToArray(point: PointF?): Array<Double> {
    return arrayOf(point?.x!!.toDouble(), point?.y!!.toDouble())

}


var PlayingVideo = false
var wrongaction = false
var wrongaction1 = false
var doingaction = 0
var acttime = 0
@SuppressLint("SimpleDateFormat")
var start = false
var time = 0

class MainActivity : AppCompatActivity() {
    companion object {
        private const val FRAGMENT_DIALOG = "dialog"
    }

    /** 为视频画面创建一个 SurfaceView */
    private lateinit var surfaceView: SurfaceView

    /** 修改默认计算设备：CPU、GPU、NNAPI（AI加速器） */
    private var device = Device.CPU

    /** 修改默认摄像头：FRONT、BACK */
    private var selectedCamera = Camera.BACK


    /** 设置一个用来显示当前坐姿状态的 ImageView */
    //private lateinit var ivStatus: ImageView
    private lateinit var VideoFrame: FrameLayout
    private lateinit var showcaseimage: ImageView
    private lateinit var handsbacktick: ImageView
    private lateinit var handsfronttick: ImageView
    private lateinit var handsuptick: ImageView
    private lateinit var necktick: ImageView
    private lateinit var foottick: ImageView
    private lateinit var foot1tick: ImageView
    private lateinit var hiptick: ImageView
    private lateinit var smallimageshows: ImageButton
    private lateinit var hands_: TextView
    private lateinit var nowact: TextView
    private lateinit var actTimes: TextView
    private lateinit var spnCamera: Spinner
    private lateinit var closeimage: Button
    private lateinit var hand1: ImageButton
    private lateinit var hand2: ImageButton
    private lateinit var hand3: ImageButton
    private lateinit var back: Button
    private lateinit var showcaseVideo: VideoView
    private var cameraSource: CameraSource? = null
    private var isClassifyPose = true
    private lateinit var textView9: TextView
    private var lastTimerStamp: Int = 1
    private var timerCount: Int = 60
    private var mediaPlayer: MediaPlayer? = null

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                /** 得到用户相机授权后，程序开始运行 */
                openCamera()
            } else {
                /** 提示用户“未获得相机权限，应用无法运行” */
                ErrorDialog.newInstance(getString(R.string.tfe_pe_request_permission))
                    .show(supportFragmentManager, FRAGMENT_DIALOG)
            }
        }


    private var changeCameraListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p0: AdapterView<*>?, view: View?, direction: Int, id: Long) {
            changeCamera(direction)
        }

        override fun onNothingSelected(p0: AdapterView<*>?) {
            /** 如果用户未选择摄像头，使用默认摄像头进行拍摄 */
        }
    }

    private fun timerCountDown() {
        var nowTime = currentTimeMillis().toInt()
        if (lastTimerStamp == -1) {
            lastTimerStamp = nowTime
        }
        textView9.text = "${timerCount} 秒"
        var time = nowTime - lastTimerStamp;
        if (time >= 1000) {
            timerCount -= 1
            lastTimerStamp = nowTime
        }
    }




    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("MissingInflatedId", "ResourceType")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mediaPlayer = MediaPlayer.create(this, R.raw.correctfitness)


        /** 程序运行时保持屏幕常亮 */
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        //tvScore = findViewById(R.id.tvScore)
        /**button*/

        VideoFrame = findViewById(R.id.VideoFrame)
        textView9 = findViewById(R.id.textView9)




            @SuppressLint("SetTextI18n")




            hand1 = findViewById(R.id.handsback)
        hand1.setOnClickListener {
            if(!PlayingVideo) {
                hand1Fun = 1;
                selectedimage = 1;
                acttime = 0
                doingaction = 0
                smallimageshows.setImageResource(R.drawable.pushup)
                smallimageshows.visibility = View.VISIBLE
            }
        }


        hand2 = findViewById(R.id.handsup)
        hand2.setOnClickListener {
            if(!PlayingVideo) {
                hand1Fun = 2;
                selectedimage = 2;
                acttime = 0
                doingaction = 0
                smallimageshows.setImageResource(R.drawable.situp)
                smallimageshows.visibility = View.VISIBLE
            }
        }
        hand3 = findViewById(R.id.handsfront)
        hand3.setOnClickListener {
            if(!PlayingVideo) {
                hand1Fun = 3;
                selectedimage = 3;
                acttime = 0
                doingaction = 0
                smallimageshows.setImageResource(R.drawable.plack)
                smallimageshows.visibility = View.VISIBLE
            }
        }
        closeimage = findViewById(R.id.closeimage)
        closeimage.setOnClickListener{
            PlayingVideo = false
            showcaseimage.visibility = View.GONE
            //showcaseVideo.visibility = View.INVISIBLE
            VideoFrame.visibility = View.INVISIBLE
            closeimage.visibility = View.GONE
            smallimageshows.visibility = View.VISIBLE
        }
        smallimageshows = findViewById(R.id.smallimageshows)
        smallimageshows.setOnClickListener{
            when (selectedimage) {
                1 -> {
                    PlayingVideo = true
                    var packageName = "android.resource://"+ packageName +"/"+ R.raw.handsback
                    var uri = Uri.parse(packageName)
                    showcaseVideo.setVideoURI(uri)
                    showcaseVideo.start()
                }
                2 -> {
                    PlayingVideo = true
                    var packageName = "android.resource://"+ packageName +"/"+ R.raw.handsup
                    var uri = Uri.parse(packageName)
                    showcaseVideo.setVideoURI(uri)
                    showcaseVideo.start()
                }
                3 -> {
                    PlayingVideo = true
                    var packageName = "android.resource://"+ packageName +"/"+ R.raw.handsfront
                    var uri = Uri.parse(packageName)
                    showcaseVideo.setVideoURI(uri)
                    showcaseVideo.start()
                }
                4 -> {PlayingVideo = true
                    var packageName = "android.resource://"+ packageName +"/"+ R.raw.hip
                    var uri = Uri.parse(packageName)
                    showcaseVideo.setVideoURI(uri)
                    showcaseVideo.start()}
                5 -> {PlayingVideo = true
                    var packageName = "android.resource://"+ packageName +"/"+ R.raw.neck
                    var uri = Uri.parse(packageName)
                    showcaseVideo.setVideoURI(uri)
                    showcaseVideo.start()}
                6 -> {PlayingVideo = true
                    var packageName = "android.resource://"+ packageName +"/"+ R.raw.foot
                    var uri = Uri.parse(packageName)
                    showcaseVideo.setVideoURI(uri)
                    showcaseVideo.start()}
                7 -> {PlayingVideo = true
                    var packageName = "android.resource://"+ packageName +"/"+ R.raw.foot1
                    var uri = Uri.parse(packageName)
                    showcaseVideo.setVideoURI(uri)
                    showcaseVideo.start()}
            }
            //showcaseimage.visibility = View.VISIBLE
            val mediacontroller = MediaController(this)
            showcaseVideo.setMediaController(mediacontroller)
            closeimage.visibility = View.VISIBLE
            VideoFrame.visibility = View.VISIBLE
            //showcaseVideo.visibility = View.VISIBLE
            smallimageshows.visibility = View.GONE
        }
        /** 用来显示 Debug 信息 */
        //tvDebug = findViewById(R.id.tvDebug)

        /** 用来显示当前坐姿状态 */
        nowact = findViewById(R.id.now_act)
        actTimes = findViewById(R.id.act_time)
        back = findViewById(R.id.button7)
        hands_ = findViewById(R.id.hands_)
        hiptick = findViewById(R.id.hiptick)
        handsbacktick = findViewById(R.id.handsbacktick)
        handsfronttick = findViewById(R.id.handsfronttick)
        handsuptick = findViewById(R.id.handsuptick)
        necktick = findViewById(R.id.necktick)
        foottick = findViewById(R.id.foottick)
        foot1tick = findViewById(R.id.foot1tick)
        //tvFPS = findViewById(R.id.tvFps)
        //spnDevice = findViewById(R.id.spnDevice)
        spnCamera = findViewById(R.id.spnCamera)
        surfaceView = findViewById(R.id.surfaceView)
        showcaseimage = findViewById(R.id.showcaseimage)
        initSpinner()
        if (!isCameraPermissionGranted()) {
            requestPermission()
        }
    }


    override fun onStart() {
        super.onStart()
        openCamera()
    }

    override fun onResume() {
        cameraSource?.resume()
        super.onResume()
    }

    override fun onPause() {
        cameraSource?.close()
        cameraSource = null
        super.onPause()
    }
    fun reset(view:View){
        timerCount = 60
        lastTimerStamp = -1
        textView9.text = "$timerCount 秒"
        doingaction = 0
    }

    /** 检查相机权限是否有授权 */
    private fun isCameraPermissionGranted(): Boolean {
        return checkPermission(
            Manifest.permission.CAMERA,
            Process.myPid(),
            Process.myUid()
        ) == PackageManager.PERMISSION_GRANTED
    }
    var selectedimage = 0
    var hand1Fun = 0;
    var passingFpsCount = 0

    private fun openCamera() {
        /** 音频播放 */
        print("BYE")
        if (isCameraPermissionGranted()) {
            if (cameraSource == null) {
                cameraSource =
                    CameraSource(surfaceView, selectedCamera, object : CameraSource.CameraSourceListener {
                        override fun onFPSListener(fps: Int) {
                            /** 解释一下，tfe_pe_tv 的意思：tensorflow example、pose estimation、text view */
                            //tvDebug.text = getString(R.string.tfe_pe_tv_fps, fps)
                        }
                        var startAction = 0
                        var countAction:Int = 0
                        var countAction1:Int = 0
                        var startCountFps = 0;
                        var wrongfps = 0
                        var wrongfps1 = 0
                        var endCountFps = 0;
                        var startTime = 0;
                        var wrongmessage:String = ""
                        @SuppressLint("SetTextI18n")
                        override fun onDetectedInfo2(allData: MutableList<Person>?){
                            //push up
                            if(hand1Fun == 1) {
                                timerCountDown()
                                val lefthand = calculateAngle(pointFToArray(allData?.get(0)?.keyPoints?.get(5)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(7)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(9)?.coordinate))
                                val righthand = calculateAngle(pointFToArray(allData?.get(0)?.keyPoints?.get(6)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(8)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(10)?.coordinate))
                                val leftside = calculateAngle(pointFToArray(allData?.get(0)?.keyPoints?.get(11)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(13)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(15)?.coordinate))
                                val rightside = calculateAngle(pointFToArray(allData?.get(0)?.keyPoints?.get(12)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(14)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(16)?.coordinate))
                                if ((lefthand.toDouble() > 150 && leftside.toDouble() > 160.0) || (righthand.toDouble() > 150 && rightside.toDouble() > 160.0))  {
                                    if(passingFpsCount >= 1) {
                                        wrongmessage = ""
                                        start = true
                                        passingFpsCount = 0
                                    } else {
                                        passingFpsCount++

                                    }
                                }

                                if ((lefthand.toDouble() < 130 && leftside.toDouble() > 160.0 && start) || (righthand.toDouble() < 130 && rightside.toDouble() > 160.0 && start)) {
                                    if (passingFpsCount >= 1) {
                                        start = false
                                        doingaction++
                                        wrongaction = false
                                        wrongaction1 = false
                                        wrongfps = 0
                                        wrongfps1 = 0
                                        passingFpsCount = 0
                                        mediaPlayer?.start()
                                    } else {
                                        passingFpsCount++
                                    }
                                } else {
                                    countAction = 0
                                    //startTime = 0
                                }
                                if(acttime == 9){
                                    hand1.alpha = 0.5F
                                    handsbacktick.alpha = 1F
                                    hand1Fun = 2
                                    acttime = 0
                                    countAction = 0
                                    //smallimageshows.setImageResource(R.drawable.handup)
                                }
                                //var textcount = "%.2f".format(handtoback.toDouble()) + " Count: " + countAction + "ActTime: "+ acttime
                                //tvFPS.text = textcount
                                nowact.text = "正在做:push up"
                                var counter = ""+doingaction+" times"
                                actTimes.text = counter
                                nowact.alpha = 1F
                                actTimes.alpha = 1F
                            }

                            //situp
                            if(hand1Fun == 2) {
                                var leftbody = calculateAngle(pointFToArray(allData?.get(0)?.keyPoints?.get(5)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(11)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(13)?.coordinate))
                                var rightbody = calculateAngle(pointFToArray(allData?.get(0)?.keyPoints?.get(6)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(12)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(14)?.coordinate))
                                var leftleg = calculateAngle(pointFToArray(allData?.get(0)?.keyPoints?.get(11)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(13)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(15)?.coordinate))
                                var rightleg = calculateAngle(pointFToArray(allData?.get(0)?.keyPoints?.get(12)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(14)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(16)?.coordinate))
                                var lefthand = calculateAngle(pointFToArray(allData?.get(0)?.keyPoints?.get(5)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(7)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(9)?.coordinate))
                                var righthand = calculateAngle(pointFToArray(allData?.get(0)?.keyPoints?.get(6)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(8)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(10)?.coordinate))
                                if (((leftbody.toDouble() > 120.0 && leftleg.toDouble() < 90.0) || (rightbody.toDouble() > 120.0 && rightleg.toDouble() < 90.0)) && !start){
                                    println("passingFpsCount: start: "+start.toString())
                                    if(passingFpsCount >= 4) {
                                        wrongmessage = ""
                                        start = true
                                        timerCountDown()

                                        passingFpsCount = 0
                                    } else {
                                        passingFpsCount++
                                        println("passingFpsCount++")
                                        println("passingFpsCount: "+passingFpsCount)
                                    }
                                } else {
                                    //passingFpsCount = 0
                                }

                                if (((leftbody.toDouble() < 120 && leftleg.toDouble() < 90.0) || (rightbody.toDouble() < 120 && rightleg.toDouble() < 90.0)) && start) {
                                    if(passingFpsCount >= 4) {
                                        start = false
                                        doingaction++
                                        wrongaction = false
                                        wrongaction1 = false
                                        wrongfps = 0
                                        wrongfps1 = 0
                                        passingFpsCount = 0
                                    } else {
                                        passingFpsCount++
                                    }
                                } else {
                                    //passingFpsCount = 0
                                    countAction = 0
                                    //startTime = 0
                                }
                                if(acttime == 9) {
                                    hand1.alpha = 0.5F
                                    handsbacktick.alpha = 1F
                                    hand1Fun = 2
                                    acttime = 0
                                    countAction = 0
                                }
                                    //smallimageshows.setImageResource(R.drawable.handup)
                                //var textcount = "%.2f".format(lefthandup .toDouble()) + " | " +"%.2f".format(righthandup .toDouble())+" | "+"%.2f".format(handup .toDouble()) + " handdis: " + handdis
                                //tvFPS.text = textcount
                                nowact.text = "正在做:sit up"
                                var counter = ""+doingaction+" times"
                                actTimes.text = counter
                                nowact.alpha = 1F
                                actTimes.alpha = 1F
                                }

                            //handsfront
                            if(hand1Fun == 3) {
                                var lefthand = calculateAngle(pointFToArray(allData?.get(0)?.keyPoints?.get(5)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(7)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(9)?.coordinate))
                                var leftleg = calculateAngle(pointFToArray(allData?.get(0)?.keyPoints?.get(11)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(13)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(15)?.coordinate))
                                var righthand = calculateAngle(pointFToArray(allData?.get(0)?.keyPoints?.get(6)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(8)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(10)?.coordinate))
                                var rightleg = calculateAngle(pointFToArray(allData?.get(0)?.keyPoints?.get(12)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(14)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(16)?.coordinate))
                                if (lefthand.toDouble() < 120 && leftleg.toDouble() > 150 && righthand.toDouble() < 120 && rightleg.toDouble() > 150) {
                                    startCountFps++
                                    if (startCountFps >= 10 && startTime == 0) {
                                        time = 0
                                        startAction = 1
                                        startCountFps = 0
                                        timerCountDown()
                                    }
                                }
                                if (lefthand.toDouble() < 120 && leftleg.toDouble() > 150 && righthand.toDouble() < 120 && rightleg.toDouble() > 150 && startTime != 0 ) {
                                    start = false
                                    wrongaction = false
                                    wrongaction1 = false
                                    wrongfps = 0
                                    wrongfps1 = 0
                                    countAction = ((currentTimeMillis() / 1000L).toInt() - startTime)
                                } else {
                                    countAction = 0
                                    startTime = 0
                                }
                                if(acttime == 9){
                                    hand3.alpha = 0.5F
                                    handsfronttick.alpha = 1F
                                    hand1Fun = 4
                                    acttime = 0
                                    countAction = 0
                                    //smallimageshows.setImageResource(R.drawable.hip)
                                }
                                nowact.text = "正在做:Plank"
                                var counter = " "
                                actTimes.text = counter
                                nowact.alpha = 1F
                                actTimes.alpha = 1F
                            }


                        }


                        /** 对检测结果进行处理 */
                        override fun onDetectedInfo(
                            personScore: Float?,
                            poseLabels: List<Pair<String, Float>>?
                        ) {
                            return;
                            //tvScore.text = getString(R.string.tfe_pe_tv_score, personScore ?: 0f)

                        }
                    }).apply {
                        prepareCamera()
                    }
                isPoseClassifier()
                lifecycleScope.launch(Dispatchers.Main) {
                    cameraSource?.initCamera()
                }
            }
            createPoseEstimator()
        }
    }

    private fun isPoseClassifier() {
        cameraSource?.setClassifier(if (isClassifyPose) PoseClassifier.create(this) else null)
    }

    /** 初始化运算设备选项菜单（CPU、GPU、NNAPI） */
    private fun initSpinner() {
        ArrayAdapter.createFromResource(
            this,
            R.array.tfe_pe_device_name, android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

            //spnDevice.adapter = adapter
            //spnDevice.onItemSelectedListener = changeDeviceListener
        }

        ArrayAdapter.createFromResource(
            this,
            R.array.tfe_pe_camera_name, android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

            spnCamera.adapter = adapter
            spnCamera.onItemSelectedListener = changeCameraListener
        }
    }

    /** 在程序运行过程中切换运算设备 */
    private fun changeDevice(position: Int) {
        val targetDevice = when (position) {
            0 -> Device.CPU
            1 -> Device.GPU
            else -> Device.NNAPI
        }
        if (device == targetDevice) return
        device = targetDevice
        createPoseEstimator()
    }

    /** 在程序运行过程中切换摄像头 */
    private fun changeCamera(direaction: Int) {
        val targetCamera = when (direaction) {
            0 -> Camera.BACK
            else -> Camera.FRONT
        }
        if (selectedCamera == targetCamera) return
        selectedCamera = targetCamera

        cameraSource?.close()
        cameraSource = null
        openCamera()
    }

    private fun createPoseEstimator() {
        val poseDetector = MoveNet.create(this, device, ModelType.Thunder)
        poseDetector.let { detector ->
            cameraSource?.setDetector(detector)
        }
    }

    private fun requestPermission() {
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) -> {
                openCamera()
            }
            else -> {
                requestPermissionLauncher.launch(
                    Manifest.permission.CAMERA
                )
            }
        }
    }

    /** 显示报错信息 */
    class ErrorDialog : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
            AlertDialog.Builder(activity)
                .setMessage(requireArguments().getString(ARG_MESSAGE))
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    // pass
                }
                .create()

        companion object {

            @JvmStatic
            private val ARG_MESSAGE = "message"

            @JvmStatic
            fun newInstance(message: String): ErrorDialog = ErrorDialog().apply {
                arguments = Bundle().apply { putString(ARG_MESSAGE, message) }
            }
        }
    }
}
