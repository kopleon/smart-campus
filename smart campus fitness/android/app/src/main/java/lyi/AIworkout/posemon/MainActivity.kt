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
import android.content.Intent
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
import java.text.SimpleDateFormat
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlinx.coroutines.*


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


var doingdirection = "左"
var handsfrontcantick = false
var PlayingVideo = false
var doneagroup = false
var wrongaction = false
var wrongaction1 = false
var doingaction = 0
var rightneck = 0
var leftneck = 0
var acttime = 0
var countdoingaction = false
val time1 = Calendar.getInstance().time
@SuppressLint("SimpleDateFormat")
val formatter = SimpleDateFormat("yyyy-MM-dd")
val currenttime = formatter.format(time1)
var start = false
var time = 0
var lastTimerStamp = -1
var timerCount = 60

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

    /** 定义几个计数器 */
    private var forwardheadCounter = 0
    private var crosslegCounter = 0
    private var standardCounter = 0
    private var missingCounter = 0
    private var lastTimeStartUnixTime = 0;

    /** 定义一个历史姿态寄存器 */
    private var poseRegister = "standard"

    /** 设置一个用来显示 Debug 信息的 TextView */
    //private lateinit var tvDebug: TextView

    /** 设置一个用来显示当前坐姿状态的 ImageView */
    //private lateinit var ivStatus: ImageView
    private lateinit var VideoFrame:FrameLayout
    private lateinit var showcaseimage: ImageView
    private lateinit var handsbacktick: ImageView
    private lateinit var handsfronttick: ImageView
    private lateinit var handsuptick: ImageView
    private lateinit var necktick: ImageView
    private lateinit var foottick: ImageView
    private lateinit var foot1tick: ImageView
    private lateinit var hiptick: ImageView
    private lateinit var smallimageshows: ImageButton
    private lateinit var hip: ImageButton
    private lateinit var foot1: ImageButton
    private lateinit var foot: ImageButton
    private lateinit var neck: ImageButton
    private lateinit var hands_: TextView
    private lateinit var hip_: TextView
    private lateinit var neck_:TextView
    private lateinit var foot_:TextView
    private lateinit var nowact: TextView
    private lateinit var actTimes: TextView
    private lateinit var tvFPS: TextView
    private lateinit var tvScore: TextView
    //private lateinit var spnDevice: Spinner
    private lateinit var spnCamera: Spinner
    private lateinit var closeimage: Button
    private lateinit var hand1: ImageButton
    private lateinit var hand2: ImageButton
    private lateinit var hand3: ImageButton
    private lateinit var back: Button
    private lateinit var handup: Button
    private lateinit var showcaseVideo: VideoView
    private var cameraSource: CameraSource? = null
    private var isClassifyPose = true
    private lateinit var textView9:TextView
    private var lastTimerStamp: Long = -1L
    private var timerCount: Int = 60  // or whatever default value

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

    private var changeDeviceListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            changeDevice(position)
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
            /** 如果用户未选择运算设备，使用默认设备进行计算 */
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
        val nowTime = System.currentTimeMillis()
        if (lastTimerStamp == -1L) {
            lastTimerStamp = nowTime
        }
        textView9.text = "$timerCount 秒"
        val time = nowTime - lastTimerStamp
        if (time >= 1000) {
            timerCount -= 1
            lastTimerStamp = nowTime
        }
    }
    private fun resetTimer() {
        timerCount = 10  // Or your desired starting value
        lastTimerStamp = -1L
        textView9.text = "$timerCount 秒"
    }


        @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("MissingInflatedId", "ResourceType")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
        /**handup = findViewById(R.id.handup)
        handup.setOnClickListener{
            var te = 1
            acttime = 0
            if (hand1Fun != 5 && te == 1){
                hand1Fun = 5
                te = 0
            }
            if (hand1Fun != 4 && te == 1){
                hand1Fun = 4
                te = 0
            }
        }*/
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
        val crosslegPlayer = MediaPlayer.create(this, R.raw.crossleg)
        //val forwardheadPlayer = MediaPlayer.create(this, R.raw.forwardhead)
        //val standardPlayer = MediaPlayer.create(this, R.raw.standard)
        val incorrectsound = MediaPlayer.create(this, R.raw.incorrect)
        val standardPlayer = MediaPlayer.create(this, R.raw.correct)
        val doneAGroup = MediaPlayer.create(this, R.raw.doneagroup)
        val doneAll = MediaPlayer.create(this, R.raw.doneall)
        val hand1fun1wrong = MediaPlayer.create(this, R.raw.hand1fun1wrong)
        val hand1fun1wrong1 = MediaPlayer.create(this, R.raw.hand1fun1wrong1)
        val hand1fun2wrong = MediaPlayer.create(this, R.raw.hand1fun2wrong)
        val hand1fun2wrong1 = MediaPlayer.create(this, R.raw.hand1fun2wrong1)
        var crosslegPlayerFlag = true
        var forwardheadPlayerFlag = true
        var standardPlayerFlag = true
        var startCount = false
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
                        var armdis:Float = 0F;
                        var armparline:Float = 0F;
                        var armheight = 0
                        var handdis:Float = 0F
                        var handparline:Float = 0F
                        var handheight = 0
                        var handheight1 = 0
                        var armheight1 = 0
                        var wrongmessage:String = ""
                        var act1 = 0
                        var act2 = 0
                        var act3 = 0
                        var act4 = 0
                        //var acttime = 0
                        var requireact = 0
                        var requireact2 = 0
                        var requireact3 = 0
                        var requireact4 = 0
                        var pairact = 0
                        var counter = 0
                        @SuppressLint("SetTextI18n")
                        override fun onDetectedInfo2(allData: MutableList<Person>?){
                            //push up
                            if(hand1Fun == 1) {
                                var lefthand = calculateAngle(pointFToArray(allData?.get(0)?.keyPoints?.get(5)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(7)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(9)?.coordinate))
                                var righthand = calculateAngle(pointFToArray(allData?.get(0)?.keyPoints?.get(6)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(8)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(10)?.coordinate))
                                var leftside = calculateAngle(pointFToArray(allData?.get(0)?.keyPoints?.get(11)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(13)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(15)?.coordinate))
                                var rightside = calculateAngle(pointFToArray(allData?.get(0)?.keyPoints?.get(12)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(14)?.coordinate),pointFToArray(allData?.get(0)?.keyPoints?.get(16)?.coordinate))
                                if ((lefthand.toDouble() > 150 && leftside.toDouble() > 160.0) || (righthand.toDouble() > 150 && rightside.toDouble() > 160.0))  {
                                    if(passingFpsCount >= 1) {
                                        wrongmessage = ""
                                        start = true
                                        timerCountDown()

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
                            /**if (hand1Fun == 4){
                                if((lefthandup.toDouble() > 30 && lefthandup.toDouble() < 140)&&(righthandup.toDouble() < 150)){
                                    startCountFps++;

                                    if (startCountFps >= 10 && startTime == 0) {
                                        startTime = (currentTimeMillis() / 1000L).toInt()
                                        startAction = 1;
                                        startCountFps = 0;
                                    }
                                }
                                if (startTime != 0 && (lefthandup.toDouble() > 30 && lefthandup.toDouble() < 140)&&(righthandup.toDouble() < 120)) {
                                    startTime = 0
                                    countAction =
                                        (currentTimeMillis() / 1000L).toInt() - startTime
                                    if(countAction != 0){
                                        countAction = 0
                                        counter++
                                    }

                                    if (counter >= 10) {
                                        acttime++
                                        countAction = 0
                                        startTime = 0
                                        counter = 0
                                    }
                                }else{
                                    startTime = 0
                                }
                                if(acttime == requireact4){
                                    hand1Fun = 6
                                    acttime = 0
                                    countAction = 0
                                    counter = 0
                                }
                                nowact.text = "正在做：手後曲拉右手"
                                var counter = ""+acttime+"組 "+counter+"秒/10秒"
                                actTimes.text = counter
                                nowact.alpha = 1F
                                actTimes.alpha = 1F
                                //var textcount = "%.2f".format(lefthandup .toDouble()) + " | " +"%.2f".format(righthandup .toDouble())
                                //tvFPS.text = textcount
                            }
                            if (hand1Fun == 5){
                                if((righthandup.toDouble() > 30 && righthandup.toDouble() < 140)&&(lefthandup.toDouble() < 150)){
                                    startCountFps++;

                                    if (startCountFps >= 10 && startTime == 0) {
                                        startTime = (currentTimeMillis() / 1000L).toInt()
                                        startAction = 1;
                                        startCountFps = 0;
                                    }
                                }
                                if (startTime != 0 && (righthandup.toDouble() > 30 && righthandup.toDouble() < 140)&&(lefthandup.toDouble() < 120)) {

                                    countAction =
                                        (currentTimeMillis() / 1000L).toInt() - startTime
                                    if(countAction == 1){
                                        countAction = 0
                                        startTime = 0
                                        counter++
                                    }

                                    if (counter >= 10) {
                                        acttime++
                                        countAction = 0
                                        startTime = 0
                                        counter = 0
                                    }
                                }else{
                                    startTime = 0
                                }
                                if(acttime == requireact4){
                                    hand1Fun = 4
                                    acttime = 0
                                    countAction = 0
                                    counter = 0
                                }
                                nowact.text = "正在做：手後曲拉左手"
                                var counter = ""+acttime+"組 "+counter+"秒/10秒"
                                actTimes.text = counter
                                nowact.alpha = 1F
                                actTimes.alpha = 1F
                                //var textcount = "%.2f".format(lefthandup .toDouble()) + " | " +"%.2f".format(righthandup .toDouble()) + "    "+(currentTimeMillis() / 1000L).toInt()+ "     "+ startCountFps
                                //tvFPS.text = textcount
                            }*/





                            /**if(handtoback.toDouble() <60) {

                                startCountFps++;

                                if(startCountFps >= 10 && startTime == 0) {
                                    startTime = (currentTimeMillis() / 1000L).toInt()
                                    startAction = 1;
                                    startCountFps = 0;
                                }
                            }
                            if(startTime != 0 && handtoback.toDouble() > 30) {
                                countAction = (currentTimeMillis() / 1000L).toInt() - startTime
                                if(countAction >= 15) {
                                    countAction = 0
                                    startTime = 0
                                }
                            } else {
                                countAction=0
                                startTime = 0

                            }*/
                            /*if(startAction ==1 && handtoback.toDouble() > 30) {
                                countAction++;
                                //startAction = 0;
                                if(countAction >= 15) {
                                    startAction = 0;
                                    endCountFps = 0;
                                    countAction = 0;
                                }
                            }else if(handtoback.toDouble() <= 30){
                                countAction=0
                                startAction=0
                            }*/



                        }


                        /** 对检测结果进行处理 */
                        override fun onDetectedInfo(
                            personScore: Float?,
                            poseLabels: List<Pair<String, Float>>?
                        ) {
                            return;
                            //tvScore.text = getString(R.string.tfe_pe_tv_score, personScore ?: 0f)
                            tvScore.text = poseLabels?.get(0)?.first;
                            /** 分析目标姿态，给出提示 */
                            if (poseLabels != null && personScore != null && personScore > 0.3) {
                                missingCounter = 0
                                val sortedLabels = poseLabels.sortedByDescending { it.second }
                                when (sortedLabels[0].first) {
                                    "incorrect" -> {
                                        crosslegCounter = 0
                                        standardCounter = 0
                                        if (poseRegister == "incorrect") {
                                            forwardheadCounter++
                                        }
                                        poseRegister = "incorrect"

                                        /** 显示当前坐姿状态：脖子前伸 */
                                        if (forwardheadCounter > 60) {

                                            /** 播放提示音 */
                                            if (forwardheadPlayerFlag) {
                                                //forwardheadPlayer.start()
                                            }
                                            standardPlayerFlag = true
                                            crosslegPlayerFlag = true
                                            forwardheadPlayerFlag = false

                                            //ivStatus.setImageResource(R.drawable.incorrect)
                                        } else if (forwardheadCounter > 30) {

                                            //ivStatus.setImageResource(R.drawable.incorrect)
                                        }

                                        /** 显示 Debug 信息 */
                                        //tvDebug.text = getString(R.string.tfe_pe_tv_debug, "${sortedLabels[0].first} $forwardheadCounter")
                                    }
                                    "crossleg" -> {
                                        forwardheadCounter = 0
                                        standardCounter = 0
                                        if (poseRegister == "crossleg") {
                                            crosslegCounter++
                                        }
                                        poseRegister = "crossleg"

                                        /** 显示当前坐姿状态：翘二郎腿 */
                                        if (crosslegCounter > 60) {

                                            /** 播放提示音 */
                                            if (crosslegPlayerFlag) {
                                                crosslegPlayer.start()
                                            }
                                            standardPlayerFlag = true
                                            crosslegPlayerFlag = false
                                            forwardheadPlayerFlag = true
                                            //ivStatus.setImageResource(R.drawable.crossleg_confirm)
                                        } else if (crosslegCounter > 30) {
                                            //ivStatus.setImageResource(R.drawable.crossleg_suspect)
                                        }

                                        /** 显示 Debug 信息 */
                                        //tvDebug.text = getString(R.string.tfe_pe_tv_debug, "${sortedLabels[0].first} $crosslegCounter")
                                    }
                                    else -> {
                                        forwardheadCounter = 0
                                        crosslegCounter = 0
                                        if (poseRegister == "correct") {
                                            standardCounter++
                                        }
                                        poseRegister = "correct"

                                        /** 显示当前坐姿状态：标准 */
                                        if (standardCounter > 30) {

                                            /** 播放提示音：坐姿标准 */
                                            if (standardPlayerFlag) {
                                                standardPlayer.start()
                                            }
                                            standardPlayerFlag = false
                                            crosslegPlayerFlag = true
                                            forwardheadPlayerFlag = true

                                            //ivStatus.setImageResource(R.drawable.correct)
                                        }

                                        /** 显示 Debug 信息 */
                                        //tvDebug.text = getString(R.string.tfe_pe_tv_debug, "${sortedLabels[0].first} $standardCounter")
                                    }
                                }


                            }
                            else {
                                missingCounter++
                                if (missingCounter > 30) {
                                    //ivStatus.setImageResource(R.drawable.incorrect)
                                    tvScore.text = "Missing"
                                }

                                /** 显示 Debug 信息 */
                                //tvDebug.text = getString(R.string.tfe_pe_tv_debug, "missing $missingCounter")
                            }
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
