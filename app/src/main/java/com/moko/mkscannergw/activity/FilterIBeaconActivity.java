package com.moko.mkscannergw.activity;


import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.lib.mqtt.MQTTSupport;
import com.moko.mkscannergw.AppConstants;
import com.moko.mkscannergw.base.BaseActivity;
import com.moko.mkscannergw.databinding.ActivityFilterIbeaconBinding;
import com.moko.mkscannergw.entity.MQTTConfig;
import com.moko.mkscannergw.entity.MokoDevice;
import com.moko.mkscannergw.utils.SPUtiles;
import com.moko.lib.scannerui.utils.ToastUtils;
import com.moko.support.scannergw.MQTTConstants;
import com.moko.support.scannergw.entity.FilterIBeacon;
import com.moko.support.scannergw.entity.MsgConfigResult;
import com.moko.support.scannergw.entity.MsgDeviceInfo;
import com.moko.support.scannergw.entity.MsgReadResult;
import com.moko.lib.mqtt.event.DeviceOnlineEvent;
import com.moko.lib.mqtt.event.MQTTMessageArrivedEvent;
import com.moko.support.scannergw.handler.MQTTMessageAssembler;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;

public class FilterIBeaconActivity extends BaseActivity<ActivityFilterIbeaconBinding> {

    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;

    public Handler mHandler;

