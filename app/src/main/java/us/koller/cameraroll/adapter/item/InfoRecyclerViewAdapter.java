package us.koller.cameraroll.adapter.item;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.OpenableColumns;
import android.support.media.ExifInterface;
import android.support.v4.content.ContextCompat;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.SimpleTarget;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import us.koller.cameraroll.R;
import us.koller.cameraroll.data.AlbumItem;
import us.koller.cameraroll.data.Gif;
import us.koller.cameraroll.data.Photo;
import us.koller.cameraroll.util.ExifUtil;
import us.koller.cameraroll.util.Rational;

import static android.content.Context.CLIPBOARD_SERVICE;

public class InfoRecyclerViewAdapter extends RecyclerView.Adapter {

    private static final int INFO_VIEW_TYPE = 0;
    private static final int COLOR_VIEW_TYPE = 1;
    private static final int LOCATION_VIEW_TYPE = 2;

    public interface OnDataRetrievedCallback {
        void onDataRetrieved();

        Context getContext();
    }

    private static class InfoItem {
        private String type, value;

        InfoItem(String type, String value) {
            this.type = type;
            this.value = value;
        }

        String getType() {
            return type;
        }

        String getValue() {
            return value;
        }
    }

    private static class ColorsItem extends InfoItem {

        private String path;

        ColorsItem(String path) {
            super("Colors", null);
            this.path = path;
        }
    }

    private static class LocationItem extends InfoItem {

        LocationItem(String type, String value) {
            super(type, value);
        }
    }

    private ExifInterface exif;

    private ArrayList<InfoItem> infoItems;

    public boolean exifSupported(Context context, AlbumItem albumItem) {
        Uri uri = albumItem.getUri(context);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                InputStream is = context.getContentResolver().openInputStream(uri);
                if (is != null) {
                    exif = new ExifInterface(is);
                }

            } else {
                exif = new ExifInterface(albumItem.getPath());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return exif != null;
    }

