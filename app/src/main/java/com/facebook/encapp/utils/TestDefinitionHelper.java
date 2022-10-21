package com.facebook.encapp.utils;

import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;
import android.util.Size;

import com.facebook.encapp.proto.Configure;
import com.facebook.encapp.proto.DataValueType;
import com.facebook.encapp.proto.Input;
import com.facebook.encapp.proto.Test;


public class TestDefinitionHelper {
    private static final String TAG = "encapp";
    public static MediaFormat buildMediaFormat(Test test) {
        Configure config = test.getConfigure();
        Input input = test.getInput();
        Size targetResolution = SizeUtils.parseXString(config.getResolution());
        // start with the default MediaFormat
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(
                config.getMime(), targetResolution.getWidth(), targetResolution.getHeight());

        // optional config parameters
        if (config.hasBitrate()) {
            String bitrate  = config.getBitrate();
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, magnitudeToInt(bitrate));
        }
        if (input.hasFramerate()) {
            float framerate  = input.getFramerate();
            mediaFormat.setFloat(MediaFormat.KEY_FRAME_RATE, framerate);
        }
        // good default: MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
        if (config.hasBitrateMode()) {
            int bitrateMode = config.getBitrateMode().getNumber();
            mediaFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, bitrateMode);
        }
        // check if there is a quality value
        if (config.hasQuality()) {
            int quality = config.getQuality();
            mediaFormat.setInteger(MediaFormat.KEY_QUALITY, quality);
        }
        if (config.hasIFrameInterval()) {
            int iFrameInterval  = config.getIFrameInterval();
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval);
        }
        // color parameters
        // good default: MediaFormat.COLOR_RANGE_LIMITED
        if (config.hasColorRange()) {
            int colorRange  = config.getColorRange().getNumber();
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_RANGE, colorRange);
        }
        // good default: MediaFormat.COLOR_TRANSFER_SDR_VIDEO
        if (config.hasColorTransfer()) {
            int colorTransfer  = config.getColorTransfer().getNumber();
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_TRANSFER, colorTransfer);
        }
        // good default: MediaFormat.COLOR_STANDARD_BT709
        if (config.hasColorStandard()) {
            int colorStandard  = config.getColorStandard().getNumber();
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_STANDARD, colorStandard);
        }

        // set all the available values
        for (Configure.Parameter param : config.getParameterList()) {
            switch (param.getType().getNumber()) {
                case DataValueType.floatType_VALUE:
                    float fval = Float.parseFloat(param.getValue());
                    mediaFormat.setFloat(param.getKey(), fval);
                    break;
                case DataValueType.intType_VALUE:
                    int ival = TestDefinitionHelper.magnitudeToInt(param.getValue());
                    mediaFormat.setInteger(param.getKey(), ival);
                    break;
                case DataValueType.longType_VALUE:
                    long lval = Long.parseLong(param.getValue());
                    mediaFormat.setLong(param.getKey(), lval);
                    break;
                case DataValueType.stringType_VALUE:
                    mediaFormat.setString(param.getKey(), param.getValue());
                    break;
                default:
                    ///Should not be here
            }
        }
        return mediaFormat;
    }


    public static int magnitudeToInt(String text) {
        int index = text.indexOf("bps");

        if (index > 0) {
            text = text.substring(0, index).trim();
        } else {
            text = text.trim();
        }

        int val = 0;
        if (text == null) {
            return 0;
        } else if (text.endsWith("k")) {
                val = Integer.parseInt(text.substring(0, text.lastIndexOf('k')).trim()) * 1000;
        } else if (text.endsWith("M")) {
            val = Integer.parseInt(text.substring(0, text.lastIndexOf('M')).trim()) * 1000000;
        } else if (text.length() > 0){
            val = Integer.parseInt(text);
        }

        return val;
    }

    public static Test updateEncoderResolution(Test test, int width, int height) {
        Test.Builder builder = Test.newBuilder(test);
        builder.setConfigure(Configure.newBuilder(test.getConfigure()).setResolution(width + "x" + height));
        return builder.build();
    }

    public static Test updateInputSettings(Test test, MediaFormat mediaFormat) {
        Test.Builder builder = test.toBuilder();
        Input.Builder input = builder.getInput().toBuilder();
        input.setResolution(mediaFormat.getInteger(MediaFormat.KEY_WIDTH) + "x"  + mediaFormat.getInteger(MediaFormat.KEY_HEIGHT));
        float framerate = 30.0f;
        try {
            framerate = mediaFormat.getFloat(MediaFormat.KEY_FRAME_RATE);
        } catch (Exception ex) {
            try {
                Log.e(TAG, "Failed to grab framerate as float.");
                framerate = (float) mediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE);
                Log.e(TAG, "framerate as int: " + framerate);
            } catch (Exception ex2) {
                Log.e(TAG, "Failed to grab framerate as int - just set 30 fps");
            }
        }
        input.setFramerate(framerate);
        builder.setInput(input);
        return builder.build();
    }

    public static Test updatePlayoutFrames(Test test, int frames) {
        Test.Builder builder = Test.newBuilder(test);
        builder.setInput(Input.newBuilder(test.getInput()).setPlayoutFrames(frames));
        return builder.build();
    }



    public static Test checkAnUpdateBasicSettings(Test test) {
        // Make sure we have the most basic settings well defined
        Size res;
        Input.Builder input = test.getInput().toBuilder();
        if (!input.hasResolution()) {
            input.setResolution("1280x720");
        }

        if (!input.hasFramerate()) {
            input.setFramerate(30.0f);
        }

        Configure.Builder config = test.getConfigure().toBuilder();
        if (!config.hasBitrate()) {
            config.setBitrate("1 Mbps");
        }

        if (!config.hasFramerate()) {
            config.setFramerate(input.getFramerate());
        }

        if (!config.hasIFrameInterval()) {
            config.setIFrameInterval(10);
        }
        if (!config.hasResolution()) {
            config.setResolution(input.getResolution());
        }

        Test.Builder builder = test.toBuilder();
        builder.setInput(input);
        builder.setConfigure(config);

        return builder.build();
    }
}
