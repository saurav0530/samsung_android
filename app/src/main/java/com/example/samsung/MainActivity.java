package com.example.samsung;
import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Base64;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.StringRequest;
import com.github.chrisbanes.photoview.PhotoView;
import com.yalantis.ucrop.UCrop;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_CODE = 1000;                                        // Code for camera access permission
    private static final int IMAGE_CAPTURE_CODE = 1001;                                     // Code for capturing image from camera
    private static final int PICKFILE_RESULT_CODE = 1002;                                   // Code for browsing image intent
    private static String static_url;                                                       // API URL
    private Bitmap bitmap;

    Button capture, upload, browse;
    ProgressDialog progressDialog;
    PhotoView imageView;
    Uri imageUri, newImageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Setting up AlertDialog to take API URL input
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter server URL");

        // Set up the input
        final EditText input = new EditText(this);
        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("Done", (dialog, which) -> static_url = input.getText().toString());
        builder.setCancelable(false);

        builder.show();

        imageView   = findViewById(R.id.photo);                     // ImageView to show image
        capture     = findViewById(R.id.capture);                   // Button for capturing image
        upload      = findViewById(R.id.upload);                    // Button to upload and process image
        browse      = findViewById(R.id.browse);                    // Button to browse existing image

        capture.setOnClickListener(view -> captureImage());         // Calling captureImage() when CAMERA button is clicked
        browse.setOnClickListener(view -> browseImage());           // Calling browseImage() when BROWSE button is clicked
        upload.setOnClickListener(view -> uploadImage());           // Calling uploadImage() when GET RESULT button is clicked
    }

    // Function for handling request of selection of file from directory
    private void browseImage()
    {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "New Picture");
        values.put(MediaStore.Images.Media.DESCRIPTION, "From the Camera");

        imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);

        Intent intent1 = new Intent(Intent.ACTION_GET_CONTENT);
        intent1.addCategory(Intent.CATEGORY_OPENABLE);
        intent1.setType("image/*");
        intent1 = Intent.createChooser(intent1, "Choose a file");
        intent1.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        startActivityForResult(intent1, PICKFILE_RESULT_CODE);
    }

    // Function for handling request of capturing image from camera
    private void captureImage()
    {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if(checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED ||
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED){
                String [] permission = {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
                requestPermissions(permission,PERMISSION_CODE);
            }
            else {
                openCamera();
            }
        }
        else{
            openCamera();
        }
    }

    // Function for communicating with API for uploading image
    private void uploadImage()
    {
        // Setting progress dialog
        progressDialog = new ProgressDialog(MainActivity.this);
        progressDialog.setTitle("");
        if (bitmap==null)
        {
            Toast.makeText(MainActivity.this, "Please select an image first", Toast.LENGTH_SHORT).show();
            return;
        }
        progressDialog.setMessage("Uploading...");
        progressDialog.setTitle("");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.setCancelable(false);
        progressDialog.show();

        String uploadURL = static_url+"/upload";
        RequestQueue requestQueue = VolleySingleton.getInstance(this).getRequestQueue();

        // Making an API call
        StringRequest stringRequest = new StringRequest(Request.Method.POST, uploadURL,
                s -> {
                    try {
                        JSONObject jsonObject = new JSONObject(s);
                        String Response = jsonObject.getString("response");
                        Toast.makeText(MainActivity.this, Response, Toast.LENGTH_LONG).show();
                        submitAndProcess();                                 // Calling submitAndProcess() once upload task is completed
                    } catch (JSONException e) {
                        Toast.makeText(MainActivity.this, "Something went wrong...", Toast.LENGTH_LONG).show();
                        e.printStackTrace();
                        progressDialog.dismiss();
                    }
                }, volleyError -> {
                        Toast.makeText(MainActivity.this, "Something went wrong...", Toast.LENGTH_LONG).show();
                        progressDialog.dismiss();
                }){
                    @Override
                    protected Map<String, String> getParams(){
                        Map<String,String> params = new HashMap<>();
                        params.put("name","img_13");
                        params.put("image",imageToString(bitmap));
                        return params;
                    }
        };
        stringRequest.setRetryPolicy(new DefaultRetryPolicy(0, -1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        stringRequest.setShouldCache(false);
        requestQueue.add(stringRequest);
    }

    // Function for communicating with API for processing image request
    void submitAndProcess() {
        progressDialog.setMessage("Processing...");
        RequestQueue requestQueue = VolleySingleton.getInstance(this).getRequestQueue();
        String processURL = static_url + "/result";

        // Making an API call
        ImageRequest imageRequest=new ImageRequest (processURL, response -> {
            progressDialog.dismiss();
            imageView.setImageBitmap(response);
            Toast.makeText(MainActivity.this,"Successful",Toast.LENGTH_SHORT).show();
            progressDialog.dismiss();
        },0,0, ImageView.ScaleType.CENTER_CROP,null, error -> {
            Toast.makeText(MainActivity.this, "Something went wrong...", Toast.LENGTH_LONG).show();
            error.printStackTrace();
            progressDialog.dismiss();
        });

        imageRequest.setRetryPolicy(new DefaultRetryPolicy(0, -1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        imageRequest.setShouldCache(false);
        requestQueue.add(imageRequest);
    }

    // Function which convert Bitmap type to String
    private String imageToString(Bitmap bitmap)
    {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG,100,byteArrayOutputStream);
        byte [] imgBytes = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(imgBytes,Base64.DEFAULT);
    }

    // Function for opening camera intent
    private void openCamera() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "New Picture");
        values.put(MediaStore.Images.Media.DESCRIPTION, "From the Camera");

        imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);

        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        startActivityForResult(cameraIntent, IMAGE_CAPTURE_CODE);
    }

    // Function for checking access of camera
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull @org.jetbrains.annotations.NotNull String[] permissions, @NonNull @org.jetbrains.annotations.NotNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == PERMISSION_CODE){
            if(grantResults.length > 0 && grantResults[0]==PackageManager.PERMISSION_GRANTED)
            {
                openCamera();
            }
            else
            {
                Toast.makeText(this,"Permission denied...", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Function for handling various activity results
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable @org.jetbrains.annotations.Nullable Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == IMAGE_CAPTURE_CODE && resultCode == RESULT_OK)                                           // handles result of capturing image from camera intent
        {
            newImageUri =  Uri.fromFile(new File(this.getCacheDir(), UUID.randomUUID().toString() + ".jpg"));
            UCrop.of(imageUri,newImageUri).start(MainActivity.this);

        }
        else if (requestCode == PICKFILE_RESULT_CODE && resultCode == RESULT_OK)                                    // handles result of existing file browsing intent
        {
            assert data != null;
            imageUri = data.getData();
            newImageUri =  Uri.fromFile(new File(this.getCacheDir(), UUID.randomUUID().toString() + ".jpg"));
            UCrop.of(imageUri,newImageUri).start(MainActivity.this);

        }
        else if (resultCode == RESULT_OK && requestCode == UCrop.REQUEST_CROP)                                      // handles result of UCrop intent
        {
            assert data != null;
            imageUri = UCrop.getOutput(data);
            imageView.setImageURI(imageUri);
            try {
                bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (resultCode == UCrop.RESULT_ERROR) {
            assert data != null;
            Objects.requireNonNull(UCrop.getError(data)).printStackTrace();
            Toast.makeText(MainActivity.this, "Something went wrong...", Toast.LENGTH_LONG).show();
        }
    }
}