    @Override
    protected void onCreate() {
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        mHandler = new Handler(Looper.getMainLooper());
        showLoadingProgressDialog();
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            finish();
        }, 30 * 1000);
        getFilterIBeacon();
    }

    @Override
    protected ActivityFilterIbeaconBinding getViewBinding() {
        return ActivityFilterIbeaconBinding.inflate(getLayoutInflater());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTMessageArrivedEvent(MQTTMessageArrivedEvent event) {
        // 更新所有设备的网络状态
        final String topic = event.getTopic();
        final String message = event.getMessage();
        if (TextUtils.isEmpty(message))
            return;
        int msg_id;
        try {
            JsonObject object = new Gson().fromJson(message, JsonObject.class);
            JsonElement element = object.get("msg_id");
            msg_id = element.getAsInt();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        if (msg_id == MQTTConstants.READ_MSG_ID_FILTER_IBEACON) {
            Type type = new TypeToken<MsgReadResult<FilterIBeacon>>() {
            }.getType();
            MsgReadResult<FilterIBeacon> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.deviceId.equals(result.device_info.device_id)) {
                return;
            }
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            mBind.cbIbeacon.setChecked(result.data.onOff == 1);
            mBind.etIbeaconUuid.setText(result.data.uuid);
            mBind.etIbeaconMajorMin.setText(String.valueOf(result.data.min_major));
            mBind.etIbeaconMajorMax.setText(String.valueOf(result.data.max_major));
            mBind.etIbeaconMinorMin.setText(String.valueOf(result.data.min_minor));
            mBind.etIbeaconMinorMax.setText(String.valueOf(result.data.max_minor));
        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_FILTER_IBEACON) {
            Type type = new TypeToken<MsgConfigResult>() {
            }.getType();
            MsgConfigResult result = new Gson().fromJson(message, type);
            if (!mMokoDevice.deviceId.equals(result.device_info.device_id)) {
                return;
            }
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            if (result.result_code == 0) {
                ToastUtils.showToast(this, "Set up succeed");
            } else {
                ToastUtils.showToast(this, "Set up failed");
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDeviceOnlineEvent(DeviceOnlineEvent event) {
        String deviceId = event.getMac();
        if (!mMokoDevice.deviceId.equals(deviceId)) {
            return;
        }
        boolean online = event.isOnline();
        if (!online) {
            finish();
        }
    }

    public void back(View view) {
        finish();
    }

    private void getFilterIBeacon() {
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = mMokoDevice.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }
        MsgDeviceInfo deviceInfo = new MsgDeviceInfo();
        deviceInfo.device_id = mMokoDevice.deviceId;
        deviceInfo.mac = mMokoDevice.mac;
        String message = MQTTMessageAssembler.assembleReadFilterIBeacon(deviceInfo);
        try {
            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.READ_MSG_ID_FILTER_IBEACON, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void onBack(View view) {
        finish();
    }

    public void onSave(View view) {
        if (isWindowLocked())
            return;
        if (isValid()) {
            mHandler.postDelayed(() -> {
                dismissLoadingProgressDialog();
                ToastUtils.showToast(this, "Set up failed");
            }, 30 * 1000);
            showLoadingProgressDialog();
            saveParams();
        }
    }


    private void saveParams() {
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = mMokoDevice.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }
        MsgDeviceInfo deviceInfo = new MsgDeviceInfo();
        deviceInfo.device_id = mMokoDevice.deviceId;
        deviceInfo.mac = mMokoDevice.mac;

        FilterIBeacon filterIBeacon = new FilterIBeacon();
        filterIBeacon.onOff = mBind.cbIbeacon.isChecked() ? 1 : 0;
        filterIBeacon.min_major = Integer.parseInt(mBind.etIbeaconMajorMin.getText().toString());
        filterIBeacon.max_major = Integer.parseInt(mBind.etIbeaconMajorMax.getText().toString());
        filterIBeacon.min_minor = Integer.parseInt(mBind.etIbeaconMinorMin.getText().toString());
        filterIBeacon.max_minor = Integer.parseInt(mBind.etIbeaconMinorMax.getText().toString());
        filterIBeacon.uuid = mBind.etIbeaconUuid.getText().toString();

        String message = MQTTMessageAssembler.assembleWriteFilterIBeacon(deviceInfo, filterIBeacon);
        try {
            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.CONFIG_MSG_ID_FILTER_IBEACON, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private boolean isValid() {
        final String uuid = mBind.etIbeaconUuid.getText().toString();
        final String majorMin = mBind.etIbeaconMajorMin.getText().toString();
        final String majorMax = mBind.etIbeaconMajorMax.getText().toString();
        final String minorMin = mBind.etIbeaconMinorMin.getText().toString();
        final String minorMax = mBind.etIbeaconMinorMax.getText().toString();
        if (!TextUtils.isEmpty(uuid)) {
            int length = uuid.length();
            if (length % 2 != 0) {
                ToastUtils.showToast(this, "Para Error");
                return false;
            }
        }
        if (!TextUtils.isEmpty(majorMin) && !TextUtils.isEmpty(majorMax)) {
            if (Integer.parseInt(majorMin) > 65535) {
                ToastUtils.showToast(this, "Para Error");
                return false;
            }
            if (Integer.parseInt(majorMax) > 65535) {
                ToastUtils.showToast(this, "Para Error");
                return false;
            }
            if (Integer.parseInt(majorMax) < Integer.parseInt(majorMin)) {
                ToastUtils.showToast(this, "Para Error");
                return false;
            }
        } else if (TextUtils.isEmpty(majorMin) || TextUtils.isEmpty(majorMax)) {
            ToastUtils.showToast(this, "Para Error");
            return false;
        }

        if (!TextUtils.isEmpty(minorMin) && !TextUtils.isEmpty(minorMax)) {
            if (Integer.parseInt(minorMin) > 65535) {
                ToastUtils.showToast(this, "Para Error");
                return false;
            }
            if (Integer.parseInt(minorMax) > 65535) {
                ToastUtils.showToast(this, "Para Error");
                return false;
            }
            if (Integer.parseInt(minorMax) < Integer.parseInt(minorMin)) {
                ToastUtils.showToast(this, "Para Error");
                return false;
            }
        } else if (TextUtils.isEmpty(minorMin) || TextUtils.isEmpty(minorMax)) {
            ToastUtils.showToast(this, "Para Error");
            return false;
        }
        return true;
    }
}
