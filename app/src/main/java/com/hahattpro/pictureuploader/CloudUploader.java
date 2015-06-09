package com.hahattpro.pictureuploader;

import android.content.Context;

import java.io.InputStream;

/**
 * Created by Thien on 6/8/2015.
 * Upload to google drive, dropbox
 */
public class CloudUploader {
    //--------------------------Variable declaration------------------------------
    //init
    Context context; //activity context
    String APP_FOLDER_NAME;

    //Prepare for Upload
    InputStream is;
    String File_Name;//name with extension
     Long File_length;


    //success flag
    boolean dropbox = false;
    boolean google_drive = false;

    public CloudUploader(Context context, String APP_FOLDER_NAME) {
        this.context = context;
        this.APP_FOLDER_NAME = APP_FOLDER_NAME;
    }

    public void UploadFile(String FileName, InputStream inputStream, Long FileLength ){


    }




}
