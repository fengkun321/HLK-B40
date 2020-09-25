package com.example.bluetooth.le.activity

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.*
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.util.Log
import android.view.View
import android.widget.RadioButton
import android.widget.Switch
import com.example.bluetooth.le.R
import com.example.bluetooth.le.UUIDInfo
import kotlinx.android.synthetic.main.activity_set_work.*
import java.util.*

class SetWorkActivity : BaseActivity(), View.OnClickListener {

    private lateinit var selectServer: UUIDInfo
    private lateinit var selectWrite: UUIDInfo
    private lateinit var selectRead: UUIDInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_set_work)

        initUI()

        bindReceiver()

        bindServerSubNofify()


    }

    private fun initUI() {
        imgBack.setOnClickListener(this)
        tvRefresh.setOnClickListener(this)
        btnNameSet.setOnClickListener(this)
        btnBandSet.setOnClickListener(this)
        btnRFPowerSet.setOnClickListener(this)
        btnCONNISet.setOnClickListener(this)
        btnADVISet.setOnClickListener(this)
        btnADVData.setOnClickListener(this)
        btnPEERMAC.setOnClickListener(this)
        btnPINCODE.setOnClickListener(this)
        btnAUTHPWG.setOnClickListener(this)
        btnDefault.setOnClickListener(this)
        btnReStart.setOnClickListener(this)

        rbAdmin.tag = false
        rbClient.isChecked = true
        rbClient.tag = false
        rbAdmin.setOnTouchListener(rbTouchListener)
        rbClient.setOnTouchListener(rbTouchListener)

        rgModelType.setOnCheckedChangeListener { group, checkedId ->
            // 主机
            if (checkedId == R.id.rbAdmin) {
                rlClientMAC.visibility = View.VISIBLE
                if (rbAdmin.tag as Boolean) {
                    rbAdmin.tag = false
                    // 发指令
                    startSendByTimer(false, true, "AT+ROLE=2\r\n")
                }
            }
            // 从机
            else {
                rlClientMAC.visibility = View.GONE
                if (rbClient.tag as Boolean) {
                    rbClient.tag = false
                    // 发指令
                    startSendByTimer(false, true, "AT+ROLE=1\r\n")
                }
            }

        }

        switchAutoSleep.tag = false
        switchAutoSleep.setOnTouchListener(switchListener)
        switchAutoSleep.setOnCheckedChangeListener { buttonView, isChecked ->
            // 发指令
            if (isChecked) {
                if (switchAutoSleep.tag as Boolean) {
                    switchAutoSleep.tag = false
                    startSendByTimer(false, true, "AT+SLEEPEN=0\r\n")
                }
            } else {
                if (switchAutoSleep.tag as Boolean) {
                    switchAutoSleep.tag = false
                    startSendByTimer(false, true, "AT+SLEEPEN=1\r\n")
                }
            }
        }

        switchPINBind.tag = false
        switchPINBind.setOnTouchListener(switchListener)
        switchPINBind.setOnCheckedChangeListener { buttonView, isChecked ->
            // 需要配对
            if (isChecked) {
                rlPINCode.visibility = View.VISIBLE
                if (switchPINBind.tag as Boolean) {
                    switchPINBind.tag = false
                    startSendByTimer(false, true, "AT+ENCRYPT=1\r\n")
                }
            } else {
                rlPINCode.visibility = View.GONE
                if (switchPINBind.tag as Boolean) {
                    switchPINBind.tag = false
                    startSendByTimer(false, true, "AT+ENCRYPT=0\r\n")
                }
            }
        }

        rlVersion.setOnLongClickListener {
            startActivity(Intent(mContext, OTAUpdateActivity::class.java))
            false
        }

        tvMac.text = DeviceScanActivity.getInstance().nowSelectDevice.address
        rlClientMAC.visibility = View.GONE
        rlPINCode.visibility = View.GONE

    }

    private val rbTouchListener = View.OnTouchListener { p0, p1 ->
        val rb = p0 as RadioButton
        if (!rb.isChecked)
            rb.tag = true
        false
    }

    private val switchListener = View.OnTouchListener { p0, p1 ->
        val rb = p0 as Switch
        rb.tag = true
        false
    }


    private fun bindReceiver() {
        var intentFilter = IntentFilter()
        intentFilter.addAction(BC_ReadData)
        intentFilter.addAction(BC_WriteData)
        intentFilter.addAction(BC_RecvData)
        intentFilter.addAction(BC_ChangeMTU)
        intentFilter.addAction(BC_ConnectStatus)
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
//                    if (readArray[iR].uuidString.equals(strVERSION_Read, true)) {
//                        val isRead = DeviceScanActivity.getInstance().mBLE.readCharacteristic(readArray[iR].bluetoothGattCharacteristic)
//                        if (!isRead) {
//                            showToast("版本号读取失败！")
//                        }
//                    }

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
            // 订阅通知成功，则开始查询参数
            tvRefresh.performClick()
        } else {
            showToast("通知打开失败，请检查设备！")
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
                    val strVersion = "V ${String.format("%d", dataValue[0])}." +
                            "${String.format("%02d", dataValue[1])}"
                    tvVersion.text = strVersion
                } else if (uuid.toString().equals(selectRead?.uuidString, true)) {

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
            // 由订阅的通道数据回调
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
                else if (isSetData) {
                    val resultArray = strResultData.split("\r\n")
                    val resultType = resultArray[0].substring(0, strResultData.indexOf("="))
                    var reusltOK = (resultArray[1].equals("OK"))
                    if (strWillSendData.contains(resultType)) {
                        startSendByTimer(false, false, "")
                        if (reusltOK) {
                            showToast("设置成功！")
                            // 恢复出厂，则重新获取所有参数
                            if (resultType.contains("DEFAULT")) {
                                tvRefresh.performClick()
                            }
                            // 重启设备，则主动断开连接，并返回列表界面
//                            else if (resultType.contains("REBOOT")) {
//                                val intent0 = Intent(mContext, DeviceScanActivity::class.java)
//                                intent0.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
//                                startActivity(intent0)
//                                finish()
//                            }
                        }
                        else {
                            loadingDialog.dismiss()
                            showToast("设置失败！")
                        }


                    }
                }
            }
            // MTU改变的回调
            else if (strAction.equals(BC_ChangeMTU)) {
                val strMsg = intent?.getStringExtra("strMsg")
                val iMTU = intent?.getIntExtra("iMTU", 0)
            }
            // 连接状态变化的回调
            else if (strAction.equals(BC_ConnectStatus)) {
                val isConnectState = intent?.getBooleanExtra("isConnectState", false)!!
                val strConnectState = intent?.getStringExtra("strConnectState")!!
                showToast(strConnectState)
                // 连接已断开
                if (!isConnectState) {
                    // 因为发了重启设备的指令，则自动返回列表界面
                    if (strWillSendData.contains("REBOOT") && loadingDialog.isShowing) {
                        val intent0 = Intent(mContext, DeviceScanActivity::class.java)
                        intent0.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent0)
                        finish()
                    }
                }

            }
        }

    }


    private var iNowQueryPosition = 0

    /** 解析查询的回调数据 */
    private val queryATData = arrayListOf<String>("AT+VER", "AT+NAME", "AT+BAND", "AT+RFPOWER",
            "AT+SLEEPEN", "AT+CONNI", "AT+ADVI", "AT+ADVDATA", "AT+ROLE",
            "AT+ENCRYPT", "AT+PINCODE", "AT+PEERMAC", "AT+AUTHPWG")

    private fun analyQueryData(strResult: String) {
        val resultArray = strResult.split("\r\n")
        var resultValue = resultArray[0].substring(strResult.indexOf("=") + 1)
        var strWillSendData = ""
        if (strResult.contains("AT+VER")) {
            if (resultArray[1].equals("OK"))
                tvVersion.text = "V $resultValue"
            strWillSendData = queryATData[1]
        } else if (strResult.contains("AT+NAME")) {
            if (resultArray[1].equals("OK"))
                edName.setText(resultValue)
            strWillSendData = queryATData[2]
        } else if (strResult.contains("AT+BAND")) {
            if (resultArray[1].equals("OK")) {
                val resoucceArray = resources.getStringArray(R.array.band_table)
                for (iPosition in 0 until resoucceArray.size) {
                    if (resoucceArray[iPosition].equals(resultValue)) {
                        spinnerBand.setSelection(iPosition, true)
                        break
                    }
                }
            }


            strWillSendData = queryATData[3]
        } else if (strResult.contains("AT+RFPOWER")) {
            if (resultArray[1].equals("OK")) {
                val resoucceArray = resources.getStringArray(R.array.rfpower_table)
                for (iPosition in 0 until resoucceArray.size) {
                    if (resoucceArray[iPosition].equals(resultValue)) {
                        spinnerPower.setSelection(iPosition, true)
                        break
                    }
                }
            }

            strWillSendData = queryATData[4]
        } else if (strResult.contains("AT+SLEEPEN")) {
            if (resultArray[1].equals("OK"))
                switchAutoSleep.isChecked = (resultValue == "1")
            strWillSendData = queryATData[5]
        } else if (strResult.contains("AT+CONNI")) {
            if (resultArray[1].equals("OK"))
                edCONNI.setText(resultValue)
            strWillSendData = queryATData[6]
        } else if (strResult.contains("AT+ADVI")) {
            if (resultArray[1].equals("OK"))
                edADVI.setText(resultValue)
            strWillSendData = queryATData[7]
        } else if (strResult.contains("AT+ADVDATA")) {
            if (resultArray[1].equals("OK"))
                edADVData.setText(resultValue)
            strWillSendData = queryATData[8]
        } else if (strResult.contains("AT+ROLE")) {
            if (resultArray[1].equals("OK")) {
                if (resultValue == "1") {
                    rbClient.isChecked = true
                } else {
                    rbAdmin.isChecked = true
                }
            }

            strWillSendData = queryATData[9]
        } else if (strResult.contains("AT+ENCRYPT")) {
            if (resultArray[1].equals("OK")) {
                switchPINBind.isChecked = (resultValue == "1")

            }

            strWillSendData = queryATData[10]
        } else if (strResult.contains("AT+PINCODE")) {
            if (resultArray[1].equals("OK"))
                edPINCODE.setText(resultValue)
            strWillSendData = queryATData[11]
        } else if (strResult.contains("AT+PEERMAC")) {
            if (resultArray[1].equals("OK"))
                edPEERMAC.setText(resultValue)
            strWillSendData = queryATData[12]
        } else if (strResult.contains("AT+AUTHPWG")) {
            if (resultArray[1].equals("OK"))
                edAUTHPWG.setText(resultValue)
            // 最后一项了，查询结束
            isQueryData = false
            loadingDialog.dismiss()
            showToast("查询完成！")
            startSendByTimer(false, false, "")
        }

        if (!strWillSendData.isEmpty()) {
            startSendByTimer(true, false, "$strWillSendData=?\r\n")
        }

    }

    private var isQueryData = false
    private var isSetData = false
    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.imgBack -> finish()
            R.id.tvRefresh -> {
                loadingDialog.showAndMsg("正在查询...")
                isQueryData = true
                startSendByTimer(true, false, queryATData[0] + "=?\r\n")
            }
            R.id.btnNameSet -> {
                val strValue = edName.text.toString()
                if (!strValue.equals("")) {
                    startSendByTimer(false, true, "AT+NAME=$strValue\r\n")
                }
            }
            R.id.btnBandSet -> {
                val strValue = spinnerBand.selectedItem.toString()
                startSendByTimer(false, true, "AT+BAND=$strValue\r\n")
            }
            R.id.btnRFPowerSet -> {
                val strValue = spinnerPower.selectedItem.toString()
                startSendByTimer(false, true, "AT+RFPOWER=$strValue\r\n")
            }
            R.id.btnCONNISet -> {
                val strValue = edCONNI.text.toString()
                if (!strValue.equals("")) {
                    startSendByTimer(false, true, "AT+CONNI=$strValue\r\n")
                }
            }
            R.id.btnADVISet -> {
                val strValue = edADVI.text.toString()
                if (!strValue.equals("")) {
                    startSendByTimer(false, true, "AT+ADVI=$strValue\r\n")
                }
            }
            R.id.btnADVData -> {
                val strValue = edADVData.text.toString()
                if (!strValue.equals("")) {
                    startSendByTimer(false, true, "AT+ADVDATA=$strValue\r\n")
                }
            }
            R.id.btnPINCODE -> {
                val strValue = edPINCODE.text.toString()
                if (!strValue.equals("")) {
                    startSendByTimer(false, true, "AT+PINCODE=$strValue\r\n")
                }
            }
            R.id.btnPEERMAC -> {
                val strValue = edPEERMAC.text.toString()
                if (!strValue.equals("")) {
                    startSendByTimer(false, true, "AT+PEERMAC=$strValue\r\n")
                }
            }
            R.id.btnAUTHPWG -> {
                val strValue = edAUTHPWG.text.toString()
                if (!strValue.equals("")) {
                    startSendByTimer(false, true, "AT+AUTHPWG=$strValue\r\n")
                }
            }
            R.id.btnDefault -> {
                val normalDialog: AlertDialog.Builder = AlertDialog.Builder(mContext)
                normalDialog.setMessage("确定要恢复出厂设置吗？")
                normalDialog.setPositiveButton("确定") { dialog, which ->
                    startSendByTimer(false, true, "AT+DEFAULT=1\r\n")
                }
                normalDialog.setNegativeButton("取消") { dialog, which ->
                }
                normalDialog.show()
            }
            R.id.btnReStart -> {
                val normalDialog: AlertDialog.Builder = AlertDialog.Builder(mContext)
                normalDialog.setMessage("确定要重启设备吗？")
                normalDialog.setPositiveButton("确定") { dialog, which ->
                    loadingDialog.showAndMsg("正在重启...")
                    startSendByTimer(false, true, "AT+REBOOT=1\r\n")
                }
                normalDialog.setNegativeButton("取消") { dialog, which ->
                }
                normalDialog.show()
            }
        }
    }

    var sendTimer: Timer? = Timer()
    var strWillSendData = ""
    var iSendCount = 0
    private fun startSendByTimer(isRead: Boolean, isWrite: Boolean, strSendData: String) {
        sendTimer?.cancel()
        sendTimer = null
        isQueryData = isRead
        isSetData = isWrite
        if (isQueryData || isSetData) {
            strWillSendData = strSendData
            iSendCount = 3
            sendTimer = Timer()
            sendTimer?.schedule(object : TimerTask() {
                override fun run() {
                    if (iSendCount > 0) {
                        startSendData(strWillSendData)
                        --iSendCount
                    }
                    // 发送超时啦！
                    else {
                        // 查询下一个
                        if (isQueryData) {
                            ++iNowQueryPosition
                            if (iNowQueryPosition < queryATData.size) {
                                showToast("查询超时，开始查询下一个！")
                                startSendByTimer(true, false, "${queryATData[iNowQueryPosition]}=?\r\n")
                            } else {
                                runOnUiThread {
                                    loadingDialog.dismiss()
                                    showToast("查询超时！")
                                    startSendByTimer(false, false, "")
                                }
                            }
                        } else if (isSetData) {
                            runOnUiThread {
                                loadingDialog.dismiss()
                                showToast("设置超时，请重试！")
                                startSendByTimer(false, false, "")
                            }
                        }
                    }
                }
            }, 0, 2000)
        }

    }


    override fun onDestroy() {
        super.onDestroy()
        DeviceScanActivity.getInstance().mBLE.setCharacteristicNotification(selectRead.bluetoothGattCharacteristic, false)
        DeviceScanActivity.getInstance().mBLE.disconnect()
        unregisterReceiver(mBroadcastReceiver)
    }

}