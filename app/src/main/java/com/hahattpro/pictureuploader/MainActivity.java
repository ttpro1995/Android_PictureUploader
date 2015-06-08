package com.hahattpro.pictureuploader;

import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.session.AppKeyPair;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.MetadataChangeSet;
import com.hahattpro.pictureuploader.StaticField.AppIDandSecret;
import com.hahattpro.pictureuploader.StaticField.Dir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class MainActivity extends ActionBarActivity {

    //LOGtag
    private String CAMERA_LOG_TAG ="CAMERA";
    final String GOOGLEDRIVE_LOG_TAG ="Google Drive";

    DropboxAPI<AndroidAuthSession> Dropbox_mApi = null;
    SharedPreferences prefs;
    SharedPreferences.Editor editor;
    Uri pic_uri = null;
    //view
    ImageView imageView;
    Button buttonSelect;
    Button buttonUpload;
    Button buttonCamera;
    TextView textStatus;
    // flag
    boolean error_Dropbox = false;
    private String LOG_TAG = MainActivity.class.getSimpleName();
    private String Dropbox_token = null;

    private int SELECT_PICTURE = 1;//select picture request code
    private int CAPTURE_IMAGE= 2;//
    private int CURRENT_REQUEST=0;
    final int GOOGLE_DRIVE_LOGIN_REQUEST_CODE = 100;


    //google drive
    GoogleApiClient mGoogleApiClient;
    GoogleApiClient.OnConnectionFailedListener onConnectionFailedListener;
    GoogleApiClient.ConnectionCallbacks connectionCallbacks;

    //DIR of image
    String IMAGE_DIR = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString();
    int IMAGE_NUM=0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        Log.i(LOG_TAG,"Keep calm and meow on");

        imageView = (ImageView) findViewById(R.id.imageview1);
        buttonSelect = (Button) findViewById(R.id.button_select);
        buttonUpload = (Button) findViewById(R.id.button_upload);
        buttonCamera = (Button) findViewById(R.id.button_camera);


        textStatus = (TextView) findViewById(R.id.textStatus);
        buttonUpload.setEnabled(false);

        //init prefs which is used to store access token
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        editor = prefs.edit();


        buttonSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectPic();
            }
        });

        buttonUpload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new LoginDropboxAndUpload().execute();
                new UploadGoogleDrive().execute();

            }
        });

        buttonCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                takePhoto();
            }
        });

    }


    //open chooser to chose picture
    private void selectPic() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), SELECT_PICTURE);
    }

    private void takePhoto(){
        Intent intent = new Intent();
        intent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);


        File image ;
        image = new File(IMAGE_DIR+"/"+"image"+IMAGE_NUM+".JPG");
        while (image.exists())
        {
            IMAGE_NUM++;
            image = new File(IMAGE_DIR+"/"+"image"+IMAGE_NUM+".JPG");
        }

         pic_uri = Uri.fromFile(image);

        intent.putExtra(MediaStore.EXTRA_OUTPUT, pic_uri);

        startActivityForResult(intent, CAPTURE_IMAGE);

    }

    //go to activity where you will login, get access token
    private void GoToAccountManager() {
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        startActivity(intent);
    }


    //dropbox document ask for this, but still don't know what it is use for ?
    protected void onResume() {
        super.onResume();

        if (Dropbox_mApi != null)
            if (Dropbox_mApi.getSession().authenticationSuccessful()) {
                try {
                    // Required to complete auth, sets the access token on the session
                    Dropbox_mApi.getSession().finishAuthentication();
                    String ACCESS_TOKEN = Dropbox_mApi.getSession().getOAuth2AccessToken();
                    editor.putString(getResources().getString(R.string.prefs_dropbox_token), ACCESS_TOKEN);//put access token into prefs
                    editor.commit();//remember to commit or it will not work
                    Log.i("dropbox_token", prefs.getString(getResources().getString(R.string.prefs_dropbox_token), null));
                    //accessToken should be save somewhere
                    //TODO: accessToken ?
                    Log.i("DbAuthLog", "Login successful");

                } catch (IllegalStateException e) {
                    Log.i("DbAuthLog", "Error authenticating", e);
                }
            }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        if (id == R.id.action_account_manager) {
            GoToAccountManager();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SELECT_PICTURE) {
            //show picture you  selected
            if (data != null) try {
                pic_uri = data.getData();
                imageView.setImageURI(pic_uri);
                if (pic_uri != null)
                    buttonUpload.setEnabled(true);
                CURRENT_REQUEST = SELECT_PICTURE;
                InputStream is = getContentResolver().openInputStream(pic_uri);
                Log.i(LOG_TAG, "File name = " + getFileName(pic_uri));
                Log.i(LOG_TAG, "File size = " + getFileSize(pic_uri));
                String FileName = getFileName(pic_uri);
                long FileLength=getFileSize(pic_uri);

                //TODO: pass FileName, FileLength,inputStream to upload method

            }
            catch (Exception e){e.printStackTrace();}
        }

        if (requestCode == CAPTURE_IMAGE) {
            if (resultCode == RESULT_OK) try {
                // Image captured and saved to fileUri specified in the Intent
                Log.i(CAMERA_LOG_TAG,"CAMERA Path = "+ pic_uri.getPath());
                imageView.setImageURI(pic_uri);
                if (pic_uri != null)
                    buttonUpload.setEnabled(true);
                CURRENT_REQUEST = CAPTURE_IMAGE;
                File myFile = new File(pic_uri.getPath());
                InputStream is = new FileInputStream(myFile);
                //TODO: pass FileName, FileLength,inputStream to upload method
            } catch (Exception e){e.printStackTrace();}
            else if (resultCode == RESULT_CANCELED) {
                // User cancelled the image capture
            } else {
                // Image capture failed, advise user
            }
        }

    }

    ///////>>>>>>HELPER FUNCTION<<<<<<///////////////////

    //build AndroidAuthSession
    private AndroidAuthSession buildSession() {
        // APP_KEY and APP_SECRET goes here
        AppKeyPair appKeyPair = new AppKeyPair(AppIDandSecret.AppID_Dropbox, AppIDandSecret.Secret_Dropbox);

        AndroidAuthSession session = new AndroidAuthSession(appKeyPair, Dropbox_token);

        return session;
    }

    //get file name from uri
    private String getFileName(Uri contentURI) {
        //https://developer.android.com/training/secure-file-sharing/retrieve-info.html
        Uri returnUri = pic_uri;
        Cursor returnCursor =
                getContentResolver().query(returnUri, null, null, null, null);
        int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
        returnCursor.moveToFirst();
        String file_name = returnCursor.getString(nameIndex);
        returnCursor.close();
        return file_name;
    }

    //get file size from uri
    private long getFileSize(Uri contentURI) {
        //https://developer.android.com/training/secure-file-sharing/retrieve-info.html
        Uri returnUri = pic_uri;
        Cursor returnCursor =
                getContentResolver().query(returnUri, null, null, null, null);
        int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
        returnCursor.moveToFirst();
        long file_size = returnCursor.getLong(sizeIndex);
        returnCursor.close();
        return file_size;
    }

    //set token to Dropbox_mApi
    private class LoginDropboxAndUpload extends AsyncTask<Void, Void, Void> {



        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            textStatus.setText("Status: Uploading");
        }

        @Override
        protected Void doInBackground(Void... params) {
            //get token
            Dropbox_token = prefs.getString(getResources().getString(R.string.prefs_dropbox_token), null);
            // bind APP_KEY and APP_SECRET with session and Access token
            AndroidAuthSession session = buildSession();
            Dropbox_mApi = new DropboxAPI<AndroidAuthSession>(session);

            if (Dropbox_token != null)
                Dropbox_mApi.getSession().setOAuth2AccessToken(Dropbox_token);

            if (Dropbox_token == null) {
                //dropbox not link because token is null
                // Login at Login Activity
                error_Dropbox = true;
                Log.e(LOG_TAG, "Dropbox is not linked");
            }
            if (Dropbox_mApi.getSession().isLinked())
                Log.i(LOG_TAG, "Dropbox is Link");
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            if (Dropbox_mApi.getSession().isLinked())
                new UploadPicture_Dropbox().execute();

            if (error_Dropbox) {
                String tmp = "Dropbox Upload Error. ";
                if (Dropbox_token == null) {
                    tmp = tmp + "Dropbox is not linked";
                }
                textStatus.setText(tmp);
            }

        }
    }

    //Upload select picture (which is shown on image view to dropbox
    private class UploadPicture_Dropbox extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {

            if (CURRENT_REQUEST == SELECT_PICTURE)
            try {
                //File file = new File(getRealPathFromURI(pic_uri));
                InputStream is = getContentResolver().openInputStream(pic_uri);

                Log.i(LOG_TAG, "File name = " + getFileName(pic_uri));
                Log.i(LOG_TAG, "File size = " + getFileSize(pic_uri));

                //upload to dropbox
                Dropbox_mApi.putFile(Dir.PICTURE_DIR + getFileName(pic_uri)
                        , is
                        , getFileSize(pic_uri)
                        , null
                        , null);
            } catch (FileNotFoundException e) {
                Log.e(LOG_TAG, "FILE_NOT_FOUND");
                error_Dropbox = true;
            } catch (DropboxException e) {
                Log.e(LOG_TAG, "DROPBOX_ERROR");
                error_Dropbox = true;
            } catch (NullPointerException e) {
                Log.e(LOG_TAG, "no picture is selected");
                e.printStackTrace();
                error_Dropbox = true;
            }


            if (CURRENT_REQUEST == CAPTURE_IMAGE)
                try{
                    File myFile = new File(pic_uri.getPath());
                    InputStream is = new FileInputStream(myFile);
                    Dropbox_mApi.putFile(Dir.PICTURE_DIR +myFile.getName()
                            , is
                            , myFile.length()
                            , null
                            , null);
                }
                catch (FileNotFoundException er){er.printStackTrace();}
                catch (DropboxException er){ er.printStackTrace();}


            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            Log.i(LOG_TAG, "Dropbox Upload Complete");

            if (error_Dropbox)
                textStatus.setText("Dropbox Error");
            else
                textStatus.setText("Dropbox Upload Complete");


        }
    }

    ////////////////google drive ////////////


    private class UploadGoogleDrive extends AsyncTask<Void,Void,Void> {

        DriveId mFolderDriveId = null;
        @Override
        protected void onPreExecute() {
            super.onPreExecute();

        }

        @Override
        protected Void doInBackground(Void... params) {
            LoginGoogleDrive();//start connecting to google api
            try {//wait for it
                while (mGoogleApiClient==null||!mGoogleApiClient.isConnected())
                    Thread.sleep(100);
            }
            catch (InterruptedException e){}

            //DriveFolder root = Drive.DriveApi.getRootFolder(mGoogleApiClient);
            //DriveFile driveFile;



            if (CURRENT_REQUEST == SELECT_PICTURE)
                try {
                    //File file = new File(getRealPathFromURI(pic_uri));
                    InputStream is = getContentResolver().openInputStream(pic_uri);

                    Log.i(LOG_TAG, "File name = " + getFileName(pic_uri));
                    Log.i(LOG_TAG, "File size = " + getFileSize(pic_uri));

                    SaveFileToDrive(getFileName(pic_uri),is);

                } catch (FileNotFoundException e) {e.printStackTrace();}
                catch (Exception e) {e.printStackTrace();}

            if (CURRENT_REQUEST == CAPTURE_IMAGE)
                try{
                    File myFile = new File(pic_uri.getPath());
                    InputStream is = new FileInputStream(myFile);
                    SaveFileToDrive(myFile.getName(),is);
                }
                catch (FileNotFoundException er){er.printStackTrace();}
                catch (Exception e){e.printStackTrace();}
            return null;
        }



        /*
        * Input: title, inputstream
        * Upload to google drive
        * */
        private void SaveFileToDrive(final String title, InputStream is)
        {
            // Start by creating a new contents, and setting a callback.
            Log.i(GOOGLEDRIVE_LOG_TAG, "Creating new contents.");

            final InputStream inputStream = is;

            //Get DriveID of old Drive folder is created
            String tmp_driveid = prefs.getString(getResources().getString(R.string.prefs_googledrive_folder),null);
            if (tmp_driveid==null)
            mFolderDriveId =  null;
            else
            mFolderDriveId = DriveId.decodeFromString(tmp_driveid);

            //Create PictureUploader  Folder if there are no PictureUploader folder
            if (mFolderDriveId == null){
            MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                    .setTitle("PictureUploader").build();
            Drive.DriveApi.getRootFolder(mGoogleApiClient).createFolder(
                    mGoogleApiClient, changeSet).setResultCallback(callback);}

            try {//wait for creating new folder
                while (mFolderDriveId == null)
                    Thread.sleep(1000);
            }
            catch (InterruptedException e){e.printStackTrace();}



            Drive.DriveApi.newDriveContents(mGoogleApiClient)
                    .setResultCallback(new ResultCallback<DriveApi.DriveContentsResult>() {

                        @Override
                        public void onResult(DriveApi.DriveContentsResult result) {
                            // If the operation was not successful, we cannot do anything
                            // and must
                            // fail.
                            if (!result.getStatus().isSuccess()) {
                                Log.i(GOOGLEDRIVE_LOG_TAG, "Failed to create new contents.");
                                return;
                            }
                            // Otherwise, we can write our data to the new contents.
                            Log.i(GOOGLEDRIVE_LOG_TAG, "New contents created.");
                            // Get an output stream for the contents.
                            OutputStream outputStream = result.getDriveContents().getOutputStream();
                            // Write the bitmap data from it.


                            try {
                                //Copy from input stream to output stream
                                org.apache.commons.io.IOUtils.copy(inputStream, outputStream);
                            } catch (IOException e1) {
                                Log.i(GOOGLEDRIVE_LOG_TAG, "Unable to write file contents.");
                            }
                            // Create the initial metadata - MIME type and title.
                            // Note that the user will be able to change the title later.

                            // Create an intent for the file chooser, and start it.
                            /*
                            IntentSender intentSender = Drive.DriveApi
                                    .newCreateFileActivityBuilder()
                                    .setInitialMetadata(metadataChangeSet)
                                    .setInitialDriveContents(result.getDriveContents())
                                    .build(mGoogleApiClient);
                            try {
                                startIntentSenderForResult(
                                        intentSender, 0, null, 0, 0, 0);
                            } catch (IntentSender.SendIntentException e) {
                                Log.i(GOOGLEDRIVE_LOG_TAG, "Failed to launch file chooser.");
                            }
                            */


                            //Create a file at root folder
                            MetadataChangeSet metadataChangeSet = new MetadataChangeSet.Builder()
                                    .setMimeType("image/jpeg").setTitle(title).build();
                            DriveFolder root = Drive.DriveApi.getFolder(mGoogleApiClient,mFolderDriveId);
                            root.createFile(mGoogleApiClient, metadataChangeSet, result.getDriveContents()).setResultCallback(fileCallback);

                        }
                    });
        }

        //create new file
        final private ResultCallback<DriveFolder.DriveFileResult> fileCallback = new
                ResultCallback<DriveFolder.DriveFileResult>() {
                    @Override
                    public void onResult(DriveFolder.DriveFileResult result) {
                        if (!result.getStatus().isSuccess()) {
                            Log.e(GOOGLEDRIVE_LOG_TAG,"Error while trying to create the file");
                            return;
                        }
                        Log.i(GOOGLEDRIVE_LOG_TAG,"Created a file  "
                                + result.getDriveFile().getDriveId());
                    }
                };

        //Create new folder
        final ResultCallback<DriveFolder.DriveFolderResult> callback = new ResultCallback<DriveFolder.DriveFolderResult>() {
            @Override
            public void onResult(DriveFolder.DriveFolderResult result) {
                if (!result.getStatus().isSuccess()) {
                    Log.e(GOOGLEDRIVE_LOG_TAG,"Error while trying to create the folder");
                    return;
                }
                Log.i(GOOGLEDRIVE_LOG_TAG, "Created a folder: " + result.getDriveFolder().getDriveId());

                //store new drive id into prefs
                 mFolderDriveId = result.getDriveFolder().getDriveId();
                editor.clear();
                editor.putString(getResources().getString(R.string.prefs_googledrive_folder), result.getDriveFolder().getDriveId().encodeToString());
                editor.commit();
            }
        };
    }

    //Connect to google api (maybe it is run its own thread ?)
    private void LoginGoogleDrive() {

        //It is auto-gen constructor
        onConnectionFailedListener = new GoogleApiClient.OnConnectionFailedListener() {
            @Override
            public void onConnectionFailed(ConnectionResult connectionResult) {
                Log.i(GOOGLEDRIVE_LOG_TAG, "onConnectionFailed");
                if (connectionResult.hasResolution()) {
                    try {
                        connectionResult.startResolutionForResult(MainActivity.this, GOOGLE_DRIVE_LOGIN_REQUEST_CODE);
                    } catch (IntentSender.SendIntentException e) {
                        // Unable to resolve, message user appropriately
                        Log.i(GOOGLEDRIVE_LOG_TAG, "something wrong");
                        e.printStackTrace();
                    }
                } else {
                    GooglePlayServicesUtil.getErrorDialog(connectionResult.getErrorCode(),MainActivity.this, 0).show();
                }
            }
        };

        //It is auto-gen constructor
        connectionCallbacks = new GoogleApiClient.ConnectionCallbacks() {
            @Override
            public void onConnected(Bundle bundle) {
                Log.i(GOOGLEDRIVE_LOG_TAG, "onConnected call back");
            }

            @Override
            public void onConnectionSuspended(int i) {
                Log.i(GOOGLEDRIVE_LOG_TAG, "onConnectionSuspended call back");
            }
        };

        //link google account (will appear account chooser)
        mGoogleApiClient = new GoogleApiClient.Builder(MainActivity.this)
                .addApi(Drive.API)
                .addScope(Drive.SCOPE_FILE)
                .addScope(Drive.SCOPE_APPFOLDER)
                .addConnectionCallbacks(connectionCallbacks)
                .addOnConnectionFailedListener(onConnectionFailedListener)
                .build();
        mGoogleApiClient.connect();
    }
}

//TODO: FIX upload task so it take inputstream, file name, file length, DIR (folder )  to upload
//TODO: move to new class
