# Android-Record
视频录制

1.注册插件
	assets\data\dcloud_properties.xml添加
	<feature name="ourVideoRec" value="com.ourstu.opensnsh5.record.VideoRecordFeature"/>
2.JS中桥接
document.addEventListener( "plusready",  function(){
    var _BARCODE = 'ourVideoRec',
		B = window.plus.bridge;
    var ourVideoRec =
    {
		startRec : function (Argus, successCallback, errorCallback )
		{
			var success = typeof successCallback !== 'function' ? null : function(args)
			{
				successCallback(args);
			},
			fail = typeof errorCallback !== 'function' ? null : function(code)
			{
				errorCallback(code);
			};
			callbackID = B.callbackId(success, fail);
			return B.exec(_BARCODE, "startRec", [callbackID, Argus]);
		}
    };
    window.plus.ourVideoRec = ourVideoRec;
}, true );
function startRec() {
    plus.ourVideoRec.startRec({quality:2, maximum:3600, name:"test_video"}, function( result ) {
        var fileName = result;
        if(~fileName.indexOf("/")){
            filename = fileName.substring(fileName.lastIndexOf("/")+1,fileName.length);
        }
        alert( result );																			//本地录制视频地址
    },function(result){
        alert("ERROR"+result)
    });
}