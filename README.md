# NoddingDetection

## Objective
To detect whether a person is nodding up-done (i.e. saying yes) or shaking head left-right (i.e. saying no).

## Approach
Using the CameraView library, a CameraView object was created in order to extract each frame from the camera video stream using the method addFrameProcessor(). Each frame is then converted into a FirebaseVisionImage object which is passed on to the detectFaces() method. The detectFaces() method creates a FirebaseVisionFaceDetector which handles the detection of faces in the previously created FirebaseVisionImage object. Each face detected is stored in a List of FirebaseVisionFace objects.
### Head Shaking Algorithm (Saying No)
In order to track head shaking (i.e. saying no) , the getHeadEulerAngleY() method of the FirebaseVisionFace object is called.  A positive euler y is when the face turns toward the right side of the image that is being processed while a negative one is for turning towards the left. A threshold of euler y >= 10 for turning head left and euler y <= -10 for turning head right was set after trial and error. If a left turn was detected and a right turn is subsequently detected within the next 2 seconds, a head shaking (saying no) is detected.
### Head Nodding Algorithm (Saying Yes)
In order to track head nodding (i.e. saying no) the midpoint y is calculated by getting the average of the right eye and left eye y positions of the FirebaseVisionFace object. The greater the midpoint y, the lower the midpoint is on the screen. A threshold of midpoint y >= 20 for nodding was determined via trial and error. If a midpoint y>= 20 was detected, a head nodding (saying yes) is detected.

## Citations
* ML Kit for Firebase
* CameraView by natario1  (https://github.com/natario1/CameraView)


