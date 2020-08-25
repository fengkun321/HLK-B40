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
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import com.example.bluetooth.le.BluetoothLeClass;
import com.example.bluetooth.le.BluetoothLeClass.OnConnectListener;
import com.example.bluetooth.le.BluetoothLeClass.OnConnectingListener;
import com.example.bluetooth.le.BluetoothLeClass.OnDisconnectListener;
import com.example.bluetooth.le.LeDeviceListAdapter;
import com.example.bluetooth.le.R;
import com.example.bluetooth.le.BluetoothLeClass.OnDataAvailableListener;
import com.example.bluetooth.le.BluetoothLeClass.OnServiceDiscoverListener;
import com.example.bluetooth.le.UUIDInfo;

/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
public class DeviceScanActivity extends Activity {
	private final static String TAG = DeviceScanActivity.class.getSimpleName();
	private final static String UUID_KEY_DATA = "0000ff01-0000-1000-8000-00805f9b34fb";
	public List<BluetoothGattService> gattlist;
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
	private Dialog dialog;
	private int width, heigh;
	private float density;
	private ListView blelv = null;
	private TextView restv = null;
	private boolean connectfailed = false;
	private boolean connect = true;
	private static String bleaddr;
	private boolean timeout = false;
	private int connectTime = 0;
	private static DeviceScanActivity deviceScanActivity;
	private static final int REQUEST_CODE_ACCESS_COARSE_LOCATION = 1;
	public static final int REQUEST_LOCATION_PERMISSION = 2;

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
				this.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
						REQUEST_CODE_ACCESS_COARSE_LOCATION);
			}
		}

		//开启位置服务，支持获取ble蓝牙扫描结果
		if (Build.VERSION.SDK_INT >= 23 && !isLocationOpen(getApplicationContext())) {
			Intent enableLocate = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
			startActivityForResult(enableLocate, REQUEST_LOCATION_PERMISSION);
		}

		mBLE = new BluetoothLeClass(this,mBluetoothAdapter);
		// 发现BLE终端的Service时回调
		mBLE.setOnServiceDiscoverListener(mOnServiceDiscover);
		// 收到BLE终端数据交互的事件
		mBLE.setOnDataAvailableListener(mOnDataAvailable);
		dialog = new Dialog(this,R.style.dialog);  
        dialog.setContentView(R.layout.connetingdiglog); 
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
				//Log.("fang", " request location permission success");
				//Android6.0需要动态申请权限
				Toast.makeText(this, "定位服务已打开", Toast.LENGTH_SHORT).show();
				/*if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
						!= PackageManager.PERMISSION_GRANTED) {
					//请求权限
					ActivityCompat.requestPermissions(this,
							new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
									Manifest.permission.ACCESS_FINE_LOCATION},
							IntentCons.REQUEST_LOCATION_PERMISSION);
					if (ActivityCompat.shouldShowRequestPermissionRationale(this,
							Manifest.permission.ACCESS_COARSE_LOCATION)) {
						//判断是否需要解释
						DialogUtils.shortT(getApplicationContext(), "需要蓝牙权限");
					}
				}*/

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
		width = dm.widthPixels;
		heigh = dm.heightPixels;
		density = dm.density;
		imgSearch = (ImageView) findViewById(R.id.search_img);
		blelv = (ListView) findViewById(R.id.blelv);
		restv = (TextView) findViewById(R.id.restv);

		roanimation = new RotateAnimation(0f, 360f, Animation.RELATIVE_TO_SELF,
				0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
		LinearInterpolator lir = new LinearInterpolator();
		roanimation.setInterpolator(lir);
		roanimation.setDuration(1000);
		roanimation.setRepeatCount(-1);
		imgSearch.startAnimation(roanimation);

		imgSearch.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (mScanning)
					return;
				scanLeDevice(true);
			}
		});

		mLeDeviceListAdapter = new LeDeviceListAdapter(this);
		blelv.setAdapter(mLeDeviceListAdapter);
		blelv.setOnItemClickListener(new OnItemClickListenerimp());

	}

	private int dp2px(int dpValue) {
		return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
				dpValue, getResources().getDisplayMetrics());
	}

	private int sp2px(int spValue) {
		return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
				spValue, getResources().getDisplayMetrics());
	}

	Handler myhandler = new Handler() {
		public void handleMessage(android.os.Message msg) {
			switch (msg.what) {
			case 2:
				System.out.println("con " + connectfailed);
				if (connectfailed == false) {
					connect = false;

				} else {
					System.out.println("addrsss " + bleaddr);
					if (connect) {
						connectTime ++;
						if(connectTime < 2){
							
							mBLE.connect(bleaddr);
							timeout = false;
						}else{
							connectTime = 0;
							dialog.dismiss();
						}
					}
				}
				
				connectfailed = false;
				break;
			}

		};

	};

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


	class OnItemClickListenerimp implements OnItemClickListener {

		@Override
		public void onItemClick(AdapterView<?> arg0, View v, int position,
				long arg3) {
			nowSelectDevice = mLeDeviceListAdapter
					.getDevice(position);
			if (nowSelectDevice == null)
				return;
			scanLeDevice(false);
			dialog.show();
			connectfailed = false;
			connect = true;
			timeout = false;
			bleaddr = nowSelectDevice.getAddress();
			mBLE.connect(bleaddr);

		}

	}

	/** 启动/停止 搜索设备 */
	private void scanLeDevice(final boolean enable) {
		if (enable) {
			mHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					scanLeDevice(false);
				}
			}, SCAN_PERIOD);
			imgSearch.startAnimation(roanimation);
			mScanning = true;
			restv.setText("Searching...");
			mLeDeviceListAdapter.clear();
			mBluetoothAdapter.startLeScan(mLeScanCallback);
		} else {
			mScanning = false;
			imgSearch.clearAnimation();
			restv.setText("Stop the search");
			mBluetoothAdapter.stopLeScan(mLeScanCallback);
		}
	}

	/**
	 * 搜索到BLE终端服务的事件
	 */
	private BluetoothLeClass.OnServiceDiscoverListener mOnServiceDiscover = new OnServiceDiscoverListener() {

		@Override
		public void onServiceDiscover(BluetoothGatt gatt) {
			displayGattServices(mBLE.getSupportedGattServices());

		}

	};

	void read_data(String action) {
		Intent intent = new Intent(action);
		this.sendBroadcast(intent);

	}

	/**
	 * 收到BLE终端数据交互的事件
	 */
	private BluetoothLeClass.OnDataAvailableListener mOnDataAvailable = new OnDataAvailableListener() {

		/**
		 * BLE终端数据被读的事件
		 */
		@Override
		public void onCharacteristicRead(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				read_data("leddata");
				//System.out.println("onCharRead " + gatt.getDevice().getName()
				//		+ " read " + characteristic.getUuid().toString()
					//	+ " -> "
					//	+ Utils.bytesToHexString(characteristic.getValue()));
			}

		}

		/**
		 * 收到BLE终端写入数据回调
		 */
		public void onCharacteristicWrite(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic) {
			// baseaddr = characteristic.getValue();
			// rf.savefile(baseaddr, baseaddr.length);
			read_data("ledack");
			// _txtRead.append(count++ +" " + '\n');
		}
	};
	private BluetoothLeClass.OnConnectingListener mOnConnecting = new OnConnectingListener() {

		@Override
		public void onConnecting(BluetoothGatt gatt) {
			System.out.println("connecting");
			blelv.setEnabled(false);
		}

	};

	private BluetoothLeClass.OnDisconnectListener mOnDisconnect = new OnDisconnectListener() {

		@Override
		public void onDisconnect(BluetoothGatt gatt) {

			//取消bond
			/*if( gatt.getDevice().getBondState() == BOND_BONDED) {
				try {
					Method m = gatt.getDevice().getClass().getMethod("removeBond", (Class[]) null);
					m.invoke(gatt.getDevice(), (Object[]) null);
				}catch (Exception e) { Log.e(TAG, e.getMessage()); }
			}*/
			connectfailed = true;
		}

	};
	// Device scan callback.
	private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

		@Override
		public void onLeScan(final BluetoothDevice device, final int rssi,
							 byte[] scanRecord) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					mLeDeviceListAdapter.addDevice(device,rssi+"");
					mLeDeviceListAdapter.notifyDataSetChanged();
				}
			});
		}
	};
	private RotateAnimation roanimation;

	private void displayGattServices(List<BluetoothGattService> gattServices) {
		if ((gattServices == null) || (timeout == true))
			return;
		dialog.dismiss();
		gattlist = gattServices;

		initServiceAndChara();

		Intent intent = new Intent(DeviceScanActivity.this,TestDataActivity.class);
		startActivity(intent);



		//Intent intent = new Intent(DeviceScanActivity.this,
		//		LedctrActivity.class);
		//startActivity(intent);
		/*
		 * for (BluetoothGattService gattService : gattServices) {
		 * //-----Service的字段信息-----// int type = gattService.getType();
		 * System.out.println("-->service type:"+Utils.getServiceType(type));
		 * System
		 * .out.println("-->includedServices size:"+gattService.getIncludedServices
		 * ().size());
		 * System.out.println("-->service uuid:"+gattService.getUuid());
		 * 
		 * //-----Characteristics的字段信息-----// List<BluetoothGattCharacteristic>
		 * gattCharacteristics =gattService.getCharacteristics(); for (final
		 * BluetoothGattCharacteristic gattCharacteristic: gattCharacteristics)
		 * { System.out.println("---->char uuid:"+gattCharacteristic.getUuid());
		 * 
		 * int permission = gattCharacteristic.getPermissions();
		 * System.out.println
		 * ("---->char permission:"+Utils.getCharPermission(permission));
		 * 
		 * int property = gattCharacteristic.getProperties();
		 * System.out.println(
		 * "---->char property:"+Utils.getCharPropertie(property));
		 * 
		 * byte[] data = gattCharacteristic.getValue(); if (data != null &&
		 * data.length > 0) { System.out.println("---->char value:"+new
		 * String(data)); }
		 * 
		 * //UUID_KEY_DATA是可以跟蓝牙模块串口通信的Characteristic
		 * if(gattCharacteristic.getUuid().toString().equals(UUID_KEY_DATA)){
		 * //测试读取当前Characteristic数据，会触发mOnDataAvailable.onCharacteristicRead()
		 * mHandler.postDelayed(new Runnable() {
		 * 
		 * @Override public void run() {
		 * mBLE.readCharacteristic(gattCharacteristic); } }, 500);
		 * 
		 * //接受Characteristic被写的通知,收到蓝牙模块的数据后会触发mOnDataAvailable.
		 * onCharacteristicWrite()
		 * mBLE.setCharacteristicNotification(gattCharacteristic, true);
		 * //设置数据内容 gattCharacteristic.setValue("send data->"); //往蓝牙模块写入数据
		 * mBLE.writeCharacteristic(gattCharacteristic); }
		 * 
		 * //-----Descriptors的字段信息-----// List<BluetoothGattDescriptor>
		 * gattDescriptors = gattCharacteristic.getDescriptors(); for
		 * (BluetoothGattDescriptor gattDescriptor : gattDescriptors) {
		 * System.out.println("-------->desc uuid:" + gattDescriptor.getUuid());
		 * int descPermission = gattDescriptor.getPermissions();
		 * System.out.println("-------->desc permission:"+
		 * Utils.getDescPermission(descPermission));
		 * 
		 * byte[] desData = gattDescriptor.getValue(); if (desData != null &&
		 * desData.length > 0) { System.out.println("-------->desc value:"+ new
		 * String(desData)); } } } }
		 */

	}


	public ArrayList<UUIDInfo> serverList = new ArrayList<>();
	public HashMap<String,ArrayList<UUIDInfo>> readCharaMap = new HashMap<>();
	public HashMap<String,ArrayList<UUIDInfo>> writeCharaMap = new HashMap<>();

	private void initServiceAndChara(){
		serverList.clear();
		readCharaMap.clear();
		writeCharaMap.clear();
		for (BluetoothGattService bluetoothGattService:gattlist){
			UUIDInfo serverInfo = new UUIDInfo(bluetoothGattService.getUuid());
			serverInfo.setStrCharactInfo("[Server]");
			serverList.add(serverInfo);
			ArrayList<UUIDInfo> readArray = new ArrayList<>();
			ArrayList<UUIDInfo> writeArray = new ArrayList<>();
			List<BluetoothGattCharacteristic> characteristics=bluetoothGattService.getCharacteristics();
			for (BluetoothGattCharacteristic characteristic:characteristics){
				int charaProp = characteristic.getProperties();
				boolean isRead = false;
				boolean isWrite = false;
				// 具备读的特征
				String strReadCharactInfo = "";
				// 具备写的特征
				String strWriteCharactInfo = "";
				if ((charaProp & BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
					isRead = true;
					strReadCharactInfo += "[PROPERTY_READ]";
					Log.e(TAG,"read_chara="+characteristic.getUuid()+"----read_service="+bluetoothGattService.getUuid());
				}
				if ((charaProp & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) {
					isWrite = true;
					strWriteCharactInfo += "[PROPERTY_WRITE]";
					Log.e(TAG,"write_chara="+characteristic.getUuid()+"----write_service="+bluetoothGattService.getUuid());
				}
				if ((charaProp & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) > 0) {
					isWrite = true;
					strWriteCharactInfo += "[PROPERTY_WRITE_NO_RESPONSE]";
					Log.e(TAG,"write_chara="+characteristic.getUuid()+"----write_service="+bluetoothGattService.getUuid());
				}
				if ((charaProp & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
					isRead = true;
					strReadCharactInfo += "[PROPERTY_NOTIFY]";
					Log.e(TAG,"notify_chara="+characteristic.getUuid()+"----notify_service="+bluetoothGattService.getUuid());
				}
				if ((charaProp & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0) {
					isRead = true;
					strReadCharactInfo += "[PROPERTY_INDICATE]";
					Log.e(TAG,"indicate_chara="+characteristic.getUuid()+"----indicate_service="+bluetoothGattService.getUuid());
				}
				if (isRead) {
					UUIDInfo uuidInfo = new UUIDInfo(characteristic.getUuid());
					uuidInfo.setStrCharactInfo(strReadCharactInfo);
					uuidInfo.setBluetoothGattCharacteristic(characteristic);
					readArray.add(uuidInfo);
				}
				if (isWrite) {
					UUIDInfo uuidInfo = new UUIDInfo(characteristic.getUuid());
					uuidInfo.setStrCharactInfo(strWriteCharactInfo);
					uuidInfo.setBluetoothGattCharacteristic(characteristic);
					writeArray.add(uuidInfo);
				}

				readCharaMap.put(bluetoothGattService.getUuid().toString(),readArray);
				writeCharaMap.put(bluetoothGattService.getUuid().toString(),writeArray);
			}
		}
	}

}