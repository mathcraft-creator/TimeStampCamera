package com.mathcraft.timestampcamera

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * 원격(리모컨) 촬영 트리거 버스.
 *
 * - 볼륨 / 블루투스 셔터 리모컨 키: [MainActivity.dispatchKeyEvent] 가 이 버스로 전달한다.
 * - 갤럭시 S펜 버튼: [SpenRemoteController] 가 이 버스로 전달한다.
 *
 * 실제 촬영 함수([onTrigger])는 카메라 화면([CameraScreen])이 화면에 떠 있는 동안에만 등록된다.
 * 설정 화면 등 다른 화면에서는 등록이 해제되어 볼륨 키가 정상 동작한다.
 */
object RemoteShutterBus {

    private val main = Handler(Looper.getMainLooper())

    /** 카메라 화면이 등록하는 촬영 콜백. null 이면 원격 촬영 비활성 상태. */
    @Volatile
    var onTrigger: (() -> Unit)? = null

    /**
     * 볼륨/미디어 키를 촬영으로 가로챌지 여부.
     * "볼륨·블루투스 리모컨" 뿐 아니라 "S펜 버튼"도 켜져 있으면 true 다.
     * 이유: 상당수 갤럭시 기기에서 S펜 버튼 클릭이 BLE 를 통해 볼륨 키 이벤트로 전달되므로,
     * Pen Remote SDK 연결과 무관하게 이 경로가 있어야 확실히 촬영된다.
     */
    @Volatile
    var keyEventsEnabled: Boolean = false

    private var lastTriggerAt = 0L

    /** 짧은 시간 내 중복 입력(키 반복·양쪽 볼륨 동시 입력 등)은 무시하고 촬영을 요청한다. */
    fun trigger() {
        val now = System.currentTimeMillis()
        if (now - lastTriggerAt < DEBOUNCE_MS) return
        lastTriggerAt = now
        val cb = onTrigger ?: return
        main.post { cb() }
    }

    private const val DEBOUNCE_MS = 1000L
}

/**
 * 삼성 S펜 "펜 리모컨(Pen Remote)" SDK 를 리플렉션으로 감싼 래퍼.
 *
 * 이 SDK 는 Maven 에 없고 jar 로만 배포되므로, jar 가 없어도 앱은 컴파일된다.
 * 실행 시 SDK 클래스가 존재하고(갤럭시 + jar 포함) 기기가 S펜 버튼 기능을 지원하면
 * S펜 버튼 클릭을 [RemoteShutterBus.trigger] 로 연결한다.
 *
 * SDK 추가 방법 (이미 완료됨):
 *   삼성 개발자 사이트(Pen Remote SDK) 배포본의 spenremote-v1.0.1.jar 와
 *   sdk-v1.0.0.jar(SsdkVendorCheck 포함) 두 개를 app/libs/ 에 넣는다.
 *   build.gradle.kts 의 fileTree 가 자동 포함하고, proguard-rules.pro 가 이름을 보존한다.
 *
 * 주의: 이 SDK 의 connect() 는 `context instanceof Activity` 를 요구한다(아니면 IllegalArgumentException).
 * 그래서 전달받은 Context 를 Activity 로 언랩해서 사용한다. Activity 를 못 찾으면 SDK 는 건너뛰고,
 * 볼륨 키 경로([RemoteShutterBus.keyEventsEnabled])가 대신 촬영을 처리한다.
 */
class SpenRemoteController(context: Context) {

    private val activity: Activity? = context.findActivity()

    private var spenRemote: Any? = null
    private var unitManager: Any? = null
    private var connecting = false

    fun connect() {
        if (connecting || unitManager != null) return
        val act = activity ?: run {
            Log.i(TAG, "Activity 컨텍스트를 찾지 못해 S펜 SDK 연결을 건너뜁니다.")
            return
        }
        try {
            val remoteCls = Class.forName(CLS_REMOTE)
            val instance = remoteCls.getMethod("getInstance").invoke(null) ?: return
            spenRemote = instance

            val featureButton = remoteCls.getField("FEATURE_TYPE_BUTTON").getInt(null)
            val supported = remoteCls
                .getMethod("isFeatureEnabled", Int::class.javaPrimitiveType)
                .invoke(instance, featureButton) as? Boolean ?: false
            if (!supported) {
                Log.i(TAG, "이 기기는 S펜 버튼 기능을 지원하지 않습니다.")
                return
            }

            val callbackCls = Class.forName("$CLS_REMOTE\$ConnectionResultCallback")
            val callback = Proxy.newProxyInstance(
                callbackCls.classLoader, arrayOf(callbackCls)
            ) { proxy, method, args -> onConnectionCallback(proxy, method, args) }

            remoteCls.getMethod("connect", Context::class.java, callbackCls)
                .invoke(instance, act, callback)
            connecting = true
        } catch (t: Throwable) {
            Log.i(TAG, "S펜 리모컨 사용 불가: ${t.message}")
        }
    }

