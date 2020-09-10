package com.example.bluetooth.le.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import com.example.bluetooth.le.R
import kotlinx.android.synthetic.main.activity_device_info.*

class DeviceInfoActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_info)

        imgBack.setOnClickListener { finish() }
        tvVerUpdate.setOnLongClickListener {
            startActivity(Intent(mContext, OTAUpdateActivity().javaClass))
            false
        }

        var intentFilter = IntentFilter()
        intentFilter.addAction(BC_ReadData)
        intentFilter.addAction(BC_WriteData)
        intentFilter.addAction(BC_RecvData)
        // 注册广播
        registerReceiver(mBroadcastReceiver, intentFilter)

    }

    val mBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val strAction = intent?.action
            // 主动读通道的回调
            if (strAction.equals(BC_ReadData)) {

            }
            // 写通道的回调
            else if (strAction.equals(BC_WriteData)) {

            }
            // 订阅通道的回调
            else if (strAction.equals(BC_RecvData)) {

            }
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(mBroadcastReceiver)
    }


}