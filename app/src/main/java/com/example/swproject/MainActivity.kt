package com.example.swproject

import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.swproject.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    val binding by lazy{ ActivityMainBinding.inflate(layoutInflater)}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }

    }
    private fun changeImage(signal: String) {

        runOnUiThread {

            when (signal.trim()) {

                "NORMAL" -> {
                    binding.imgWater.setImageResource(
                        R.drawable.normal
                    )
                }

                "WARNING" -> {
                    binding.imgWater.setImageResource(
                        R.drawable.warning
                    )
                }

                "DANGER" -> {
                    binding.imgWater.setImageResource(
                        R.drawable.danger
                    )
                }
            }
        }
    }


    private fun receiveData(socket: BluetoothSocket) {

        Thread {

            val reader = socket.inputStream.bufferedReader()

            while (true) {

                val message = reader.readLine()

                if (message != null) {
                    changeImage(message)
                }
            }

        }.start()
    }



    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_test,menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.controller ->{
                val intent : Intent
                        = Intent(this, ControlActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    }
}