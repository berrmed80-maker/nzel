package com.nzel.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nzel.app.dl.HistoryStore
import com.nzel.app.dl.LinkParser
import com.nzel.app.ui.NzelTheme
import com.nzel.app.ui.RootScreen

object SharedLink {
    var pending by mutableStateOf<String?>(null)
}

class MainActivity : AppCompatActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HistoryStore.init(this)
        handleIntent(intent)
        setContent {
            NzelTheme {
                RootScreen(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            LinkParser.extract(text)?.let { SharedLink.pending = it }
        }
    }
}
