package com.example.bluetooth.le.activity

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.*
import android.os.Bundle
import android.util.Log
import android.view.View
import com.example.bluetooth.le.R
import com.example.bluetooth.le.UUIDInfo
import kotlinx.android.synthetic.main.activity_set_work.*
import java.util.*

class SetWorkActivity : BaseActivity(), View.OnClickListener {

    private lateinit var selectServer : UUIDInfo
    private lateinit var selectWrite : UUIDInfo
    private lateinit var selectRead : UUIDInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_set_work)

        imgBack.setOnClickListener(this)
        tvRefresh.setOnClickListener(this)
        btnNameSet.setOnClickListener(this)
        btnCONNISet.setOnClickListener(this)
        btnRFPowerSet.setOnClickListener(this)
        btnBandSet.setOnClickListener(this)
        rlVersion.setOnLongClickListener {
            startActivity(Intent(mContext, OTAUpdateActivity::class.java))
            false
        }

        tvMac.text = DeviceScanActivity.getInstance().nowSelectDevice.address

        bindReceiver()

        bindServerSubNofify()


    }


    private fun bindReceiver() {
        var intentFilter = IntentFilter()
        intentFilter.addAction(BC_ReadData)
        intentFilter.addAction(BC_WriteData)
        intentFilter.addAction(BC_RecvData)
        // 注册广播
        registerReceiver(mBroadcastReceiver, intentFilter)
    }

    private fun bindServerSubNofify() {

        for (iPosition in 0 until TRXActivity.getInstance().serverList.size) {
            val serverUUID = TRXActivity.getInstance().serverList[iPosition]
            if (serverUUID.uuidString.equals(strSET_Server, true)) {
                selectServer = serverUUID
                val readArray = TRXActivity.getInstance().readCharaMap[selectServer.uuidString]
                for (iR in 0 until readArray!!.size) {
                    // 读版本号
                    if (readArray[iR].uuidString.equals(strVERSION_Read, true)) {
                        val isRead = DeviceScanActivity.getInstance().mBLE.readCharacteristic(readArray[iR].bluetoothGattCharacteristic)
                        if (!isRead) {
                            showToast("版本号读取失败！")
                        }
                    }

                    if (readArray[iR].uuidString.equals(strSET_Read, true)) {
                        selectRead = readArray[iR]
                        break
                    }
                }
                val writeArray = TRXActivity.getInstance().writeCharaMap[selectServer.uuidString]
                for (iW in 0 until writeArray!!.size) {
                    if (writeArray[iW].uuidString.equals(strSET_Write, true)) {
                        selectWrite = writeArray[iW]
                        break
                    }
                }
                break
            }
        }
        if (!this::selectServer.isInitialized || selectServer == null ||
                !this::selectRead.isInitialized || selectRead == null ||
                !this::selectWrite.isInitialized || selectWrite == null) {
            showToast("未找到指定通道！")
            tvRefresh.isEnabled = false
            btnNameSet.isEnabled = false
            btnCONNISet.isEnabled = false
            btnRFPowerSet.isEnabled = false
            btnBandSet.isEnabled = false
            return
        }

        // 订阅通知
        val isNotification = DeviceScanActivity.getInstance().mBLE.setCharacteristicNotification(selectRead.bluetoothGattCharacteristic, true)
        Log.e("TestDataActivity", "isNotification:$isNotification")
        if (isNotification) {
            val descriptors: List<BluetoothGattDescriptor> = selectRead?.bluetoothGattCharacteristic!!.descriptors
            for (descriptor in descriptors) {
                // 读写开关操作，writeDescriptor 否则可能读取不到数据。
                val b1 = descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                if (b1) {
                    val isB = DeviceScanActivity.getInstance().mBLE.writeDescriptor(descriptor)
                    Log.e(ContentValues.TAG, "startRead: $isB")
                }
            }
        }

    }

    private fun startSendData(strSend: String) {
        if (selectWrite == null) return
        var mgattCharacteristic = selectWrite?.bluetoothGattCharacteristic!!
        mgattCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        mgattCharacteristic.setValue(strSend.toByteArray())
        DeviceScanActivity.getInstance().mBLE.writeCharacteristic(mgattCharacteristic)
    }

    val mBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val strAction = intent?.action
            // 主动读通道的回调
            if (strAction.equals(BC_ReadData)) {
                val uuid = intent?.getSerializableExtra("UUID") as UUID
                val iStatus = intent.getIntExtra("status", -1)
                val dataValue = intent.getByteArrayExtra("data")
                // 版本查询的回复
                if (uuid.toString().equals(strVERSION_Read, true)) {
                    val strVersion = "V ${String.format("%d",dataValue[0])}." +
                            "${String.format("%02d",dataValue[1])}"
                    tvVersion.text = strVersion
                }
                else if (uuid.toString().equals(selectRead?.uuidString, true)) {

                }

            }
            // 写通道的回调
            else if (strAction.equals(BC_WriteData)) {
                val uuid = intent?.getSerializableExtra("UUID") as UUID
                val iStatus = intent?.getIntExtra("status", -1)

                if (!uuid.toString().equals(selectWrite?.uuidString, true)) {
                    return
                }

            }
            // 订阅通道的回调
            else if (strAction.equals(BC_RecvData)) {
                val uuid = intent?.getSerializableExtra("UUID") as UUID
                val dataValue = intent.getByteArrayExtra("data")
                if (!uuid.toString().equals(selectRead?.uuidString, true)) {
                    return
                }
                val strResultData = String(dataValue)
                // 正在查询参数
                if (isQueryData) {
                    runOnUiThread { analyQueryData(strResultData) }
                }
                // 可能是设置的回复
                else {
                    if (strResultData.contains(strNowSetData)) {
                        strNowSetData = ""
                        showToast("操作成功！")
                    }
                }

            }
        }

    }


    /** 解析查询的回调数据 */
    private val queryATData = arrayListOf<String>("AT+VER", "AT+MAC",
            "AT+NAME", "AT+CONNI", "AT+RFPOWER", "AT+BAND")
    private fun analyQueryData(strResult: String) {
        var strWillSendData = ""
        if (strResult.contains("AT+VER")) {
            strWillSendData = queryATData[1]
        }
        else if (strResult.contains("AT+MAC")) {
            strWillSendData = queryATData[2]
        }
        else if (strResult.contains("AT+NAME")) {
            strWillSendData = queryATData[3]
        }
        else if (strResult.contains("AT+CONNI")) {
            strWillSendData = queryATData[4]
        }
        else if (strResult.contains("AT+RFPOWER")) {
            strWillSendData = queryATData[5]
        }
        else if (strResult.contains("AT+BAND")) {
            // 最后一项了，查询结束
            isQueryData = false
            loadingDialog.dismiss()
            showToast("查询完成！")
        }

        if (!strWillSendData.isEmpty()) {
            startSendData(strWillSendData + "\r\n")
        }

    }

    private var isQueryData = false
    private var strNowSetData = ""
    override fun onClick(v: View?) {
        when(v?.id) {
            R.id.imgBack -> finish()
            R.id.tvRefresh -> {
                loadingDialog.showAndMsg("正在查询...")
                isQueryData = true
                val strSendData = queryATData[0] + "\r\n"
                startSendData(strSendData)
            }
            R.id.btnNameSet -> {
                val strValue = edName.text.toString()
                if (!strValue.equals("")) {
                    strNowSetData = "AT+NAME=$strValue\r\n"
                    startSendData(strNowSetData)
                }
            }
            R.id.btnCONNISet -> {
                val strValue = edCONNI.text.toString()
                if (!strValue.equals("")) {
                    strNowSetData = "AT+CONNI=$strValue\r\n"
                    startSendData(strNowSetData)
                }
            }
            R.id.btnRFPowerSet -> {
                val strValue = spinnerPower.selectedItem.toString()
                strNowSetData = "AT+RFPOWER=$strValue\r\n"
                startSendData(strNowSetData)
            }
            R.id.btnBandSet -> {
                val strValue = spinnerBand.selectedItem.toString()
                strNowSetData = "AT+BAND=$strValue\r\n"
                startSendData(strNowSetData)
            }

        }

    }



    override fun onDestroy() {
        super.onDestroy()
        DeviceScanActivity.getInstance().mBLE.setCharacteristicNotification(selectRead.bluetoothGattCharacteristic, false)
        unregisterReceiver(mBroadcastReceiver)
    }

}