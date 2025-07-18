package com.moko.mkscannergw.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import com.moko.mkscannergw.AppConstants;
import com.moko.mkscannergw.R;
import com.moko.mkscannergw.base.BaseActivity;
import com.moko.mkscannergw.databinding.ActivityModifyDeviceNameBinding;
import com.moko.mkscannergw.db.DBTools;
import com.moko.mkscannergw.entity.MokoDevice;
import com.moko.lib.scannerui.utils.ToastUtils;
import com.moko.lib.mqtt.event.MQTTConnectionCompleteEvent;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;


public class ModifyNameActivity extends BaseActivity<ActivityModifyDeviceNameBinding> {
    private final String FILTER_ASCII = "[ -~]*";
    public static String TAG = ModifyNameActivity.class.getSimpleName();

    private MokoDevice device;
    private InputFilter filter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBind = ActivityModifyDeviceNameBinding.inflate(getLayoutInflater());
        setContentView(mBind.getRoot());
        device = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        filter = (source, start, end, dest, dstart, dend) -> {
            if (!(source + "").matches(FILTER_ASCII)) {
                return "";
            }

            return null;
        };
        mBind.etNickName.setText(device.nickName);
        mBind.etNickName.setSelection(mBind.etNickName.getText().toString().length());
        mBind.etNickName.setFilters(new InputFilter[]{filter, new InputFilter.LengthFilter(20)});
        mBind.etNickName.postDelayed(new Runnable() {
            @Override
            public void run() {
                InputMethodManager inputManager = (InputMethodManager) mBind.etNickName.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                inputManager.showSoftInput(mBind.etNickName, 0);
            }
        }, 300);
    }

    @Override
    protected ActivityModifyDeviceNameBinding getViewBinding() {
        return ActivityModifyDeviceNameBinding.inflate(getLayoutInflater());
    }


    public void modifyDone(View view) {
        String nickName = mBind.etNickName.getText().toString();
        if (TextUtils.isEmpty(nickName)) {
            ToastUtils.showToast(this, R.string.modify_device_name_empty);
            return;
        }
        device.nickName = nickName;
        DBTools.getInstance(this).updateDevice(device);
        // 跳转首页，刷新数据
        Intent intent = new Intent(this, ScannerMainActivity.class);
        intent.putExtra(AppConstants.EXTRA_KEY_FROM_ACTIVITY, TAG);
        intent.putExtra(AppConstants.EXTRA_KEY_DEVICE_ID, device.deviceId);
        startActivity(intent);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return false;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTConnectionCompleteEvent(MQTTConnectionCompleteEvent event) {
    }
}
