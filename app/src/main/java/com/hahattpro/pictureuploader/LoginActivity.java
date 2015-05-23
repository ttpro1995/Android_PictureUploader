package com.hahattpro.pictureuploader;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.session.AppKeyPair;
import com.hahattpro.pictureuploader.StaticField.AppIDandSecret;


public class LoginActivity extends ActionBarActivity {


    DropboxAPI<AndroidAuthSession> Dropbox_mApi=null;
    private String Dropbox_token=null;

    SharedPreferences prefs;
    SharedPreferences.Editor editor;

    Button buttonLoginDropbox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        buttonLoginDropbox = (Button) findViewById(R.id.button_login_dropbox);

        //init prefs which is used to store access token
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        editor = prefs.edit();

        //get token
        Dropbox_token = prefs.getString(getResources().getString(R.string.prefs_dropbox_token), null);





        buttonLoginDropbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (Dropbox_mApi!=null &&Dropbox_mApi.getSession().isLinked())
                {
                    Dropbox_mApi.getSession().unlink();//logout
                    editor.remove(getResources().getString(R.string.prefs_dropbox_token));
                    editor.commit();//remember to commit
                    UpdateUI();
                }
                    else
                new LoginDropbox().execute();
            }
        });
    }



    protected void onResume() {
        super.onResume();
        UpdateUI();
        //auto login if have access token
        if (Dropbox_token!= null)
            new LoginDropbox().execute();

        if (Dropbox_mApi!=null)
        if (Dropbox_mApi.getSession().authenticationSuccessful()) {
            try {
                // Required to complete auth, sets the access token on the session
                Dropbox_mApi.getSession().finishAuthentication();
                String ACCESS_TOKEN = Dropbox_mApi.getSession().getOAuth2AccessToken();
                editor.putString(getResources().getString(R.string.prefs_dropbox_token),ACCESS_TOKEN);//put access token into prefs
                editor.commit();//commit or it will not save
                //accessToken should be save somewhere
                //TODO: accessToken ?
                Log.i("DbAuthLog", "Login successful");
                UpdateUI();
            } catch (IllegalStateException e) {
                Log.i("DbAuthLog", "Error authenticating", e);
            }
        }
    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_login, menu);
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

        return super.onOptionsItemSelected(item);
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


    //open browser, login, ask for permission
    private class LoginDropbox extends AsyncTask<Void,Void,Void>
    {
        @Override
        protected Void doInBackground(Void... params) {

            Dropbox_token=prefs.getString(getResources().getString(R.string.prefs_dropbox_token),null);

            // bind APP_KEY and APP_SECRET with session
            AndroidAuthSession session = buildSession();
            Dropbox_mApi = new DropboxAPI<AndroidAuthSession>(session);

            if (Dropbox_token!=null)
                Dropbox_mApi.getSession().setOAuth2AccessToken(Dropbox_token);

            if (Dropbox_token==null)
            Dropbox_mApi.getSession().startOAuth2Authentication(LoginActivity.this);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            if (Dropbox_mApi.getSession().authenticationSuccessful()) {
                try {
                    Dropbox_mApi.getSession().finishAuthentication();
                    UpdateUI();
                } catch (IllegalStateException e) {
                    Log.i("DbAuthLog", "Error authenticating", e);
                }
            }
        }
    }

    //show status of account button
    private void UpdateUI()
    {
        if (Dropbox_mApi!=null &&Dropbox_mApi.getSession().isLinked())
            buttonLoginDropbox.setText("Unlink Dropbox");
        else
            buttonLoginDropbox.setText("Login Dropbox");

    }

}
