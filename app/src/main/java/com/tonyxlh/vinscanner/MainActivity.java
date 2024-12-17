package com.tonyxlh.vinscanner;

import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.dynamsoft.core.basic_structures.CapturedResultItem;
import com.dynamsoft.core.basic_structures.CompletionListener;
import com.dynamsoft.core.basic_structures.DSRect;
import com.dynamsoft.core.basic_structures.EnumCapturedResultItemType;
import com.dynamsoft.cvr.CaptureVisionRouter;
import com.dynamsoft.cvr.CaptureVisionRouterException;
import com.dynamsoft.cvr.CapturedResult;
import com.dynamsoft.cvr.CapturedResultReceiver;
import com.dynamsoft.dbr.BarcodeResultItem;
import com.dynamsoft.dbr.DecodedBarcodesResult;
import com.dynamsoft.dce.CameraEnhancer;
import com.dynamsoft.dce.CameraEnhancerException;
import com.dynamsoft.dce.CameraView;
import com.dynamsoft.dce.EnumCameraState;
import com.dynamsoft.dce.EnumEnhancerFeatures;
import com.dynamsoft.dce.utils.PermissionUtil;
import com.dynamsoft.dlr.TextLineResultItem;
import com.dynamsoft.license.LicenseManager;
import com.dynamsoft.utility.MultiFrameResultCrossFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private CameraEnhancer mCamera;
    private CaptureVisionRouter mRouter;
    private AlertDialog mAlertDialog;
    private TextView textView;
    private MultiFrameResultCrossFilter filter;
    private boolean isScanning = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        if (savedInstanceState == null) {
            LicenseManager.initLicense("DLS2eyJvcmdhbml6YXRpb25JRCI6IjIwMDAwMSJ9", this, (isSuccess, error) -> {
                error.printStackTrace();
            });
        }
        PermissionUtil.requestCameraPermission(this);
        CameraView cameraView = findViewById(R.id.camera_view);
        textView = findViewById(R.id.textView);
        mCamera = new CameraEnhancer(cameraView, this);
        DSRect region = new DSRect(0,0.4f,1,0.6f,true);
        mRouter = new CaptureVisionRouter(this);
        filter = new MultiFrameResultCrossFilter();
        try {
            mCamera.setScanRegion(region);
            mCamera.enableEnhancedFeatures(EnumEnhancerFeatures.EF_FRAME_FILTER);
            String template = readTemplate(R.raw.vin_template);
            mRouter.initSettings(template);
            mRouter.setInput(mCamera);
            filter.enableResultCrossVerification(EnumCapturedResultItemType.CRIT_TEXT_LINE, true);
            mRouter.addResultFilter(filter);
            mRouter.addResultReceiver(new CapturedResultReceiver() {
                @Override
                public void onCapturedResultReceived(@NonNull CapturedResult result) {
                    String barcode = "";
                    String textLine = "";
                    for (CapturedResultItem item:result.getItems()) {
                        if (item.getType() == EnumCapturedResultItemType.CRIT_BARCODE) {
                            barcode = ((BarcodeResultItem) item).getText();
                        }
                        if (item.getType() == EnumCapturedResultItemType.CRIT_TEXT_LINE) {
                            textLine = ((TextLineResultItem) item).getText();
                        }
                    }
                    displayResult(barcode,textLine);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        startScanning();
    }

    private void displayResult(String barcode,String textLine){
        runOnUiThread(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("Barcode Result: ");
            sb.append(barcode);
            sb.append("\n");
            sb.append("Text Result: ");
            sb.append(textLine);
            sb.append("\n");
            textView.setText(sb.toString());
        });
    }

    private String readTemplate(int ID) throws IOException {
        InputStream inp = this.getResources().openRawResource(ID);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inp));
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            out.append(line);
        }
        String content = out.toString();
        reader.close();
        return content;
    }

    private void startScanning(){
        if (isScanning) {
            return;
        }
        isScanning = true;
        try {
            mCamera.open();
        } catch (CameraEnhancerException e) {
            e.printStackTrace();
        }
        mRouter.startCapturing("ReadVINBarcodeAndText", new CompletionListener() {
            @Override
            public void onSuccess() {}
            @Override
            public void onFailure(int errorCode, String errorString) {
                runOnUiThread(() -> showDialog("Error", String.format(Locale.getDefault(), "ErrorCode: %d %nErrorMessage: %s", errorCode, errorString)));
            }
        });
    }

    private void stopScanning(){
        try {
            mCamera.close();
        } catch (CameraEnhancerException e) {
            e.printStackTrace();
        }
        mRouter.stopCapturing();
        isScanning = false;
    }

    @Override
    public void onResume() {
        super.onResume();
        startScanning();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopScanning();
    }

    private void showDialog(String title, String message) {
        if (mAlertDialog == null) {
            mAlertDialog = new AlertDialog.Builder(this)
                    .setCancelable(true)
                    .setPositiveButton("OK", null)
                    .create();
        }
        mAlertDialog.setTitle(title);
        mAlertDialog.setMessage(message);
        mAlertDialog.show();
    }
}