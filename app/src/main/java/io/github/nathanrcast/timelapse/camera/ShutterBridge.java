package io.github.nathanrcast.timelapse.camera;

import com.sony.scalar.hardware.CameraEx;

// Implements the Sony interface in its own class, so it is only loaded after the probe found setShutterListener.
final class ShutterBridge implements CameraEx.ShutterListener {
    private final SafeCamera.ShutterCallback callback;

    ShutterBridge(SafeCamera.ShutterCallback callback) {
        this.callback = callback;
    }

    @Override
    public void onShutter(int status, CameraEx camera) {
        callback.onShutter(status);
    }
}
