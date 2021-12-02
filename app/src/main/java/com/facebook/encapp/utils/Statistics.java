package com.facebook.encapp.utils;

import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;
import android.util.Size;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

public class Statistics {
    final static String TAG = "encapp";
    private final String mId;
    private final String mDesc;
    private String mEncodedfile = "";
    private String mCodec;
    private long mStartTime;
    private long mStopTime;
    private MediaFormat mEncoderMediaFormat;
    private MediaFormat mDecoderMediaFormat;

    private final HashMap<Long,FrameInfo> mEncodingFrames;
    private final HashMap<Long,FrameInfo> mDecodingFrames;
    TestParams mVc;
    Date mStartDate;

    public Statistics(String desc, TestParams vc) {
        mDesc = desc;
        mEncodingFrames = new HashMap<>();
        mDecodingFrames = new HashMap<>();
        mVc = vc;
        mStartDate = new Date();
        mId = "encapp_" + UUID.randomUUID().toString();
    }

    public String getId(){
        return mId;
    }

    public String toString() {
        ArrayList<FrameInfo> allEncodingFrames = new ArrayList<>(mEncodingFrames.values());
        Comparator<FrameInfo> compareByPts = (FrameInfo o1, FrameInfo o2) -> Long.valueOf(o1.getPts()).compareTo( Long.valueOf(o2.getPts() ));
        Collections.sort(allEncodingFrames, compareByPts);

        StringBuffer buffer = new StringBuffer();
        int counter = 0;
        for (FrameInfo info: allEncodingFrames) {
            buffer.append(mId + ", " +
                          counter + ", " +
                          info.isIFrame() + ", " +
                          info.getSize() + ", " +
                          info.getPts() + ", " +
                          info.getProcessingTime() + "\n");
            counter++;
        }

        return buffer.toString();
    }

    public void start(){
        mStartTime = System.nanoTime();
    }

    public void stop(){
        mStopTime = System.nanoTime();
    }

    public void startEncodingFrame(long pts) {
        FrameInfo frame = new FrameInfo(pts);
        frame.start();
        mEncodingFrames.put(Long.valueOf(pts), frame);
    }

    public void stopEncodingFrame(long pts, long size, boolean isIFrame) {
        FrameInfo frame = mEncodingFrames.get(Long.valueOf(pts));
        if (frame != null) {
            frame.stop();
            frame.setSize(size);
            frame.isIFrame(isIFrame);
        }
    }

    public void startDecodingFrame(long pts, long size, int flags) {
        FrameInfo frame = new FrameInfo(pts);
        frame.setSize(size);
        frame.setFlags(flags);
        frame.start();
        mDecodingFrames.put(Long.valueOf(pts), frame);
    }

    public void stopDecodingFrame(long pts) {
        FrameInfo frame = mDecodingFrames.get(Long.valueOf(pts));
        if (frame != null) {
            frame.stop();
        }
    }

    public long getProcessingTime() {
        return mStopTime - mStartTime;
    }

    public int getEncodedFrameCount() {
        return mEncodingFrames.size();
    }

    public void setCodec(String codec) {
        mCodec = codec;
    }

    public int getAverageBitrate() {
        ArrayList<FrameInfo> allFrames = new ArrayList<>(mEncodingFrames.values());
        Comparator<FrameInfo> compareByPts = (FrameInfo o1, FrameInfo o2) -> Long.valueOf(o1.getPts()).compareTo( Long.valueOf(o2.getPts() ));
        Collections.sort(allFrames, compareByPts);
        int framecount = allFrames.size();
        if (framecount > 0) {
            long startPts = allFrames.get(0).mPts;
            //We just ignore the last frame, for the average does not mean much.
            long lastTime = allFrames.get(allFrames.size() - 1).mPts;
            double totalTime =  ((double)(lastTime - startPts)) / 1000000.0;
            long totalSize = 0;
            for (FrameInfo info: allFrames) {
                totalSize += info.getSize();
            }
            totalSize -= allFrames.get(framecount - 1).mSize;
            return (int)(Math.round(8 * totalSize/(totalTime))); // bytes/Secs -> bit/sec
        } else {
            return 0;
        }
    }

    public void  setEncodedfile(String filename) {
        mEncodedfile = filename;
    }

    public void setEncoderMediaFormat(MediaFormat format) {
        mEncoderMediaFormat = format;
    }

    public void setDecoderMediaFormat(MediaFormat format) {
        mDecoderMediaFormat = format;
    }

