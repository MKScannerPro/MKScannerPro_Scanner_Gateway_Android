package com.moko.mkscannergw.base;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.DisplayCutout;

import com.elvishew.xlog.XLog;
import com.moko.mkscannergw.activity.GuideActivity;
import com.moko.lib.scannerui.dialog.LoadingDialog;
import com.moko.lib.scannerui.dialog.LoadingMessageDialog;

import org.greenrobot.eventbus.EventBus;

import java.util.List;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

public abstract class BaseActivity<VM extends ViewBinding> extends FragmentActivity {
    protected VM mBind;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            Intent intent = new Intent(this, GuideActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            return;
        }
        getWindow().getDecorView().setOnApplyWindowInsetsListener((v, insets) -> {
            DisplayCutout cutout = insets.getDisplayCutout();
            if (cutout != null) {
                List<Rect> rects = cutout.getBoundingRects();
                if (rects.size() != 0) {
                    getWindow().getDecorView().setPadding(cutout.getSafeInsetLeft(), cutout.getSafeInsetTop(),
                            cutout.getSafeInsetRight(), cutout.getSafeInsetBottom());
                }
            }
            return insets;
        });
        mBind = getViewBinding();
        setContentView(mBind.getRoot());
        onCreate();
        EventBus.getDefault().register(this);
    }

    protected void onCreate(){}

    protected abstract VM getViewBinding();

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        XLog.i("onConfigurationChanged...");
        finish();
    }

    // 记录上次页面控件点击时间,屏蔽无效点击事件
    protected long mLastOnClickTime = 0;

    public boolean isWindowLocked() {
        long current = SystemClock.elapsedRealtime();
        if (current - mLastOnClickTime > 500) {
            mLastOnClickTime = current;
            return false;
        } else {
            return true;
        }
    }

    private LoadingDialog mLoadingDialog;

    public void showLoadingProgressDialog() {
        mLoadingDialog = new LoadingDialog();
        mLoadingDialog.show(getSupportFragmentManager());

    }

    public void dismissLoadingProgressDialog() {
        if (mLoadingDialog != null)
            mLoadingDialog.dismissAllowingStateLoss();
    }

    private LoadingMessageDialog mLoadingMessageDialog;

    public void showLoadingMessageDialog(String message) {
        mLoadingMessageDialog = new LoadingMessageDialog();
        mLoadingMessageDialog.setMessage(message);
        mLoadingMessageDialog.show(getSupportFragmentManager());

    }

    public void dismissLoadingMessageDialog() {
        if (mLoadingMessageDialog != null)
            mLoadingMessageDialog.dismissAllowingStateLoss();
    }

    public boolean isWriteStoragePermissionOpen() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    public boolean isLocationPermissionOpen() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
}
