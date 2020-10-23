/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.bluetooth.le.activity;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.example.bluetooth.le.BluetoothLeClass;
import com.example.bluetooth.le.BluetoothLeClass.OnConnectingListener;
import com.example.bluetooth.le.BluetoothLeClass.OnDisconnectListener;
import com.example.bluetooth.le.adapter.LeDeviceListAdapter;
import com.example.bluetooth.le.R;
import com.example.bluetooth.le.BluetoothLeClass.OnDataAvailableListener;
import com.example.bluetooth.le.BluetoothLeClass.OnServiceDiscoverListener;
import com.example.bluetooth.le.UUIDInfo;
import com.example.bluetooth.le.utilInfo.ClsUtils;

/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
public class DeviceScanActivity extends Activity {
	private final static String TAG = DeviceScanActivity.class.getSimpleName();
	public BluetoothDevice nowSelectDevice;
	private LeDeviceListAdapter mLeDeviceListAdapter;
	/** 搜索BLE终端 */
	private BluetoothAdapter mBluetoothAdapter;
	/** 读写BLE终端 */
	public  BluetoothLeClass mBLE;
	private boolean mScanning;
	private Handler mHandler;
	private static final long SCAN_PERIOD = 3000;
	private ImageView imgSearch = null;
	private ProgressDialog dialog;
	private ListView blelv = null;
	private TextView restv = null;
	private static DeviceScanActivity deviceScanActivity;
	private static final int REQUEST_CODE_ACCESS_COARSE_LOCATION = 1;
	public static final int REQUEST_LOCATION_PERMISSION = 2;
	private RotateAnimation roanimation;
	public static DeviceScanActivity getInstance() {
		return deviceScanActivity;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// getActionBar().setTitle(R.string.title_devices);
		setContentView(R.layout.activity_main);
		deviceScanActivity = this;
		mHandler = new Handler();
		initlayout();
		if (!getPackageManager().hasSystemFeature(
				PackageManager.FEATURE_BLUETOOTH_LE)) {
			Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT)
					.show();
			finish();
		}
		final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = bluetoothManager.getAdapter();
		if (mBluetoothAdapter == null) {
			Toast.makeText(this, R.string.error_bluetooth_not_supported,
					Toast.LENGTH_SHORT).show();
			finish();
			return;
		}
		// 开启蓝牙
		if(!mBluetoothAdapter.isEnabled())
			mBluetoothAdapter.enable();