    public void retrieveData(final AlbumItem albumItem, final boolean showColors, final OnDataRetrievedCallback callback) {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {

                infoItems = new ArrayList<>();
                if (showColors) {
                    infoItems.add(new ColorsItem(albumItem.getPath()));
                }

                Context context = callback.getContext();

                File file = new File(albumItem.getPath());
                Uri uri = albumItem.getUri(context);

                String name = albumItem.getName();
                infoItems.add(new InfoItem(context.getString(R.string.info_filename), name));

                String path = file.getPath();
                infoItems.add(new InfoItem(context.getString(R.string.info_filepath), path));

                String size;
                //retrieve fileSize form MediaStore
                Cursor cursor = context.getContentResolver().query(
                        uri, null, null,
                        null, null);
                if (cursor != null) {
                    int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                    cursor.moveToFirst();
                    size = parseFileSize(cursor.getLong(sizeIndex));
                    cursor.close();
                } else {
                    size = parseFileSize(0);
                }
                infoItems.add(new InfoItem(context.getString(R.string.info_size), size));

                if (exif == null) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            InputStream is = context.getContentResolver().openInputStream(uri);
                            if (is != null) {
                                exif = new ExifInterface(is);
                            }

                        } else {
                            exif = new ExifInterface(albumItem.getPath());
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                if (exif != null) {
                    /*Dimensions*/
                    String height = String.valueOf(ExifUtil.getCastValue(exif, ExifInterface.TAG_IMAGE_LENGTH));
                    String width = String.valueOf(ExifUtil.getCastValue(exif, ExifInterface.TAG_IMAGE_WIDTH));
                    infoItems.add(new InfoItem(context.getString(R.string.info_dimensions), width + " x " + height));

                    /*Date*/
                    Object date = ExifUtil.getCastValue(exif, ExifInterface.TAG_DATETIME);
                    infoItems.add(new InfoItem(context.getString(R.string.info_date), date != null ? String.valueOf(date) : ExifUtil.NO_DATA));

                    /*Location*/
                    Object latitude = ExifUtil.getCastValue(exif, ExifInterface.TAG_GPS_LATITUDE);
                    Object longitude = ExifUtil.getCastValue(exif, ExifInterface.TAG_GPS_LONGITUDE);
                    String location;
                    if (latitude != null && longitude != null) {
                        boolean positiveLat = ExifUtil.getCastValue(exif, ExifInterface.TAG_GPS_LATITUDE_REF).equals("N");
                        latitude = parseGPSLongOrLat(String.valueOf(latitude), positiveLat);

                        boolean positiveLong = ExifUtil.getCastValue(exif, ExifInterface.TAG_GPS_LONGITUDE_REF).equals("E");
                        longitude = parseGPSLongOrLat(String.valueOf(longitude), positiveLong);
                        location = latitude + "," + longitude;
                    } else {
                        location = ExifUtil.NO_DATA;
                    }
                    infoItems.add(new LocationItem(context.getString(R.string.info_location), location));

                    /*Focal Length*/
                    Object focalLengthObject = ExifUtil.getCastValue(exif, ExifInterface.TAG_FOCAL_LENGTH);
                    String focalLength;
                    if (focalLengthObject != null) {
                        focalLength = String.valueOf(focalLengthObject);
                    } else {
                        focalLength = ExifUtil.NO_DATA;
                    }
                    infoItems.add(new InfoItem(context.getString(R.string.info_focal_length), focalLength));

                    /*Exposure*/
                    Object exposureObject = String.valueOf(ExifUtil.getCastValue(exif, ExifInterface.TAG_EXPOSURE_TIME));
                    String exposure;
                    if (exposureObject != null) {
                        exposure = parseExposureTime(String.valueOf(exposureObject));
                    } else {
                        exposure = ExifUtil.NO_DATA;
                    }
                    infoItems.add(new InfoItem(context.getString(R.string.info_exposure), exposure));

                    /*Model & Make*/
                    Object makeObject = ExifUtil.getCastValue(exif, ExifInterface.TAG_MAKE);
                    Object modelObject = ExifUtil.getCastValue(exif, ExifInterface.TAG_MODEL);
                    String model;
                    if (makeObject != null && modelObject != null) {
                        model = String.valueOf(makeObject) + " " + String.valueOf(modelObject);
                    } else {
                        model = ExifUtil.NO_DATA;
                    }
                    infoItems.add(new InfoItem(context.getString(R.string.info_camera_model), model));

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        /*Aperture*/
                        Object apertureObject = ExifUtil.getCastValue(exif, ExifInterface.TAG_F_NUMBER);
                        String aperture;
                        if (apertureObject != null) {
                            aperture = "f/" + String.valueOf(apertureObject);
                        } else {
                            aperture = ExifUtil.NO_DATA;
                        }
                        infoItems.add(new InfoItem(context.getString(R.string.info_aperture), aperture));

                        /*ISO*/
                        Object isoObject = ExifUtil.getCastValue(exif, ExifInterface.TAG_ISO_SPEED_RATINGS);
                        String iso;
                        if (apertureObject != null) {
                            iso = String.valueOf(isoObject);
                        } else {
                            iso = ExifUtil.NO_DATA;
                        }
                        infoItems.add(new InfoItem(context.getString(R.string.info_iso), iso));
                    }
                } else {
                    int[] imageDimens = albumItem.getImageDimens(context);
                    String height = String.valueOf(imageDimens[1]);
                    String width = String.valueOf(imageDimens[0]);
                    infoItems.add(new InfoItem(context.getString(R.string.info_dimensions), width + " x " + height));

                    Locale locale;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        locale = context.getResources().getConfiguration().getLocales().get(0);
                    } else {
                        locale = context.getResources().getConfiguration().locale;
                    }
                    String date = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", locale).format(new Date(albumItem.getDate()));
                    infoItems.add(new InfoItem(context.getString(R.string.info_date), date));
                }

