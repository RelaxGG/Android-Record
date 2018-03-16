package com.ourstu.opensnsh5.record;


import android.content.Intent;

import org.json.JSONArray;

import io.dcloud.common.DHInterface.IWebview;
import io.dcloud.common.DHInterface.StandardFeature;
import io.dcloud.common.util.JSUtil;

import static io.dcloud.common.DHInterface.IApp.ABS_PUBLIC_DOCUMENTS_DIR;

public class VideoRecordFeature extends StandardFeature{

    private IWebview mainWebView;
    private String callbackId;
    private boolean cancel = true;//是否需要创建onActivityResult监听器
    private String path = null;

    public void startRec(IWebview pWebView, JSONArray array)
    {
        String vdDir = pWebView.obtainApp().getPathByType(ABS_PUBLIC_DOCUMENTS_DIR);
        mainWebView = pWebView;
        callbackId =  array.optString(0);
        Intent intent = new Intent(pWebView.getActivity(),RecordActivity.class);
        intent.putExtra("config",array.optString(1));
        intent.putExtra("dir",vdDir);
        if(cancel){
            registerSysEvent(pWebView,SysEventType.onActivityResult);
            cancel = false;
        }
        pWebView.getActivity().startActivityForResult(intent,1001);
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    protected void onActivityResult(int i, int i1, Intent intent) {
        //Todo 有几率会无限触发此事件BUG
        unregisterSysEvent(mainWebView,SysEventType.onActivityResult);
        cancel = true;
        if(intent!=null&&(path==null||!path.equals(intent.getStringExtra("path")))){
            path = intent.getStringExtra("path");
            JSUtil.execCallback(mainWebView, callbackId, path, JSUtil.OK, false);
        }else{
            if(intent!=null){
                registerSysEvent(mainWebView,SysEventType.onActivityResult);
            }else{
                JSUtil.execCallback(mainWebView, callbackId, "cancel", JSUtil.ERROR, false);
            }
        }
    }
}
