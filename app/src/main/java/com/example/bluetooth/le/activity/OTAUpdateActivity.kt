package com.example.bluetooth.le.activity

import android.app.Activity
import android.app.Dialog
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.ContentValues
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.bluetooth.le.BluetoothLeClass
import com.example.bluetooth.le.R
import com.example.bluetooth.le.UUIDInfo
import com.example.bluetooth.le.WriterOperation
import com.example.bluetooth.le.utilInfo.Utils
import kotlinx.android.synthetic.main.activity_ota_update.*
import kotlinx.android.synthetic.main.activity_ota_update.imgBack
import kotlinx.android.synthetic.main.activity_ota_update.tvMTU
import kotlinx.android.synthetic.main.activity_testdata.*
import kotlinx.android.synthetic.main.viewpager_two.view.*
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

class OTAUpdateActivity : Activity(), View.OnClickListener {

    private lateinit var woperation : WriterOperation
    private lateinit var selectServer : UUIDInfo
    private lateinit var selectWrite : UUIDInfo
    private lateinit var selectRead : UUIDInfo
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ota_update)

        tvFilePath.setOnClickListener(this)
        btnUpdate.setOnClickListener(this)
        imgBack.setOnClickListener(this)

        woperation = WriterOperation()

    }

    override fun onResume() {
        super.onResume()
        // 断开或连接 状态发生变化时调用
        DeviceScanActivity.getInstance().mBLE.setOnConnectListener(OnConnectListener)
        // 读操作的回调
        DeviceScanActivity.getInstance().mBLE.setOnDataAvailableListener(OnDataAvailableListener)
        // 写操作的回调
        DeviceScanActivity.getInstance().mBLE.setOnWriteDataListener(OnWriteDataListener)
        // 接收到硬件返回的数据
        DeviceScanActivity.getInstance().mBLE.setOnRecvDataListener(OnRecvDataListerner)
        // MTU改变的回调
        DeviceScanActivity.getInstance().mBLE.setOnChangeMTUListener(onChangeMTUListener)

    }

    override fun onPause() {
        super.onPause()
        // 反注册
        DeviceScanActivity.getInstance().mBLE.setUnConnectListener()
        DeviceScanActivity.getInstance().mBLE.setUnDataAvailableListener()
        DeviceScanActivity.getInstance().mBLE.setUnWriteDataListener()
        DeviceScanActivity.getInstance().mBLE.setUnRecvDataListener()
        DeviceScanActivity.getInstance().mBLE.setUnChangeMTUListener()
    }

    // 断开或连接 状态发生变化时调用
    private val OnConnectListener = object : BluetoothLeClass.OnConnectListener {
        override fun onConnected(gatt: BluetoothGatt?, status: Int, newState: Int) {
            updateLog("Connected to GATT server.")
            isConnected = true
        }
        override fun onDisconnect(gatt: BluetoothGatt?, status: Int, newState: Int) {
            updateLog("Disconnected from GATT server.")
            isConnected = false
        }
        override fun onConnectting(gatt: BluetoothGatt?, status: Int, newState: Int) {
            updateLog("Connectting to GATT...")
            isConnected = false
        }
    }

    // 读操作的回调
    private val OnDataAvailableListener = object : BluetoothLeClass.OnDataAvailableListener {
        override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            val strStatus = BluetoothLeClass.strResultInfoByStatus(status)
            updateLog("Read:$strStatus")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (characteristic!!.value.size == 0) return
                var string = String(characteristic!!.value)
                updateLog("RecvData<<<$string,Len:${characteristic!!.value.size}")
            }
        }
    }

    // 写操作的回调
    private val OnWriteDataListener = object : BluetoothLeClass.OnWriteDataListener {
        override fun OnCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            val strStatus = BluetoothLeClass.strResultInfoByStatus(status)
            updateLog("Write:$strStatus")
            writeStatus = (status == 0)
        }
    }

    // 接收到硬件返回的数据
    private val OnRecvDataListerner = object : BluetoothLeClass.OnRecvDataListerner {
        override fun OnCharacteristicRecv(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            if (characteristic!!.value.size == 0) return
            isResultCallBack = true
            resultUpdateValue = characteristic.value
            var string = String(characteristic!!.value)
            updateLog("RecvData<<<$string,Len:${characteristic!!.value.size}")
        }
    }

    /** MTU更改的回调 */
    private val onChangeMTUListener = object : BluetoothLeClass.OnChangeMTUListener {
        override fun OnChangeMTUListener(strResult: String?, iMTU: Int) {
            val strValue = intent.getStringExtra("onMtuChanged")
            updateLog(strValue)
            tvMTU.text = "MTU*${DeviceScanActivity.getInstance().mBLE.mtuSize}"
            isResultCallBack = true
        }

    }

    private lateinit var precenttv: TextView
    private lateinit var mDialog : Dialog
    private fun showDialog() {
        val layoutinflater = LayoutInflater.from(this)
        val view = layoutinflater.inflate(R.layout.loading_process_dialog_anim, null)
        precenttv = view.findViewById<View>(R.id.precenttv) as TextView
        mDialog = Dialog(this, R.style.dialog)
        mDialog.setCancelable(false)
        mDialog.setContentView(view)
        mDialog.show()
    }

    override fun onClick(v: View?) {
        when(v?.id) {
            R.id.imgBack -> onBackPressed()
            R.id.btnUpdate -> {
                val strFilePath = tvFilePath.text.toString()
                startUpdateBT(strFilePath)
            }
            R.id.tvFilePath -> {
                var strFilePath = tvFilePath.text.toString()
                if (strFilePath.equals("请选择 bin 文件")) strFilePath = ""
                val intent = Intent(this, SelectFileActivity::class.java)
                intent.putExtra("filepatch", strFilePath)
                startActivityForResult(intent,SelectFileActivity.RESULT_CODE)
            }
        }
    }

    /** 开始升级 */
    private fun startUpdateBT(strFilePath:String) {
        if (strFilePath.equals("")) return
        val file = File(strFilePath.trim())
        // 校验文件
        if (!checkFileOK(file)) return
        tvLog.text = ""
        updateLog("!!!!!!!!!!开始升级!!!!!!!!!!\n文件：$strFilePath")
        showDialog()
        // 起一个线程，开始升级
        Thread(Runnable {
            try {
                doSendFileByBluetooth(file)
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            }
        }).start()
    }

    /**
     * 校验文件
     */
    private fun checkFileOK(file:File) : Boolean {
        var isOK = true
        if (file.length() < 100) {
            Toast.makeText(this, "请选择有效的配置文件", Toast.LENGTH_LONG).show()
            isOK = false
        }
        val infile = FileInputStream(file)
        var Buffer = ByteArray(62)
        val input = BufferedInputStream(infile)
        input.read(Buffer, 0, Buffer.size)
        if (Buffer[58].toInt() != 0x51 || Buffer[59].toInt() != 0x52 || Buffer[60].toInt() != 0x52 || Buffer[61].toInt() != 0x51) {
            Toast.makeText(this, "请选择正确的文件", Toast.LENGTH_LONG).show()
            isOK = false
        }
        input.close()
        infile.close()
        return isOK
    }

    private lateinit var isfile: FileInputStream
    private lateinit var input : BufferedInputStream
    private var leng = 0L
    private var isResultCallBack = false
    private var writeStatus = false
    private var resultUpdateValue: ByteArray? = null
    private val sencondaddr = 0x14000
    private val firstaddr = 0

    fun doSendFileByBluetooth(file:File) {
        var read_count: Int
        var i = 0
        var addr: Int // 存储的起始地址
        var lastReadCount = 0
        var packageSize = 235 //bleclass.mtuSize - 3; //235;
        var send_data_count = 0
        isfile = FileInputStream(file)
        leng = file.length()
        input = BufferedInputStream(isfile)
        updateLog("大小：${Utils.formatFileSize(leng)}")
        // 先设置mtu
        isResultCallBack = false
        DeviceScanActivity.getInstance().mBLE.requestMtu(512)
        while (!isResultCallBack) {
            if (!checkConnectState()) {
                return
            }
        }
        packageSize = DeviceScanActivity.getInstance().mBLE.mtuSize - 3 - 9
        val inputBuffer = ByteArray(packageSize)
        // 再获取当前升级程序的存储起始地址
        isResultCallBack = false
        woperation.send_data(WriterOperation.OTA_CMD_GET_STR_BASE, 0, null, 0,
                selectWrite.bluetoothGattCharacteristic, DeviceScanActivity.getInstance().mBLE)
        while (!isResultCallBack) {
            if (!checkConnectState()) {
                return
            }
        }
        if (woperation.bytetoint(resultUpdateValue) == firstaddr)
            addr = sencondaddr
        else
            addr = firstaddr

        updateLog("BLE升级的起始位置：$addr")
        // 按照上面的起始地址和文件大小，计算具体需要发多少数据，并擦除模块对应的升级内存空间
        page_erase(addr, leng, selectWrite.bluetoothGattCharacteristic, DeviceScanActivity.getInstance().mBLE)
        try {
            updateLog("开始写入：0%")
            // 最后根据MTU的传输长度，开始发送升级文件
            while (input.read(inputBuffer, 0, packageSize).also { read_count = it } != -1) {
                writeStatus = false
                isResultCallBack = false
                woperation.send_data(WriterOperation.OTA_CMD_WRITE_DATA, addr, inputBuffer, read_count,
                        selectWrite.bluetoothGattCharacteristic, DeviceScanActivity.getInstance().mBLE)
                //for(delay_num = 0;delay_num < 10000;delay_num++);
                addr += read_count
                lastReadCount = read_count
                send_data_count += read_count
                i++
                runOnUiThread {
                    // 更新升级进度%
                    val writePrecent = (send_data_count.toFloat() / leng * 100) as Int
                    precenttv.text = "已写入..$writePrecent%"
                    updateLog("已写入..$writePrecent%")
                }

                while (!writeStatus);
                while (!isResultCallBack) {
                    if (!checkConnectState()) {
                        return
                    }
                }
            }
            // 如果模块回复的数据大小和升级的不一致，则断开连接
            while (woperation.bytetoint(resultUpdateValue) != (addr - lastReadCount)) {
                if (!checkConnectState()) {
                    return
                }
            }
            updateLog("**********升级完成，则重启设备**********")
            // 升级完成，则重启设备！
            woperation.send_data(WriterOperation.OTA_CMD_REBOOT, 0, null,
                    0,selectWrite.bluetoothGattCharacteristic, DeviceScanActivity.getInstance().mBLE)
            runOnUiThread {
                OtaActiviy.mDialog.cancel()
                Toast.makeText(this, "写入成功",Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun page_erase(addr: Int, length: Long, mgattCharacteristic: BluetoothGattCharacteristic, bleclass: BluetoothLeClass): Int {
        var addr = addr
        var count = length / 0x1000 // 0x1000 4096,4kb
        if (length % 0x1000 != 0L) {
            count++
        }
        Log.e("page_erase","该升级文件，需要擦除：$count 次空间，每次0x1000个长度")
        for (i in 0 until count) {
            isResultCallBack = false
            woperation.send_data(WriterOperation.OTA_CMD_PAGE_ERASE, addr, null, 0,
                    mgattCharacteristic, bleclass)
            while (!isResultCallBack)
                addr += 0x1000
        }
        return 0
    }

    var isConnected = true
    private fun checkConnectState():Boolean{
        if (!isConnected) {
            runOnUiThread {
                OtaActiviy.mDialog.cancel()
                Toast.makeText(this, "连接断开",Toast.LENGTH_SHORT).show()
            }
            return false
        }
        return true
    }

    private fun updateLog(strValue:String) {
        if (strValue.equals("")) return
        val date = Date()
        val dateFormat = SimpleDateFormat("HH:mm:ss:SSS")
        // 主线程操作
        runOnUiThread {
            tvLog.text =  tvLog.text.toString().plus("\n${dateFormat.format(date)}::::${strValue}")
            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == SelectFileActivity.RESULT_CODE) {
            // 请求为 "选择文件"
            try {
                // 取得选择的文件名
                val sendFileName = data?.getStringExtra(SelectFileActivity.SEND_FILE_NAME)
                if (sendFileName != null && !sendFileName.equals(""))
                    tvFilePath.text = sendFileName
            } catch (e: Exception) {
                Log.e(ContentValues.TAG, "onActivityResult: " + "回调异常！")
                tvFilePath.text = "文件异常：${e.message}"
            }
        }
    }


}