		if (Build.VERSION.SDK_INT >= 23) {//如果 API level 是大于等于 23(Android 6.0) 时
			//判断是否具有权限
			if (PackageManager.PERMISSION_GRANTED != this.checkSelfPermission(
					Manifest.permission.ACCESS_COARSE_LOCATION)) {
				//请求权限
				this.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
								Manifest.permission.ACCESS_FINE_LOCATION},
						REQUEST_CODE_ACCESS_COARSE_LOCATION);
			}
		}

		//开启位置服务，支持获取ble蓝牙扫描结果
		if (Build.VERSION.SDK_INT >= 23 && !isLocationOpen(getApplicationContext())) {
			Intent enableLocate = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
			startActivityForResult(enableLocate, REQUEST_LOCATION_PERMISSION);
		}

		mBLE = new BluetoothLeClass(this,mBluetoothAdapter);
		dialog = new ProgressDialog(this);
		dialog.setCanceledOnTouchOutside(false);
		dialog.setMessage("正在连接，请稍后...");

		receiveBLEBroadcast();

	}

	private void receiveBLEBroadcast() {
		IntentFilter intent = new IntentFilter();
		intent.addAction(BluetoothDevice.ACTION_FOUND);//搜索发现设备
		intent.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);//状态改变
		intent.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);//配对请求

		registerReceiver(searchDevices, intent);
	}




	@Override
	public void onRequestPermissionsResult(int requestCode,  String[] permissions,  int[] grantResults) {
		if (requestCode == REQUEST_CODE_ACCESS_COARSE_LOCATION) {
			if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				//用户允许改权限，0表示允许，-1表示拒绝 PERMISSION_GRANTED = 0， PERMISSION_DENIED = -1
				//permission was granted, yay! Do the contacts-related task you need to do.
				//这里进行授权被允许的处理
				Toast.makeText(this, "位置权限已打开", Toast.LENGTH_SHORT).show();
			} else {
				//permission denied, boo! Disable the functionality that depends on this permission.
				//这里进行权限被拒绝的处理
				Toast.makeText(this, "需要打开位置权限才可以搜索到Ble设备。", Toast.LENGTH_SHORT).show();
			}
		} else {
			super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		}
	}
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_LOCATION_PERMISSION) {
			if (isLocationOpen(getApplicationContext())) {
				Toast.makeText(this, "定位服务已打开", Toast.LENGTH_SHORT).show();
			} else {
				//若未开启位置信息功能，则退出该应用
				Toast.makeText(this, "需要打开定位服务才可以搜索到Ble设备", Toast.LENGTH_SHORT).show();
			}
		}

		super.onActivityResult(requestCode, resultCode, data);

	}
	/**
	 *判断位置信息是否开启
	 * @param context
	 * @return
	 */
	public static boolean isLocationOpen(final Context context){
		LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
		//gps定位
		boolean isGpsProvider = manager.isProviderEnabled(LocationManager.GPS_PROVIDER);
		//网络定位
		boolean isNetWorkProvider = manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
		return isGpsProvider|| isNetWorkProvider;
	}

	void initlayout() {

		DisplayMetrics dm = getResources().getDisplayMetrics();
		imgSearch = (ImageView) findViewById(R.id.search_img);
		blelv = (ListView) findViewById(R.id.blelv);
		restv = (TextView) findViewById(R.id.restv);
		TextView tvVersion = (TextView) findViewById(R.id.tvVersion);

		String strVersionInfo = "";
		try {
			PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			strVersionInfo = packageInfo.versionName;
		} catch (PackageManager.NameNotFoundException e) {
			e.printStackTrace();
		}
		tvVersion.setText("V "+strVersionInfo);

		roanimation = new RotateAnimation(0f, 360f, Animation.RELATIVE_TO_SELF,
				0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
		LinearInterpolator lir = new LinearInterpolator();
		roanimation.setInterpolator(lir);
		roanimation.setDuration(1000);
		roanimation.setRepeatCount(-1);
		imgSearch.startAnimation(roanimation);

		imgSearch.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				scanLeDevice(!mScanning);
			}
		});

		mLeDeviceListAdapter = new LeDeviceListAdapter(this);
		blelv.setAdapter(mLeDeviceListAdapter);
		blelv.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				scanLeDevice(false);
				nowSelectDevice = mLeDeviceListAdapter.getDevice(position);
				dialog.show();
				mBLE.connect(nowSelectDevice,OnConnectListener);
			}
		});

	}

	@Override
	protected void onResume() {
		super.onResume();
		scanLeDevice(true);
	}

	@Override
	protected void onPause() {
		super.onPause();
		scanLeDevice(false);
	}


	BluetoothLeScanner scanner = null;
	/** 启动/停止 搜索设备 */
	private void scanLeDevice(final boolean enable) {
		if (enable) {
//			mHandler.postDelayed(new Runnable() {
//				@Override
//				public void run() {
//					scanLeDevice(false);
//				}
//			}, SCAN_PERIOD);
			imgSearch.startAnimation(roanimation);
			mScanning = true;
			restv.setText("Searching...");
			mLeDeviceListAdapter.clear();
//			mBluetoothAdapter.startDiscovery();
			mBluetoothAdapter.startLeScan(leScanCallback);
			if (scanner == null)
				scanner = mBluetoothAdapter.getBluetoothLeScanner();
			scanner.startScan(scanCallback);
//			scanner.startScan(null,null,scanCallback);

		} else {
			mScanning = false;
			imgSearch.clearAnimation();
			restv.setText("Stop the search");
//			mBluetoothAdapter.cancelDiscovery();
			mBluetoothAdapter.stopLeScan(leScanCallback);

			if (scanner != null)
				scanner.stopScan(scanCallback);

		}
	}

	private ScanCallback scanCallback = new ScanCallback() {
		@Override
		public void onScanResult(int callbackType, ScanResult result) {
			super.onScanResult(callbackType, result);
			BluetoothDevice bluetoothDevice = result.getDevice();
			Log.e(TAG, "onScanResult: "+result.toString());
			String strName = bluetoothDevice.getName();
			if (strName != null && strName.length() > 0) {
				mLeDeviceListAdapter.addDevice(bluetoothDevice,result.getRssi()+"");
			}
//			mLeDeviceListAdapter.addDevice(bluetoothDevice,result.getRssi()+"");

		}

		@Override
		public void onBatchScanResults(List<ScanResult> results) {
			super.onBatchScanResults(results);
		}

		@Override
		public void onScanFailed(int errorCode) {
			super.onScanFailed(errorCode);
		}
	};

	private BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
		@Override
		public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
			String strName = device.getName();
			if (strName != null && strName.length() > 0) {
				mLeDeviceListAdapter.addDevice(device,rssi+"");
			}