    private JSONObject getSettingsFromMediaFormat(MediaFormat format) {
        JSONObject mediaformat = new JSONObject();
        if ( Build.VERSION.SDK_INT >= 29) {
            Set<String> features = format.getFeatures();
            for (String feature: features) {
                Log.d(TAG, "MediaFormat: " + feature);
            }

            Set<String> keys = format.getKeys();


            for (String key: keys) {
                int type = format.getValueTypeForKey(key);
                try {
                    switch (type) {
                        case MediaFormat.TYPE_BYTE_BUFFER:
                            mediaformat.put(key, "bytebuffer");
                            break;
                        case MediaFormat.TYPE_FLOAT:
                            mediaformat.put(key, format.getFloat(key));
                            break;
                        case MediaFormat.TYPE_INTEGER:
                            mediaformat.put(key, format.getInteger(key));
                            break;
                        case MediaFormat.TYPE_LONG:
                            mediaformat.put(key, format.getLong(key));
                            break;
                        case MediaFormat.TYPE_NULL:
                            mediaformat.put(key, "");
                            break;
                        case MediaFormat.TYPE_STRING:
                            mediaformat.put(key, format.getString(key));
                            break;
                    }
                } catch (JSONException jex){
                    Log.d(TAG, key + ", Failed to parse MediaFormat: " + jex.getMessage());
                }
            }
        } else {
            ArrayList<ConfigureParam> params = mVc.getExtraConfigure();
            for (ConfigureParam param : params) {
                try {
                    if (param.value instanceof Integer) {
                        mediaformat.put(param.name, format.getInteger(param.name));
                    } else if (param.value instanceof String) {
                        mediaformat.put(param.name, format.getString(param.name));
                    } else if (param.value instanceof Float) {
                        mediaformat.put(param.name, format.getFloat(param.name));
                    }
                } catch (Exception ex) {
                    Log.e(TAG, param.name + ", Bad behaving Mediaformat query: " + ex.getMessage());
                }
            }
        }
        return mediaformat;
    }
    public void writeJSON(Writer writer) throws IOException {
        try {
            JSONObject json = new JSONObject();

            json.put("id", mId);
            json.put("description", mDesc);
            json.put("test", mVc.getDescription());
            json.put("date", mStartDate.toString());
            json.put("proctime", getProcessingTime());
            json.put("framecount", getEncodedFrameCount());
            json.put("encodedfile", mEncodedfile);

            JSONObject settings = new JSONObject();
            settings.put("codec", mCodec);
            settings.put("gop", mVc.getKeyframeRate());
            settings.put("fps", mVc.getFPS());
            settings.put("bitrate", mVc.getBitRate());
            settings.put("meanbitrate", getAverageBitrate());
            Size s = mVc.getVideoSize();
            settings.put("width", s.getWidth());
            settings.put("height", s.getHeight());
            settings.put("encmode",mVc.bitrateModeName());
            settings.put("keyrate",mVc.getKeyframeRate());
            settings.put("iframepreset",mVc.getIframeSizePreset());

            ArrayList<ConfigureParam> configure = mVc.getExtraConfigure();
            for (ConfigureParam param: configure) {
                settings.put(param.name, param.value.toString());
            }
            json.put("settings", settings);
            json.put("encoder_media_format", getSettingsFromMediaFormat(mEncoderMediaFormat));
            if (mDecodingFrames.size() > 0) {
                json.put("decoder_media_format", getSettingsFromMediaFormat(mDecoderMediaFormat));
            }
            ArrayList<FrameInfo> allFrames = new ArrayList<>(mEncodingFrames.values());
            Comparator<FrameInfo> compareByPts = (FrameInfo o1, FrameInfo o2) -> Long.valueOf(o1.getPts()).compareTo( Long.valueOf(o2.getPts() ));
            Collections.sort(allFrames, compareByPts);
            int counter = 0;
            JSONArray jsonArray = new JSONArray();

            JSONObject obj = null;
            for (FrameInfo info: allFrames) {
                obj = new JSONObject();

                obj.put("frame", counter++);
                obj.put("iframe", (info.isIFrame())? 1: 0);
                obj.put("size", info.getSize());
                obj.put("pts", info.getPts());
                obj.put("proctime", (int)(info.getProcessingTime()));
                jsonArray.put(obj);
            }
            json.put("frames", jsonArray);

            if (mDecodingFrames.size() > 0) {
                allFrames = new ArrayList<>(mDecodingFrames.values());
                Collections.sort(allFrames, compareByPts);
                counter = 0;
                jsonArray = new JSONArray();

                obj = null;
                for (FrameInfo info : allFrames) {
                    long proc_time = info.getProcessingTime();
                    if (proc_time > 0) {
                        obj = new JSONObject();

                        obj.put("frame", counter++);
                        obj.put("flags", info.getFlags());
                        obj.put("size", info.getSize());
                        obj.put("pts", info.getPts());
                        obj.put("proctime", (int) (info.getProcessingTime()));
                        jsonArray.put(obj);
                    } else {
                        Log.d(TAG, "Decode time is negative. " + proc_time + ", start = " +
                                         info.mStartTime + ", stoptime = " +info.mStopTime);
                    }
                }
                json.put("decoded_frames", jsonArray);
            }
            writer.write(json.toString(2));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