    fun disconnect() {
        try {
            val um = unitManager
            if (um != null) {
                val unit = buttonUnit(um)
                if (unit != null) {
                    um.javaClass
                        .getMethod("unregisterSpenEventListener", Class.forName(CLS_UNIT))
                        .invoke(um, unit)
                }
            }
            val act = activity
            if (spenRemote != null && act != null) {
                spenRemote!!.javaClass.getMethod("disconnect", Context::class.java)
                    .invoke(spenRemote, act)
            }
        } catch (t: Throwable) {
            Log.i(TAG, "S펜 리모컨 해제 중 오류: ${t.message}")
        } finally {
            unitManager = null
            spenRemote = null
            connecting = false
        }
    }

    private fun onConnectionCallback(proxy: Any?, method: Method, args: Array<out Any?>?): Any? {
        if (method.declaringClass == Any::class.java) return objectMethod(proxy, method, args)
        when (method.name) {
            "onSuccess" -> {
                connecting = false
                val um = args?.getOrNull(0) ?: return null
                unitManager = um
                registerButtonListener(um)
            }
            "onFailure" -> {
                connecting = false
                Log.i(TAG, "S펜 리모컨 연결 실패: ${args?.getOrNull(0)}")
            }
        }
        return null
    }

    private fun registerButtonListener(um: Any) {
        try {
            val listenerCls = Class.forName(CLS_EVENT_LISTENER)
            val spenEventCls = Class.forName(CLS_EVENT)
            val buttonEventCls = Class.forName(CLS_BUTTON_EVENT)
            val actionDown = buttonEventCls.getField("ACTION_DOWN").getInt(null)

            val listener = Proxy.newProxyInstance(
                listenerCls.classLoader, arrayOf(listenerCls)
            ) { proxy, method, args ->
                if (method.declaringClass == Any::class.java) {
                    objectMethod(proxy, method, args)
                } else {
                    if (method.name == "onEvent") {
                        val ev = args?.getOrNull(0)
                        if (ev != null) {
                            val be = buttonEventCls.getConstructor(spenEventCls).newInstance(ev)
                            val action = buttonEventCls.getMethod("getAction").invoke(be) as? Int
                            if (action == actionDown) RemoteShutterBus.trigger()
                        }
                    }
                    null
                }
            }

            val unit = buttonUnit(um) ?: return
            um.javaClass
                .getMethod("registerSpenEventListener", listenerCls, Class.forName(CLS_UNIT))
                .invoke(um, listener, unit)
        } catch (t: Throwable) {
            Log.i(TAG, "S펜 버튼 리스너 등록 실패: ${t.message}")
        }
    }

    private fun buttonUnit(um: Any): Any? {
        val unitCls = Class.forName(CLS_UNIT)
        val typeButton = unitCls.getField("TYPE_BUTTON").getInt(null)
        return um.javaClass
            .getMethod("getUnit", Int::class.javaPrimitiveType)
            .invoke(um, typeButton)
    }

    private fun objectMethod(proxy: Any?, method: Method, args: Array<out Any?>?): Any? =
        when (method.name) {
            "toString" -> "SpenRemoteProxy"
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.getOrNull(0)
            else -> null
        }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    companion object {
        private const val TAG = "SpenRemote"
        private const val CLS_REMOTE = "com.samsung.android.sdk.penremote.SpenRemote"
        private const val CLS_EVENT_LISTENER = "com.samsung.android.sdk.penremote.SpenEventListener"
        private const val CLS_UNIT = "com.samsung.android.sdk.penremote.SpenUnit"
        private const val CLS_EVENT = "com.samsung.android.sdk.penremote.SpenEvent"
        private const val CLS_BUTTON_EVENT = "com.samsung.android.sdk.penremote.ButtonEvent"
    }
}
