package io.github.nathanrcast.timelapse.camera;

import com.sony.scalar.hardware.CameraEx;

// The holy-grail meter. ProgramLineRangeOverListener may be missing on old firmware, so it is implemented here, in a
// class only loaded after the probe found setProgramLineRangeOverListener.
final class ExposureBridge implements CameraEx.ProgramLineRangeOverListener {
    private final SafeCamera.ExposureCallback callback;

    ExposureBridge(SafeCamera.ExposureCallback callback) {
        this.callback = callback;
    }

    @Override
    public void onAERange(boolean ae, boolean iris, boolean shutter, CameraEx camera) {}

    @Override
    public void onEVRange(int ev, CameraEx camera) {
        callback.onEvRange(ev);
    }

    @Override
    public void onMeteringRange(boolean outOfRange, CameraEx camera) {}
}
