package com.hahattpro.pictureuploader;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.session.AppKeyPair;
import com.hahattpro.pictureuploader.StaticField.AppIDandSecret;
import com.hahattpro.pictureuploader.StaticField.Dir;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;


public class MainActivity extends ActionBarActivity {

    private String LOG_TAG=MainActivity.class.getSimpleName();

    DropboxAPI<AndroidAuthSession> Dropbox_mApi=null;
    private String Dropbox_token=null;

    SharedPreferences prefs;
    SharedPreferences.Editor editor;

    private int SELECT_PICTURE=1;//select picture request code
    Uri pic_uri=null;

    //view
    ImageView imageView;
    Button buttonSelect;
    Button buttonUpload;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = (ImageView) findViewById(R.id.imageview1);
        buttonSelect = (Button) findViewById(R.id.button_select);
        buttonUpload = (Button) findViewById(R.id.button_upload);

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

    //go to activity where you will login, get access token
    private void GoToAccountManager()
    {
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        startActivity(intent);
    }


    //dropbox document ask for this, but still don't know what it is use for ?
    protected void onResume() {
        super.onResume();

        if (Dropbox_mApi!=null)
        if (Dropbox_mApi.getSession().authenticationSuccessful()) {
            try {
                // Required to complete auth, sets the access token on the session
                Dropbox_mApi.getSession().finishAuthentication();
                String ACCESS_TOKEN = Dropbox_mApi.getSession().getOAuth2AccessToken();
                editor.putString(getResources().getString(R.string.prefs_dropbox_token), ACCESS_TOKEN);//put access token into prefs
                editor.commit();//remember to commit or it will not work
                Log.i("dropbox_token",prefs.getString(getResources().getString(R.string.prefs_dropbox_token),null));
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
        if (id == R.id.action_account_manager)
        {
            GoToAccountManager();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode==SELECT_PICTURE)
        {
            //show picture you  selected
            if (data!=null) {
                 pic_uri = data.getData();
                imageView.setImageURI(pic_uri);
            }
        }

    }

    ///////>>>>>>HELPER FUNCTION<<<<<<///////////////////

    //build AndroidAuthSession
    private AndroidAuthSession buildSession()
    {
        // APP_KEY and APP_SECRET goes here
        AppKeyPair appKeyPair= new AppKeyPair(AppIDandSecret.AppID_Dropbox,AppIDandSecret.Secret_Dropbox);

        AndroidAuthSession session = new AndroidAuthSession(appKeyPair,Dropbox_token);

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
        String file_name =  returnCursor.getString(nameIndex);
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

    //open browser, login, ask for permission then upload
    private class LoginDropboxAndUpload extends AsyncTask<Void,Void,Void>
    {
        @Override
        protected Void doInBackground(Void... params) {
            //get token
            Dropbox_token = prefs.getString(getResources().getString(R.string.prefs_dropbox_token),null);
            // bind APP_KEY and APP_SECRET with session and Access token
            AndroidAuthSession session = buildSession();
            Dropbox_mApi = new DropboxAPI<AndroidAuthSession>(session);

            if (Dropbox_token!=null)
                Dropbox_mApi.getSession().setOAuth2AccessToken(Dropbox_token);

            if (Dropbox_token==null)
            Dropbox_mApi.getSession().startOAuth2Authentication(MainActivity.this);

            if (Dropbox_mApi.getSession().isLinked())
                Log.i(LOG_TAG,"Dropbox is Link");
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            new UploadPicture_Dropbox().execute();
        }
    }

    //Upload select picture (which is shown on image view to dropbox
    private class UploadPicture_Dropbox extends AsyncTask<Void,Void,Void>
    {
        @Override
        protected Void doInBackground(Void... voids) {
            try {

                //File file = new File(getRealPathFromURI(pic_uri));
                InputStream is = getContentResolver().openInputStream(pic_uri);

                Log.i(LOG_TAG,"File name = "+getFileName(pic_uri));
                Log.i(LOG_TAG,"File size = "+getFileSize(pic_uri));

                //upload to dropbox
                Dropbox_mApi.putFile(Dir.PICTURE_DIR+getFileName(pic_uri)
                ,is
                ,getFileSize(pic_uri)
                ,null
                ,null);
            }
           catch (FileNotFoundException e)
           {
               Log.e(LOG_TAG,"FILE_NOT_FOUND");
           }
            catch (DropboxException e)
            {
                Log.e(LOG_TAG,"DROPBOX_ERROR");
            }



            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            Log.i(LOG_TAG,"Dropbox Upload Complete");
        }
    }

}
