package com.yaros.RadioUrl.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.yaros.RadioUrl.MainActivity
import com.yaros.RadioUrl.R

@SuppressLint("CustomSplashScreen")
class SplashScreen : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Установите splash screen перед базовой инициализацией
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

//        // Условие, при котором сплэшскрин будет оставаться видимым
//        splashScreen.setKeepOnScreenCondition {
//            // Например, можно использовать переменную для управления состоянием
//            // Здесь можно добавить логику, которая будет определять, когда скрыть сплэшскрин
//            // Например, возвращать true, если идет загрузка данных
//        }

        // Имитация задержки для демонстрации сплэшскрина
        // Замените на вашу логику загрузки данных, если необходимо
        Handler(Looper.getMainLooper()).postDelayed({
            // После завершения задержки переходите к основной активности
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish() // Закрываем текущую активность, чтобы она больше не отображалась
        }, 2000) // Задержка в 2 секунды (2000 миллисекунд)
    }
}