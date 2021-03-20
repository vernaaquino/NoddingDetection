package com.example.noddingdetection2;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.common.FirebaseVisionPoint;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceLandmark;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.controls.Facing;
import com.otaliastudios.cameraview.frame.Frame;
import com.otaliastudios.cameraview.frame.FrameProcessor;
import com.otaliastudios.cameraview.size.Size;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int MY_PERMISSIONS_REQUEST_CAMERA= 1;
    boolean firstFrame = true;
    //midpoint y history
    double prevY = 0;
    double currY = 0;

    //euler y history
    boolean faceLeft = false;
    boolean faceRight = false;

    // euler y and midpoint y values
    float rotY;
    double midPointY;

    long start = 0;
    long end = 0;
    float duration; //in seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkCameraPermission();

        CameraView cameraview = (CameraView) findViewById(R.id.cameraView);
        cameraview.setFacing(Facing.FRONT);
        cameraview.setLifecycleOwner(this); //Automatically handles the camera lifecycle

        cameraview.addFrameProcessor(new FrameProcessor() {
                                         @Override
                                         @WorkerThread
                                         public void process(@NonNull Frame frame) {
                                             byte[] data = frame.getData();
                                             int rotation = frame.getRotation();
                                             long time = frame.getTime();
                                             Size size = frame.getSize();
                                             int format = frame.getFormat();

                                             //set prev and current midpoint Y
                                             if(firstFrame){
                                                 // Process...
                                                 FirebaseVisionImage image = FirebaseVisionImage.fromByteArray(data, extractFrameMetadata(frame));
                                                 prevY = detectFaces(image);
                                                 currY = prevY;
                                                 firstFrame = false;
                                             }
                                             else{
                                                 // Process...
                                                 FirebaseVisionImage image = FirebaseVisionImage.fromByteArray(data, extractFrameMetadata(frame));
                                                 currY = detectFaces(image);
                                             }

                                             //check how much current midpoint y has changed from prev
                                             if(((currY - prevY) >= 20 )&& (prevY != 0)){
                                                 Log.d("nodding", " You are saying yes");
                                                 Toast.makeText(getApplicationContext(),"You are saying yes", Toast.LENGTH_SHORT).show();
                                             }

                                             //check if a left-right turn was made within 2 seconds
                                             if(faceLeft){
                                                //start timer
                                                 start = System.currentTimeMillis();

                                             }
                                             else if(faceRight){
                                                //stop timer and record duration
                                                 if(start != 0) {
                                                     end = System.currentTimeMillis();
                                                     duration = (end - start) / 1000F;
                                                     if(duration <= 2){
                                                         Log.d("shaking", " You are saying no");
                                                         Toast.makeText(getApplicationContext(),"You are saying no", Toast.LENGTH_SHORT).show();
                                                     }
                                                     //reset start
                                                     start = 0;
                                                 }
                                             }

                                             //set previous to current
                                             prevY = currY;

                                         }
                                     });
    }

    private FirebaseVisionImageMetadata extractFrameMetadata (Frame frame){

        return new FirebaseVisionImageMetadata.Builder()
                .setWidth(frame.getSize().getWidth())
                .setHeight(frame.getSize().getHeight())
                .setFormat(frame.getFormat())
                .setRotation(frame.getRotation() / 90)
                .build();
    }

    private double detectFaces(FirebaseVisionImage image) {
        // [START set_detector_options]
        FirebaseVisionFaceDetectorOptions options =
                new FirebaseVisionFaceDetectorOptions.Builder()
                        .setClassificationMode(FirebaseVisionFaceDetectorOptions.ACCURATE)
                        .setLandmarkMode(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
                        .setClassificationMode(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
                        .setMinFaceSize(0.15f)
                        .enableTracking()
                        .build();
        // [END set_detector_options]

        // [START get_detector]
        FirebaseVisionFaceDetector detector = FirebaseVision.getInstance()
                .getVisionFaceDetector(options);
        // [END get_detector]

        // [START run_detector]
        Task<List<FirebaseVisionFace>> result =
                detector.detectInImage(image)
                        .addOnSuccessListener(
                                new OnSuccessListener<List<FirebaseVisionFace>>() {
                                    @Override
                                    public void onSuccess(List<FirebaseVisionFace> faces) {
                                        // Task completed successfully
                                        // [START_EXCLUDE]
                                        // [START get_face_info]
                                        for (FirebaseVisionFace face : faces) {
                                            Rect bounds = face.getBoundingBox();
                                            rotY = face.getHeadEulerAngleY();  // Head is rotated to the right rotY degrees
                                            Log.d("roty", rotY + "");

                                            //calculate midpoint
                                            FirebaseVisionFaceLandmark rightEye = face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_EYE);
                                            FirebaseVisionFaceLandmark leftEye = face.getLandmark(FirebaseVisionFaceLandmark.LEFT_EYE);
                                            FirebaseVisionPoint rightPosition = rightEye.getPosition();
                                            FirebaseVisionPoint leftPosition = leftEye.getPosition();
                                            midPointY = (rightPosition.getY() + leftPosition.getY()) / 2.0;

                                            //set face orientation
                                            if(rotY >= 10){
                                                faceLeft = true;
                                                faceRight = false;
                                            }
                                            else if (rotY <= -10){
                                                faceRight = true;
                                                faceLeft = false;
                                            }
                                            else{
                                                faceRight = false;
                                                faceLeft = false;
                                            }

                                               /*** Draw Bounding Box ***/
//
//                                            ImageView overlay = findViewById(R.id.boundBox);
//                                            ViewGroup.LayoutParams params = (ViewGroup.LayoutParams) overlay.getLayoutParams();
//                                            params.width = bounds.width();
//                                            params.height = bounds.height();
//
//                                            if(bounds.left <= bounds.right){
//                                                overlay.setTop(bounds.top);
//                                                overlay.setLeft(bounds.left);
//                                                overlay.setBottom(bounds.top + bounds.width());
//                                                overlay.setRight(bounds.left + bounds.height());
//                                            }

                                        }
                                        // [END get_face_info]
                                        // [END_EXCLUDE]
                                    }
                                })
                        .addOnFailureListener(
                                new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        // Task failed with an exception
                                        // ...
                                    }
                                });


        // [END run_detector]

        return midPointY;
    }

    public boolean checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                new AlertDialog.Builder(this)
                        .setTitle(R.string.title_camera_permission)
                        .setMessage(R.string.text_camera_permission)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                //Prompt the user once explanation has been shown
                                ActivityCompat.requestPermissions(MainActivity.this,
                                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                                        MY_PERMISSIONS_REQUEST_CAMERA);
                            }
                        })
                        .create()
                        .show();


            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_CAMERA);
            }
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_CAMERA: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.e("value", "Permission Granted, Now you can read local drive .");
                } else {
                    Log.e("value", "Permission Denied, You cannot read local drive .");
                }
                break;
            }

        }
    }
}
