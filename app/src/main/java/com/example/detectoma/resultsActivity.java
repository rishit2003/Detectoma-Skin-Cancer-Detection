package com.example.detectoma;

import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.FirebaseApp;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import com.google.firebase.ml.modeldownloader.CustomModelDownloadConditions;
import com.google.firebase.ml.modeldownloader.FirebaseModelDownloader;
import com.google.firebase.ml.modeldownloader.DownloadType;
import com.google.firebase.ml.modeldownloader.CustomModel;
import com.google.firebase.ml.modeldownloader.FirebaseMlException;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class resultsActivity extends AppCompatActivity {

    private static final String TAG = "ScreeningResultsActivity";

    // Views for AI Prediction
    private TextView predictionTextView;
    private ImageView imageView;

    // Views for Questionnaire Results
    private TextView resultsTextView;
    private TextView recommendationTextView;
//2024-11-21 12:30.jpg
    // Firebase Storage reference
    private FirebaseStorage storage = FirebaseStorage.getInstance();
    //private StorageReference storageRef = storage.getReference().child("Test/test1.jpg"); // Update the path accordingly
    private StorageReference storageRef = storage.getReference("/Patients/4t34RojIIuNPeJ79j1OKWZJ75EJ2/2024-11-21 12:30.jpg");
    private Interpreter tflite;
    private final long ONE_MEGABYTE = 5 * 1024 * 1024; // 5 MB

    private float[] classWeights; // To store the classification layer weights

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_results); // Ensure this layout includes all necessary views

        // Handle window insets for edge-to-edge display
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize Firebase
        FirebaseApp.initializeApp(this);

        // Initialize AI Prediction Views
        predictionTextView = findViewById(R.id.predictionTextView);
        imageView = findViewById(R.id.imageView_image);

        // Initialize Questionnaire Results Views
        resultsTextView = findViewById(R.id.resultsTextView);
        recommendationTextView = findViewById(R.id.recommendationTextView);

        // Load the classification layer weights
        loadClassWeights();

        // Download and load the model
        // If you prefer to load the model from assets, comment the following line and uncomment loadModelFromAssets()
        downloadAndLoadModel();

        // For loading model from assets, use:
        // loadModelFromAssets();

        // Retrieve questionnaire results from Intent
        Intent intent = getIntent();
        boolean asymmetry = intent.getBooleanExtra("asymmetry", false);
        boolean border = intent.getBooleanExtra("border", false);
        boolean color = intent.getBooleanExtra("color", false);
        boolean diameter = intent.getBooleanExtra("diameter", false);
        boolean evolving = intent.getBooleanExtra("evolving", false);

        // Analyze and display questionnaire results
        analyzeResults(asymmetry, border, color, diameter, evolving);
    }

    /**
     * Downloads the custom TensorFlow Lite model from Firebase and initializes the interpreter.
     */
    private void downloadAndLoadModel() {
        Log.d(TAG, "Starting model download...");
        CustomModelDownloadConditions conditions = new CustomModelDownloadConditions.Builder()
                .requireWifi()
                .build();

        FirebaseModelDownloader.getInstance()
                .getModel("MelanomaCamMap", DownloadType.LOCAL_MODEL, conditions)
                .addOnSuccessListener(model -> {
                    Log.d(TAG, "Model download successful.");
                    // Initialize the interpreter with the downloaded model
                    processModel(model);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to download model", e);
                    if (e instanceof FirebaseMlException) {
                        if (e.getMessage().contains("Model download in bad state")) {
                            Log.d(TAG, "Deleting local model and retrying download...");
                            deleteLocalModel("MelanomaDetectorWithCAM");
                            // Retry downloading the model
                            downloadAndLoadModel();
                        } else {
                            showErrorToUser("Failed to download model. Please try again later.");
                        }
                    } else {
                        showErrorToUser("Failed to download model. Please try again later.");
                    }
                });
    }

    /**
     * Processes the downloaded model by initializing the TensorFlow Lite interpreter.
     *
     * @param model The downloaded CustomModel from Firebase.
     */
    private void processModel(CustomModel model) {
        File modelFile = model.getFile();
        if (modelFile != null) {
            try {
                // Initialize the interpreter with the downloaded model
                tflite = new Interpreter(modelFile);
                Log.d(TAG, "Interpreter initialized.");

                // Proceed to process the image
                processImageFromFirebase();
                // Or use a local image for testing
                // processImageFromAssets();
            } catch (Exception e) {
                Log.e(TAG, "Error initializing interpreter", e);
                showErrorToUser("Failed to initialize the model.");
            }
        } else {
            Log.e(TAG, "Model file is null");
            showErrorToUser("Model file is unavailable.");
        }
    }

    /**
     * Deletes the locally downloaded model from Firebase.
     *
     * @param modelName The name of the model to delete.
     */
    private void deleteLocalModel(String modelName) {
        FirebaseModelDownloader.getInstance().deleteDownloadedModel(modelName)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Local model deleted successfully.");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to delete local model", e);
                });
    }

    /**
     * Loads the TensorFlow Lite model from the app's assets directory.
     */
    private void loadModelFromAssets() {
        try {
            AssetFileDescriptor fileDescriptor = getAssets().openFd("model_with_cam.tflite");
            FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            MappedByteBuffer modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);

            // Initialize the interpreter
            tflite = new Interpreter(modelBuffer);
            Log.d(TAG, "Interpreter initialized from assets.");

            // Proceed to process the image
            processImageFromAssets();
        } catch (IOException e) {
            Log.e(TAG, "Error loading model from assets", e);
            showErrorToUser("Failed to load model from assets.");
        }
    }

    /**
     * Downloads the image from Firebase Storage and initiates processing.
     */
    private void processImageFromFirebase() {
        Log.d(TAG, "Starting image download...");
        storageRef.getBytes(ONE_MEGABYTE)
                .addOnSuccessListener(bytes -> {
                    Log.d(TAG, "Image download successful.");
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

                    // Display the original image
                    imageView.setImageBitmap(bitmap);

                    // Run the model on the image
                    runModel(bitmap);
                })
                .addOnFailureListener(exception -> {
                    Log.e(TAG, "Error downloading image", exception);
                    if (exception instanceof com.google.firebase.storage.StorageException) {
                        com.google.firebase.storage.StorageException se = (com.google.firebase.storage.StorageException) exception;
                        if (se.getErrorCode() == com.google.firebase.storage.StorageException.ERROR_OBJECT_NOT_FOUND) {
                            showErrorToUser("Image not found at the specified location.");
                        } else {
                            showErrorToUser("Failed to download image. Please try again later.");
                        }
                    } else {
                        showErrorToUser("Failed to download image. Please try again later.");
                    }
                });
    }

    /**
     * Loads a local image from assets for testing purposes.
     */
    private void processImageFromAssets() {
        try {
            InputStream is = getAssets().open("2024-11-21 12:30.jpg"); // Ensure test1.jpg is in assets
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            is.close();

            // Display the original image
            imageView.setImageBitmap(bitmap);

            // Run the model on the image
            runModel(bitmap);
        } catch (IOException e) {
            Log.e(TAG, "Error loading image from assets", e);
            showErrorToUser("Failed to load image from assets.");
        }
    }

    /**
     * Runs the TensorFlow Lite model on the provided bitmap.
     *
     * @param bitmap The image to process.
     */
    private void runModel(Bitmap bitmap) {
        Log.d(TAG, "Running model...");
        if (tflite == null) {
            Log.e(TAG, "Interpreter is not initialized");
            showErrorToUser("Model is not loaded.");
            return;
        }

        // Preprocess the image
        TensorImage tensorImage = preprocessImage(bitmap);

        // Prepare input and output buffers
        Map<Integer, Object> outputs = new HashMap<>();

        // Get output shapes
        int[] featureMapShape = tflite.getOutputTensor(0).shape(); // [1,8,8,1280]
        int[] predictionShape = tflite.getOutputTensor(1).shape();  // [1,1]

        Log.d(TAG, "featureMapShape: " + Arrays.toString(featureMapShape));
        Log.d(TAG, "predictionShape: " + Arrays.toString(predictionShape));

        // Validate output shapes
        if (featureMapShape.length != 4 || featureMapShape[0] != 1 ||
                predictionShape.length != 2 || predictionShape[0] != 1) {
            Log.e(TAG, "Unexpected output tensor shapes.");
            showErrorToUser("Unexpected output tensor shapes.");
            return;
        }

        // Create output buffers
        TensorBuffer featureMapBuffer = TensorBuffer.createFixedSize(featureMapShape, DataType.FLOAT32);
        TensorBuffer predictionBuffer = TensorBuffer.createFixedSize(predictionShape, DataType.FLOAT32);

        outputs.put(0, featureMapBuffer.getBuffer());
        outputs.put(1, predictionBuffer.getBuffer());

        try {
            // Run inference
            Object[] inputArray = {tensorImage.getBuffer()};
            tflite.runForMultipleInputsOutputs(inputArray, outputs);

            // Get the prediction from output[1]
            float prediction = predictionBuffer.getFloatArray()[0];
            String result = prediction > 0.5 ? "Positive" : "Negative";

            Log.d(TAG, "Prediction: " + prediction + ", Result: " + result);

            // Get the feature maps from output[0]
            float[] featureMaps = featureMapBuffer.getFloatArray();

            // Compute the CAM
            Bitmap camBitmap = computeCAM(featureMaps, classWeights, featureMapShape, bitmap.getWidth(), bitmap.getHeight());

            if (camBitmap != null) {
                // Overlay the CAM on the original image
                Bitmap overlayedImage = overlayHeatmapOnImage(camBitmap, bitmap);

                // Display the result and the image with heatmap
                runOnUiThread(() -> {
                    predictionTextView.setText("Prediction: " + result + " (" + String.format("%.4f", prediction) + ")");
                    imageView.setImageBitmap(overlayedImage);
                });
            } else {
                showErrorToUser("Failed to compute CAM.");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error during model inference", e);
            showErrorToUser("Error during model inference.");
        }
    }

    /**
     * Preprocesses the input bitmap to match the model's expected input format.
     *
     * @param bitmap The image to preprocess.
     * @return The preprocessed TensorImage.
     */
    private TensorImage preprocessImage(Bitmap bitmap) {
        // Create a TensorImage object
        TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
        tensorImage.load(bitmap);

        // Define image preprocessing steps
        ImageProcessor imageProcessor = new ImageProcessor.Builder()
                .add(new ResizeOp(256, 256, ResizeOp.ResizeMethod.BILINEAR))
                .add(new NormalizeOp(127.5f, 127.5f)) // Normalize to [-1, 1]
                .build();

        // Preprocess the image
        tensorImage = imageProcessor.process(tensorImage);

        return tensorImage;
    }

    /**
     * Loads the classification layer weights from a text file in assets.
     */
    private void loadClassWeights() {
        try {
            InputStream is = getAssets().open("class_weights.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            List<Float> weightList = new ArrayList<>();

            int lineNumber = 0; // To track line numbers for debugging

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                lineNumber++;
                if (!line.isEmpty()) {
                    try {
                        float value = Float.parseFloat(line);
                        weightList.add(value);

                        // Log the first 5 weights for verification
                        if (weightList.size() <= 5) {
                            Log.d(TAG, "Weight " + (weightList.size() - 1) + " (Line " + lineNumber + "): " + value);
                        }
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Invalid weight value at line " + lineNumber + ": " + line);
                    }
                }
            }
            reader.close();
            is.close();

            // Convert List<Float> to float[]
            classWeights = new float[weightList.size()];
            for (int i = 0; i < weightList.size(); i++) {
                classWeights[i] = weightList.get(i);
            }

            Log.d(TAG, "Loaded " + classWeights.length + " class weights.");

        } catch (IOException e) {
            Log.e(TAG, "Error loading class weights", e);
            showErrorToUser("Failed to load class weights.");
        }
    }

    /**
     * Computes the Class Activation Map (CAM) based on feature maps and class weights.
     *
     * @param featureMaps    The output feature maps from the model.
     * @param classWeights   The weights of the classification layer.
     * @param featureMapShape The shape of the feature maps tensor.
     * @param outputWidth    The desired width of the CAM bitmap.
     * @param outputHeight   The desired height of the CAM bitmap.
     * @return A Bitmap representing the CAM heatmap.
     */
    private Bitmap computeCAM(float[] featureMaps, float[] classWeights, int[] featureMapShape, int outputWidth, int outputHeight) {
        int batchSize = featureMapShape[0]; // Should be 1
        int h = featureMapShape[1];
        int w = featureMapShape[2];
        int c = featureMapShape[3];

        Log.d(TAG, "computeCAM: h=" + h + ", w=" + w + ", c=" + c);
        Log.d(TAG, "computeCAM: classWeights.length=" + classWeights.length);

        if (classWeights.length != c) {
            Log.e(TAG, "Mismatch between classWeights length and feature map channels.");
            showErrorToUser("Error computing CAM: mismatched weights and feature maps.");
            return null;
        }

        // Reshape featureMaps to [h][w][c]
        float[][][] featureMaps3D = new float[h][w][c];
        int index = 0;
        for (int i = 0; i < h; i++) {
            for (int j = 0; j < w; j++) {
                for (int k = 0; k < c; k++) {
                    featureMaps3D[i][j][k] = featureMaps[index++];
                }
            }
        }

        // Compute the weighted sum
        float[][] cam = new float[h][w];
        for (int i = 0; i < h; i++) {
            for (int j = 0; j < w; j++) {
                float value = 0;
                for (int k = 0; k < c; k++) {
                    value += featureMaps3D[i][j][k] * classWeights[k];
                }
                cam[i][j] = value;
            }
        }

        // Apply ReLU and normalize
        float maxVal = Float.NEGATIVE_INFINITY;
        float minVal = Float.POSITIVE_INFINITY;
        for (int i = 0; i < h; i++) {
            for (int j = 0; j < w; j++) {
                cam[i][j] = Math.max(0, cam[i][j]); // ReLU
                if (cam[i][j] > maxVal) maxVal = cam[i][j];
                if (cam[i][j] < minVal) minVal = cam[i][j];
            }
        }
        float range = maxVal - minVal + 1e-5f; // Avoid division by zero
        for (int i = 0; i < h; i++) {
            for (int j = 0; j < w; j++) {
                cam[i][j] = (cam[i][j] - minVal) / range; // Normalize to [0,1]
            }
        }

        // Create heatmap bitmap
        Bitmap camBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        for (int i = 0; i < h; i++) {
            for (int j = 0; j < w; j++) {
                int value = (int) (cam[i][j] * 255);
                // Apply a colormap or use semi-transparent red
                int color = Color.argb(value / 2, 255, 0, 0); // Adjust transparency as needed
                camBitmap.setPixel(j, i, color);
            }
        }

        // Resize CAM to match the original image size
        return Bitmap.createScaledBitmap(camBitmap, outputWidth, outputHeight, true);
    }

    /**
     * Overlays the CAM heatmap onto the original image.
     *
     * @param heatmap       The CAM heatmap bitmap.
     * @param originalImage The original image bitmap.
     * @return A new bitmap with the heatmap overlay.
     */
    private Bitmap overlayHeatmapOnImage(Bitmap heatmap, Bitmap originalImage) {
        if (heatmap == null) {
            return originalImage;
        }

        Bitmap overlayedImage = Bitmap.createBitmap(originalImage.getWidth(), originalImage.getHeight(), originalImage.getConfig());
        Canvas canvas = new Canvas(overlayedImage);
        Paint paint = new Paint();
        paint.setAntiAlias(true);

        // Draw the original image
        canvas.drawBitmap(originalImage, new Matrix(), paint);

        // Draw the heatmap on top
        canvas.drawBitmap(heatmap, 0, 0, paint);

        return overlayedImage;
    }

    /**
     * Analyzes the questionnaire results and displays appropriate messages.
     *
     * @param asymmetry Indicates if asymmetry was flagged.
     * @param border     Indicates if border irregularity was flagged.
     * @param color      Indicates if color variation was flagged.
     * @param diameter   Indicates if diameter was flagged.
     * @param evolving   Indicates if evolution was flagged.
     */
    private void analyzeResults(boolean asymmetry, boolean border, boolean color, boolean diameter, boolean evolving) {
        List<String> flaggedCriteria = new ArrayList<>();

        if (asymmetry) {
            flaggedCriteria.add("Asymmetry: The mole is asymmetrical, which can be a warning sign for melanoma.");
        }
        if (border) {
            flaggedCriteria.add("Border Irregularity: Uneven or notched borders can indicate a potentially dangerous mole.");
        }
        if (color) {
            flaggedCriteria.add("Color Variation: Multiple colors in a mole are concerning.");
        }
        if (diameter) {
            flaggedCriteria.add("Diameter: Moles larger than 6mm or unusually dark are flagged as concerning.");
        }
        if (evolving) {
            flaggedCriteria.add("Evolution: Changes in size, color, or symptoms like itching or bleeding are significant.");
        }

        // Build results explanation
        if (flaggedCriteria.isEmpty()) {
            resultsTextView.setText("No immediate concerns based on your responses. Keep monitoring for any changes.");
            recommendationTextView.setText("Recommendation: No need to seek medical consultation at this time.");
        } else {
            StringBuilder resultsBuilder = new StringBuilder();
            for (String criteria : flaggedCriteria) {
                resultsBuilder.append(criteria).append("\n\n");
            }
            resultsTextView.setText(resultsBuilder.toString());
            recommendationTextView.setText("Recommendation: We suggest consulting a dermatologist for further evaluation.");
        }
    }

    /**
     * Displays an error message to the user via a Toast.
     *
     * @param message The error message to display.
     */
    private void showErrorToUser(String message) {
        runOnUiThread(() -> {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }
}
