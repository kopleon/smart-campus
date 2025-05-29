package lyi.AIworkout.posemon

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.TextView
import lyi.AIworkout.posemon.data.SurveyResult



var diff: String? = null
class MainActivity3 : AppCompatActivity() {
    private lateinit var Activities: Button
    private lateinit var level: TextView
    private lateinit var DIFF: TextView


    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main1)



        val total = SurveyResult.Total


        //println(diff)
        //println("++"+DIFF.text)
        Activities = findViewById(R.id.button5)
        Activities.setOnClickListener {
            //println(dang.text)
                val activityopen = Intent(this, MainActivity::class.java)

                startActivity(activityopen)

            }

    }


}