//			mLeDeviceListAdapter.addDevice(device,rssi+"");
		}
	};


	// 断开或连接 状态发生变化时调用
	private BluetoothLeClass.OnConnectListener OnConnectListener = new BluetoothLeClass.OnConnectListener() {
		@Override
		public void onConnectting(BluetoothGatt gatt, int status, int newState) {
			Log.e(TAG,"status: "+status+",newState:"+newState);
		}

		@Override
		public void onConnected(BluetoothGatt gatt, int status, int newState) {
			Log.e(TAG,"status: "+status+",newState:"+newState);
			if (newState == BluetoothProfile.STATE_CONNECTED) {
				dialog.dismiss();
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						startActivity(new Intent(DeviceScanActivity.this,TRXActivity.class));
					}
				});

			}
		}

		@Override
		public void onDisconnect(BluetoothGatt gatt, int status, int newState) {
			Log.e(TAG,"status: "+status+",newState:"+newState);
			dialog.dismiss();
		}
	};




	String PIN = "0000";
	/**
	 * 蓝牙接收广播
	 */
	private BroadcastReceiver searchDevices = new BroadcastReceiver() {
		//接收
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			Bundle b = intent.getExtras();
			Object[] lstName = b.keySet().toArray();

			// 显示所有收到的消息及其细节
			for (int i = 0; i < lstName.length; i++) {
				String keyName = lstName[i].toString();
				Log.e("bluetooth", keyName + ">>>" + String.valueOf(b.get(keyName)));
			}
			BluetoothDevice device;
			// 搜索发现设备时，取得设备的信息；注意，这里有可能重复搜索同一设备
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				String strRSSI = b.get(BluetoothDevice.EXTRA_RSSI)+"";
				String strName = device.getName();
				if (strName != null && strName.length() > 0) {
					mLeDeviceListAdapter.addDevice(device,strRSSI);
				}
//				mLeDeviceListAdapter.addDevice(device,strRSSI);

			}
			//状态改变时
			else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
				device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				switch (device.getBondState()) {
					case BluetoothDevice.BOND_BONDING://正在配对
						Log.e("BlueToothTestActivity", "正在配对......");
						Toast.makeText(DeviceScanActivity.this,"正在配对......",Toast.LENGTH_SHORT).show();
						break;
					case BluetoothDevice.BOND_BONDED://配对结束
						Log.e("BlueToothTestActivity", "配对结束");
						Toast.makeText(DeviceScanActivity.this,"完成配对",Toast.LENGTH_SHORT).show();
//						mBLE.connect(device,OnConnectListener);
						break;
					case BluetoothDevice.BOND_NONE://取消配对/未配对
						Log.e("BlueToothTestActivity", "取消配对/未配对......");
						Toast.makeText(DeviceScanActivity.this,"取消配对",Toast.LENGTH_SHORT).show();
//						mBLE.connect(device,OnConnectListener);
					default:
						break;
				}
				mLeDeviceListAdapter.updateDevice(device);
			}
			// 配对请求
			else if (BluetoothDevice.ACTION_PAIRING_REQUEST.equals(action)) {
				device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				Log.e("BlueToothTestActivity", "确认配对......");
				try {
					//1.确认配对
					ClsUtils.setPairingConfirmation(device.getClass(), device, true);
					//2.终止有序广播
//					Log.e("order...", "isOrderedBroadcast:"+isOrderedBroadcast()+",isInitialStickyBroadcast:"+isInitialStickyBroadcast());
//					abortBroadcast();//如果没有将广播终止，则会出现一个一闪而过的配对框。
					//3.调用setPin方法进行配对...
					boolean ret = ClsUtils.setPin(device.getClass(), device, PIN);

				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

		}
	};

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterReceiver(searchDevices);
	}
}