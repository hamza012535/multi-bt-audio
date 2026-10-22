package com.hamza.multibtaudio;
interface IMultiSinkControl {
    String applyPreferredDevices(in String[] addresses);
    void clearPreferredDevices();
}