                callback.onDataRetrieved();
            }
        });
    }

    @Override
    public int getItemViewType(int position) {
        InfoItem infoItem = infoItems.get(position);
        if (infoItem instanceof ColorsItem) {
            return COLOR_VIEW_TYPE;
        } else if (infoItem instanceof LocationItem) {
            return LOCATION_VIEW_TYPE;
        }
        return INFO_VIEW_TYPE;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        int layoutRes = viewType == COLOR_VIEW_TYPE ? R.layout.info_color : R.layout.info_item;
        View v = LayoutInflater.from(parent.getContext()).inflate(layoutRes, parent, false);
        switch (viewType) {
            case INFO_VIEW_TYPE:
                return new InfoHolder(v);
            case COLOR_VIEW_TYPE:
                return new ColorHolder(v);
            case LOCATION_VIEW_TYPE:
                return new LocationHolder(v);
        }
        return null;
    }

    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder holder, int position) {
        InfoItem infoItem = infoItems.get(position);
        if (holder instanceof ColorHolder && infoItem instanceof ColorsItem) {
            ((ColorHolder) holder).setColors((ColorsItem) infoItem);
        } else if (holder instanceof InfoHolder) {
            ((InfoHolder) holder).bind(infoItem);
        }
    }

    @Override
    public int getItemCount() {
        return infoItems.size();
    }

    private String parseFileSize(long fileLength) {
        long file_bytes = fileLength / 1000 * 1000;
        float size = file_bytes;
        int i = 0;
        while (size > 1000) {
            size = size / 1000;
            i++;
        }
        switch (i) {
            case 1:
                return size + " KB";
            case 2:
                return size + " MB";
            case 3:
                return size + " GB";
        }
        return file_bytes + " Bytes";
    }

    private String parseExposureTime(String input) {
        if (input == null || input.equals("null")) {
            return ExifUtil.NO_DATA;
        }
        float f = Float.valueOf(input);
        try {
            int i = Math.round(1 / f);
            return String.valueOf(1 + "/" + i) + " sec";
        } catch (NumberFormatException e) {
            return input;
        }
    }

    private String parseGPSLongOrLat(String input, boolean positive) {
        if (input == null || input.equals("null")) {
            return ExifUtil.NO_DATA;
        }

        float value = 0;
        String[] parts = input.split(",");
        for (int i = 0; i < parts.length; i++) {
            Rational r = Rational.parseRational(parts[i]);
            int factor = 1;
            for (int k = 0; k < i; k++) {
                factor *= 60;
            }
            r.setDenominator(r.getDenominator() * factor);
            value += r.floatValue();
        }
        if (!positive) {
            value *= -1.0f;
        }
        return String.valueOf(value);
    }


    /*ViewHolder classes*/
    static class InfoHolder extends RecyclerView.ViewHolder {

        TextView type, value;

        InfoHolder(View itemView) {
            super(itemView);
            type = itemView.findViewById(R.id.tag);
            value = itemView.findViewById(R.id.value);
        }

        void bind(InfoItem infoItem) {
            type.setText(infoItem.getType());
            value.setText(infoItem.getValue());
        }
    }

    static class LocationHolder extends InfoHolder {

        private double[] location;
        private String featureName;

        LocationHolder(View itemView) {
            super(itemView);
        }

        @Override
        void bind(InfoItem infoItem) {
            type.setText(infoItem.getType());
            String[] parts = infoItem.getValue().split(",");
            try {
                location = new double[]{
                        Double.parseDouble(parts[0]),
                        Double.parseDouble(parts[1])};
                String address = getAddress(type.getContext(), location[0], location[1]);
                value.setText(address);
                value.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        launchLocation();
                    }
                });
            } catch (NumberFormatException e) {
                value.setText(infoItem.getValue());
            }
        }

        private void launchLocation() {
            String locationValue = String.valueOf(location[0] + "," + location[1]);
            Uri gmUri = Uri.parse("geo:0,0?q=" + locationValue + "(" + featureName + ")");
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setData(gmUri)
                    .setPackage("com.google.android.apps.maps");

            Context context = itemView.getContext();
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
            }
        }

        String getAddress(Context context, double lat, double lng) {
            Geocoder geocoder = new Geocoder(context, Locale.getDefault());
            try {
                List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
                Address obj = addresses.get(0);
                featureName = obj.getFeatureName();
                return obj.getFeatureName() + ", "
                        + obj.getLocality() + ", "
                        + obj.getAdminArea();
            } catch (IOException e) {
                e.printStackTrace();
                return context.getString(R.string.error);
            }
        }
    }

    static class ColorHolder extends RecyclerView.ViewHolder {

        private Palette p;
        private Uri uri;

        private View.OnClickListener onClickListener
                = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String color = (String) view.getTag();
                if (color != null) {
                    ClipboardManager clipboard = (ClipboardManager) view.getContext()
                            .getSystemService(CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("label", color);
                    clipboard.setPrimaryClip(clip);

                    Toast.makeText(view.getContext(),
                            R.string.copied_to_clipboard,
                            Toast.LENGTH_SHORT).show();
                }
            }
        };

        ColorHolder(View itemView) {
            super(itemView);
        }

        private void retrieveColors(final Uri uri) {
            if (uri == null) {
                return;
            }
            Glide.with(itemView.getContext())
                    .asBitmap()
                    .load(uri)
                    .into(new SimpleTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(Bitmap bitmap, com.bumptech.glide.request
                                .transition.Transition<? super Bitmap> transition) {
                            // Do something with bitmap here.
                            Palette.from(bitmap).generate(new Palette.PaletteAsyncListener() {
                                @Override
                                public void onGenerated(Palette palette) {
                                    p = palette;
                                    setColors(null);
                                }
                            });
                        }
                    });
        }

        private void setColors(ColorsItem colorsItem) {
            if (p == null) {
                AlbumItem albumItem = AlbumItem.getInstance(colorsItem.path);

                if (albumItem instanceof Photo || albumItem instanceof Gif) {
                    uri = albumItem.getUri(itemView.getContext());
                    retrieveColors(uri);
                } else {
                    itemView.setVisibility(View.GONE);
                }
                return;
            }

            int defaultColor = Color.argb(0, 0, 0, 0);

                /*Vibrant color*/
            setColor((CardView) itemView.findViewById(R.id.vibrant_card),
                    (TextView) itemView.findViewById(R.id.vibrant_text),
                    p.getVibrantColor(defaultColor));

                /*Vibrant Dark color*/
            setColor((CardView) itemView.findViewById(R.id.vibrant_dark_card),
                    (TextView) itemView.findViewById(R.id.vibrant_dark_text),
                    p.getDarkVibrantColor(defaultColor));

                /*Vibrant Light color*/
            setColor((CardView) itemView.findViewById(R.id.vibrant_light_card),
                    (TextView) itemView.findViewById(R.id.vibrant_light_text),
                    p.getLightVibrantColor(defaultColor));

                /*Muted color*/
            setColor((CardView) itemView.findViewById(R.id.muted_card),
                    (TextView) itemView.findViewById(R.id.muted_text),
                    p.getMutedColor(defaultColor));

                /*Muted Dark color*/
            setColor((CardView) itemView.findViewById(R.id.muted_dark_card),
                    (TextView) itemView.findViewById(R.id.muted_dark_text),
                    p.getDarkMutedColor(defaultColor));

                /*Muted Light color*/
            setColor((CardView) itemView.findViewById(R.id.muted_light_card),
                    (TextView) itemView.findViewById(R.id.muted_light_text),
                    p.getLightMutedColor(defaultColor));
        }

        private void setColor(CardView card, TextView text, int color) {
            if (Color.alpha(color) == 0) {
                //color not found
                int transparent = ContextCompat.getColor(card.getContext(),
                        android.R.color.transparent);
                card.setCardBackgroundColor(transparent);
                text.setText("N/A");
                return;
            }

            card.setCardBackgroundColor(color);
            text.setTextColor(getTextColor(text.getContext(), color));
            String colorHex = String.format("#%06X", (0xFFFFFF & color));
            text.setText(colorHex);

            card.setTag(colorHex);
            card.setOnClickListener(onClickListener);
        }

        private static int getTextColor(Context context, int backgroundColor) {
            if ((Color.red(backgroundColor) +
                    Color.green(backgroundColor) +
                    Color.blue(backgroundColor)) / 3 < 100) {
                return ContextCompat.getColor(context, R.color.white_translucent1);
            }
            return ContextCompat.getColor(context, R.color.grey_900_translucent);
        }
    }
}