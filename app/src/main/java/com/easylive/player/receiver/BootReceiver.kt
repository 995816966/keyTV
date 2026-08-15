package com.easylive.player.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.easylive.player.data.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 开机自启广播。仅在用户开启“开机启动”设置时才拉起播放器。
 * 注意：部分国产 ROM（小米/华为/OPPO/VIVO）还需用户在系统“自启动管理”里手动授权，
 * 否则广播会被厂商杀掉——应用内会引导用户去开启。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        CoroutineScope(Dispatchers.IO).launch {
            val enabled = Preferences.bootStart(context).first()
            if (enabled) {
                val launch = Intent(context, com.easylive.player.PlayerActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(launch)
            }
        }
    }
}
