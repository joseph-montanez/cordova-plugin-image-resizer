package info.protonet.imageresizer;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.File;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.camera.FileHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.net.Uri;
import android.os.Environment;

public class ImageResizer extends CordovaPlugin {
    private static final int ARGUMENT_NUMBER = 1;
    public static final int IMAGE_RESIZER_PERMISSIONS = 0;
    protected final static String[] permissions = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    public CallbackContext callbackContext;

    private String uri;
    private String folderName;
    private int quality;
    private int width;
    private int height;
    private Bitmap bitmap;
    public static final int PERMISSION_DENIED_ERROR = 20;

    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        try {
            this.callbackContext = callbackContext;

            if (action.equals("resize")) {
                checkParameters(args);

                // get the arguments
                JSONObject jsonObject = args.getJSONObject(0);
                uri = jsonObject.getString("uri");
                folderName = jsonObject.getString("folderName");
                quality = jsonObject.getInt("quality");
                width = jsonObject.getInt("width");
                height = jsonObject.getInt("height");

                // load the image from uri
                bitmap = loadScaledBitmapFromUri(uri, width, height);

                // correct orientation
                String path = FileHelper.getRealPath(uri, cordova);

                ExifInterface exif = null;
                try {
                    exif = new ExifInterface(path);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
                bitmap = rotateBitmap(bitmap, orientation);


                // save the image as jpeg on the device
                Uri scaledFile = saveFile(bitmap);

//                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, scaledFile.toString()));
                return true;
            } else if (action.equals("correctOrientation")) {
                checkParameters(args);

                // get the arguments
                JSONObject jsonObject = args.getJSONObject(0);
                uri = jsonObject.getString("uri");
                folderName = jsonObject.getString("folderName");
                quality = jsonObject.getInt("quality");

                // load the image from uri
                bitmap = loadBitmapFromUri(uri);

                // correct orientation
                String path = FileHelper.getRealPath(uri, cordova);

                ExifInterface exif = null;
                try {
                    exif = new ExifInterface(path);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
                bitmap = rotateBitmap(bitmap, orientation);


                // save the image as jpeg on the device
                Uri scaledFile = saveFile(bitmap);

                return true;
            } else {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR));
                return false;
            }
        } catch (JSONException e) {
            Log.e("Protonet", "JSON Exception during the Image Resizer Plugin... :(");
        }
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR));
        return false;
    }

    /**
     * Loads a Bitmap of the given android uri path
     *
     * @params uri the URI who points to the image
     **/
    private Bitmap loadScaledBitmapFromUri(String uriString, int width, int height) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(FileHelper.getInputStreamFromUriString(uriString, cordova), null, options);

            //calc aspect ratio
            int[] retval = calculateAspectRatio(options.outWidth, options.outHeight);

            options.inJustDecodeBounds = false;
            options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight, width, height);
            Bitmap unscaledBitmap = BitmapFactory.decodeStream(FileHelper.getInputStreamFromUriString(uriString, cordova), null, options);
            return Bitmap.createScaledBitmap(unscaledBitmap, retval[0], retval[1], true);
        } catch (FileNotFoundException e) {
            Log.e("Protonet", "File not found. :(");
        } catch (IOException e) {
            Log.e("Protonet", "IO Exception :(");
        } catch (Exception e) {
            Log.e("Protonet", e.toString());
        }
        return null;
    }


    /**
     * Loads a Bitmap of the given android uri path
     *
     * @params uri the URI who points to the image
     **/
    private Bitmap loadBitmapFromUri(String uriString) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeStream(FileHelper.getInputStreamFromUriString(uriString, cordova), null, options);
        } catch (FileNotFoundException e) {
            Log.e("Protonet", "File not found. :(");
        } catch (IOException e) {
            Log.e("Protonet", "IO Exception :(");
        } catch (Exception e) {
            Log.e("Protonet", e.toString());
        }
        return null;
    }

    public void onRequestPermissionResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException {
        for (int r : grantResults) {
            if (r == PackageManager.PERMISSION_DENIED) {
                this.callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, PERMISSION_DENIED_ERROR));
                return;
            }
        }
        switch (requestCode) {
            case IMAGE_RESIZER_PERMISSIONS:
                Uri scaledFile = saveFile2(this.bitmap);
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, scaledFile.toString()));
                break;
        }
    }

    private Uri saveFile(Bitmap bitmap) {

        if (!PermissionHelper.hasPermission(this, permissions[0]) && !PermissionHelper.hasPermission(this, permissions[1])) {
            PermissionHelper.requestPermissions(this, IMAGE_RESIZER_PERMISSIONS, permissions);
        } else {
            Uri scaledFile = saveFile2(this.bitmap);
            Log.d("ImageResizer", String.format("scaledFile:%s", scaledFile.toString()));
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, scaledFile.toString()));
        }

        return null;
    }

    private Uri saveFile2(Bitmap bitmap) {
        File folder = new File(Environment.getExternalStorageDirectory() + "/" + folderName);
        boolean success = true;
        if (!folder.exists()) {
            success = folder.mkdir();
        }

        if (success) {
            String fileName = System.currentTimeMillis() + ".jpg";
            File file = new File(folder, fileName);
            if (file.exists()) file.delete();
            try {
                FileOutputStream out = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
                out.flush();
                out.close();
            } catch (Exception e) {
                Log.e("Protonet", e.toString());
            }
            return Uri.fromFile(file);
        }
        return null;
    }

    /**
     * Figure out what ratio we can load our image into memory at while still being bigger than
     * our desired width and height
     *
     * @param srcWidth
     * @param srcHeight
     * @param dstWidth
     * @param dstHeight
     * @return
     */
    private int calculateSampleSize(int srcWidth, int srcHeight, int dstWidth, int dstHeight) {
        final float srcAspect = (float) srcWidth / (float) srcHeight;
        final float dstAspect = (float) dstWidth / (float) dstHeight;

        if (srcAspect > dstAspect) {
            return srcWidth / dstWidth;
        } else {
            return srcHeight / dstHeight;
        }
    }

    /**
     * Maintain the aspect ratio so the resulting image does not look smooshed
     *
     * @param origWidth
     * @param origHeight
     * @return
     */
    private int[] calculateAspectRatio(int origWidth, int origHeight) {
        int newWidth = width;
        int newHeight = height;

        // If no new width or height were specified return the original bitmap
        if (newWidth <= 0 && newHeight <= 0) {
            newWidth = origWidth;
            newHeight = origHeight;
        }
        // Only the width was specified
        else if (newWidth > 0 && newHeight <= 0) {
            newHeight = (newWidth * origHeight) / origWidth;
        }
        // only the height was specified
        else if (newWidth <= 0 && newHeight > 0) {
            newWidth = (newHeight * origWidth) / origHeight;
        }
        // If the user specified both a positive width and height
        // (potentially different aspect ratio) then the width or height is
        // scaled so that the image fits while maintaining aspect ratio.
        // Alternatively, the specified width and height could have been
        // kept and Bitmap.SCALE_TO_FIT specified when scaling, but this
        // would result in whitespace in the new image.
        else {
            double newRatio = newWidth / (double) newHeight;
            double origRatio = origWidth / (double) origHeight;

            if (origRatio > newRatio) {
                newHeight = (newWidth * origHeight) / origWidth;
            } else if (origRatio < newRatio) {
                newWidth = (newHeight * origWidth) / origHeight;
            }
        }

        int[] retval = new int[2];
        retval[0] = newWidth;
        retval[1] = newHeight;
        return retval;
    }

    private boolean checkParameters(JSONArray args) {
        if (args.length() != ARGUMENT_NUMBER) {
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
            return false;
        }
        return true;
    }

    public static Bitmap rotateBitmap(Bitmap bitmap, int orientation) {

        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_NORMAL:
                return bitmap;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setRotate(180);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90);
                break;
            default:
                return bitmap;
        }
        try {
            Bitmap bmRotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            bitmap.recycle();
            return bmRotated;
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            return null;
        }
    }
}
