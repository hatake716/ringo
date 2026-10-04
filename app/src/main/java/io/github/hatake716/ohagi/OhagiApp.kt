package io.github.hatake716.ohagi

import android.app.Application

class OhagiApp : Application() {

    lateinit var graph: Graph
        private set

    override fun onCreate() {
        super.onCreate()
        // 分割起動の通知チャネルは SplitLaunchNotification の通知 worker が投稿前に作る。
        // ここで作ると cold start のメインスレッドに NotificationManager の Binder 呼び出しが入る。
        graph = Graph(this)
        graph.start()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::graph.isInitialized) graph.trimMemory(level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (::graph.isInitialized) graph.clearMemoryCaches()
    }
}
