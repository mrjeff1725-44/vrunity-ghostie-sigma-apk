#include <android/native_activity.h>
#include <android/native_window.h>
#include <jni.h>
#include <stdatomic.h>

static _Atomic int resumed = 0;
static _Atomic int windowReady = 0;
static _Atomic int destroyed = 0;

static void onResume(ANativeActivity *activity) { atomic_store(&resumed, 1); }
static void onPause(ANativeActivity *activity) { atomic_store(&resumed, 0); }
static void onWindow(ANativeActivity *activity, ANativeWindow *window) {
    atomic_store(&windowReady, window && ANativeWindow_getWidth(window) > 0 && ANativeWindow_getHeight(window) > 0);
}
static void onWindowDestroyed(ANativeActivity *activity, ANativeWindow *window) {
    atomic_store(&windowReady, 0);
}
static void onDestroy(ANativeActivity *activity) {
    atomic_store(&destroyed, 1);
    atomic_store(&resumed, 0);
    atomic_store(&windowReady, 0);
}

JNIEXPORT void ANativeActivity_onCreate(ANativeActivity *activity, void *savedState, size_t savedStateSize) {
    atomic_store(&destroyed, 0);
    atomic_store(&resumed, 0);
    atomic_store(&windowReady, 0);
    activity->callbacks->onResume = onResume;
    activity->callbacks->onPause = onPause;
    activity->callbacks->onDestroy = onDestroy;
    activity->callbacks->onNativeWindowCreated = onWindow;
    activity->callbacks->onNativeWindowResized = onWindow;
    activity->callbacks->onNativeWindowDestroyed = onWindowDestroyed;
}

JNIEXPORT jboolean JNICALL Java_com_vrunity_vrapk_Xr_nativeWindowReady(JNIEnv *env, jobject thiz) {
    return atomic_load(&resumed) && atomic_load(&windowReady) && !atomic_load(&destroyed) ? JNI_TRUE : JNI_FALSE;
}
