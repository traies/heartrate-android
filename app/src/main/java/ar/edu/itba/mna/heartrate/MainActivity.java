package ar.edu.itba.mna.heartrate;

import android.graphics.Color;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.TextView;
//import android.util.Log;

import com.getkeepsafe.relinker.ReLinker;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import org.apache.commons.math3.complex.Complex;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_core.Scalar;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.Rect;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameConverter;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import uk.me.berndporr.iirj.Butterworth;

import static org.bytedeco.javacpp.opencv_core.mean;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private Camera mCamera;
    private MediaRecorder mMediaRecorder;
    private boolean isRecording = false;
    private CameraPreview mCameraPreview;
    private TextView mFrequencyCounterRed;
    private TextView mFrequencyCounterGreen;
    private TextView mFrequencyCounterBlue;
    private GraphView graph;
    private boolean flash = false;
    private Chronometer chronometer;
    static {

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ReLinker.Logger logger = new ReLinker.Logger() {
            @Override
            public void log(String message) {
                Log.v("HODOR", "(hold the door) " + message);
            }
        };
        ReLinker.log(logger).recursively().loadLibrary(getApplicationContext(), "opencv_core");

        setContentView(R.layout.activity_main);
        mCamera = Camera.open();
        mCamera.setDisplayOrientation(90);
        mCameraPreview = new CameraPreview(this, mCamera);

        mFrequencyCounterRed = (TextView) findViewById(R.id.max_frequency_red);
        mFrequencyCounterGreen = (TextView) findViewById(R.id.max_frequency_green);
        mFrequencyCounterBlue = (TextView) findViewById(R.id.max_frequency_blue);

        FrameLayout frameLayout = (FrameLayout) findViewById(R.id.camera_preview);
        frameLayout.addView(mCameraPreview);
        graph = (GraphView) findViewById(R.id.graph);
        graph.getGridLabelRenderer().setVerticalLabelsVisible(false);
        chronometer = (Chronometer) findViewById(R.id.capture_timer);
        Spinner spinner = (Spinner) findViewById(R.id.times_spinner);
        spinner.getSelectedItem();

        Button button = (Button) findViewById(R.id.button_capture);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AsyncTask.execute(() -> {
                    synchronized (this) {
                        if (!isRecording) {
                            Camera.Parameters p = mCamera.getParameters();
                            p.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                            mCamera.setParameters(p);
                            // initialize video camera
                            if (prepareVideoRecorder()) {
                                // Camera is available and unlocked, MediaRecorder is prepared,
                                // now you can start recording
                                mMediaRecorder.start();

                                // inform the user that recording has started
                                isRecording = true;
                                runOnUiThread(() -> {
                                    chronometer.setBase(SystemClock.elapsedRealtime());
                                    chronometer.start();
                                }  );
                                final Handler handler = new Handler(Looper.getMainLooper());

                                String delay = (String) spinner.getSelectedItem().toString();
                                long delaymilis = 0;
                                switch (delay) {
                                    case "20 segundos (n = 512)":
                                        delaymilis = 20000L;
                                        break;
                                    case "40 segundos (n = 1024)":
                                        delaymilis = 40000L;
                                        break;
                                    case "80 segundos (n = 2048)":
                                        delaymilis = 80000L;
                                        break;
                                }
                                handler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        chronometer.stop();
                                        Camera.Parameters p = mCamera.getParameters();
                                        p.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                                        mCamera.setParameters(p);
                                        // stop recording and release camera
                                        mMediaRecorder.stop();  // stop the recording
                                        releaseMediaRecorder(); // release the MediaRecorder object
                                        mCamera.lock();         // take camera access back from MediaRecorder

                                        try {
                                            updateFFT();
                                        } catch (FrameGrabber.Exception e) {
                                            e.printStackTrace();
                                        }
                                        isRecording = false;
                                    }
                                }, delaymilis);
                            } else {
                                // prepare didn't work, release the camera
                                releaseMediaRecorder();
                                // inform user
                            }
                        }
                    }
                });
            }
        });
    }

    private boolean prepareVideoRecorder(){
        mMediaRecorder = new MediaRecorder();
        // Step 1: Unlock and set camera to MediaRecorder

        mCamera.unlock();
        mMediaRecorder.setCamera(mCamera);

        // Step 2: Set sources
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        // Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
        mMediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_480P));

        // Step 4: Set output file
        mMediaRecorder.setOutputFile(getOutputMediaFile().toString());
        mMediaRecorder.setPreviewDisplay(mCameraPreview.getHolder().getSurface());

        // Step 6: Prepare configured MediaRecorder
        try {
            mMediaRecorder.prepare();
        } catch (IllegalStateException e) {
            Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        } catch (IOException e) {
            Log.d(TAG, "IOException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        }
        return true;
    }

    private void releaseMediaRecorder(){
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();   // clear recorder configuration
            mMediaRecorder.release(); // release the recorder object
            mMediaRecorder = null;
        }
    }

    /** Create a file Uri for saving an image or video */
    private static Uri getOutputMediaFileUri(int type){
        return Uri.fromFile(getOutputMediaFile());
    }

    /** Create a File for saving an image or video */
    private static File getOutputMediaFile(){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "MyCameraApp");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d("MyCameraApp", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        File mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                "VID_FFT.mp4");
        return mediaFile;
    }


    public void drawGraph(double[] r, double[] g, double[] b,  double[] frequencies) {

        DataPoint[] red = new DataPoint[r.length / 2];
        for (int i = r.length / 2; i < r.length; i++) {
            red[i - r.length / 2] = new DataPoint( frequencies[i] * 60 , r[i]);
        }
        LineGraphSeries<DataPoint> series = new LineGraphSeries<>(red);
        series.setColor(Color.RED);

        DataPoint[] green = new DataPoint[g.length / 2];
        for (int i = g.length / 2; i < g.length; i++) {
            green[i - g.length / 2] = new DataPoint( frequencies[i] * 60 , g[i]);
        }
        LineGraphSeries<DataPoint> series2 = new LineGraphSeries<>(green);
        series2.setColor(Color.GREEN);

        DataPoint[] blue = new DataPoint[b.length / 2];
        for (int i = b.length / 2; i < b.length; i++) {
            blue[i - b.length / 2] = new DataPoint( frequencies[i] * 60 , b[i]);
        }
        LineGraphSeries<DataPoint> series3 = new LineGraphSeries<>(blue);
        series3.setColor(Color.BLUE);
        graph.removeAllSeries();
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(150);
        graph.addSeries(series);
        graph.addSeries(series2);
        graph.addSeries(series3);
    }


    private void updateFFT() throws FrameGrabber.Exception {

        FFmpegFrameGrabber frameGrabber = new FFmpegFrameGrabber(getOutputMediaFile().toString());
        try {
            frameGrabber.start();
        } catch (FrameGrabber.Exception e) {
            e.printStackTrace();
        }
        int length = (int) frameGrabber.getLengthInFrames();
        int width = (int) frameGrabber.getImageWidth();
        int height = (int) frameGrabber.getImageHeight();
        int fps = (int) frameGrabber.getFrameRate();

        Log.d(TAG, "length: "+length+", width: "+width+", height: "+height+", fps: "+fps);

        double[] r = new double[length];
        double[] g = new double[length];
        double[] b = new double[length];

        for (int i = 0; i < length; i++) {
            OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat();
            Frame frame = frameGrabber.grabImage();
            Mat mat = converter.convertToMat(frame);
            Scalar means = opencv_core.mean(mat);

            r[i] = means.red();
            g[i] = means.green();
            b[i] = means.blue();
        }

        int n = (int)Math.pow(2, Math.floor(log(length, 2)));
        r = Arrays.copyOfRange(r, 0, n);
        g = Arrays.copyOfRange(g, 0, n);
        b = Arrays.copyOfRange(b, 0, n);
        double rmean = mean(r);
        double gmean = mean(g);
        double bmean = mean(b);

        Butterworth butterworthRed = new Butterworth();
        Butterworth butterworthGreen = new Butterworth();
        Butterworth butterworthBlue = new Butterworth();
        butterworthRed.bandPass(2, fps, 90 / 60.0, 80/60.0);
        butterworthGreen.bandPass(2, fps, 90 / 60.0, 80/60.0);
        butterworthBlue.bandPass(2, fps, 90 / 60.0, 80/60.0);

        for(int i = 0; i < n; i++) {
            r[i] -= rmean;
            r[i] = butterworthRed.filter(r[i]);
            g[i] -= gmean;
            g[i] = butterworthGreen.filter(g[i]);
            b[i] -= bmean;
            b[i] = butterworthBlue.filter(b[i]);
        }

        long start = System.currentTimeMillis();
        Complex[] R = FFT.fft(r);
        FFT.fftshift(R);
        double[] Rsq = FFT.complexSquare(R);
        Complex[] G = FFT.fft(g);
        FFT.fftshift(G);
        double[] Gsq = FFT.complexSquare(G);
        Complex[] B = FFT.fft(b);
        FFT.fftshift(B);
        double[] Bsq = FFT.complexSquare(B);
        long end = System.currentTimeMillis();

        double[] f = linspace( -n / 2.0, n / 2.0 - 1, n);
        for (int i = 0; i < n; i++) {
            f[i] *= fps / (double) n;
        }
        int argRed = maxArg(Rsq);
        int argGreen = maxArg(Gsq);
        int argBlue = maxArg(Bsq);

        double fRed = f[argRed > n / 2 ? argRed: n  - argRed] * 60;
        double fGreen = f[argGreen > n / 2 ? argGreen: n  - argGreen] * 60;
        double fBlue = f[argBlue > n / 2 ? argBlue: n  - argBlue] * 60;

        runOnUiThread(() -> {
            mFrequencyCounterRed.setText(String.format("%.1f", fRed));
            mFrequencyCounterGreen.setText(String.format("%.1f", fGreen));
            mFrequencyCounterBlue.setText(String.format("%.1f", fBlue));
            drawGraph(Rsq, Gsq, Bsq, f);
        });

    }

    static int log(int x, int base)
    {
        return (int) (Math.log(x) / Math.log(base));
    }

    static double mean(double[] arr) {
        double sum = 0;
        for (double d: arr) {
            sum += d;
        }
        return sum / arr.length;
    }

    static double[] linspace(double init, double end, int steps) {
        double[] ret = new double[steps];
        double step = (end - init) / steps;
        ret[0] = init;
        for (int i= 1; i < steps; i++) {
            ret[i] = ret[i - 1] + step;
        }
        return ret;
    }

    static int maxArg(double[] arr) {
        double max = 0.0;
        int maxIndex = 0;
        for (int i = 0; i < arr.length; i++) {
            if (max <= arr[i]) {
                max = arr[i];
                maxIndex = i;
            }
        }
        return maxIndex;
    }
}
