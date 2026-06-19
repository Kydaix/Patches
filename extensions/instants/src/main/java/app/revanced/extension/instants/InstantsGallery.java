package app.revanced.extension.instants;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Sélecteur de galerie pour les "Instants" (QuickSnap).
 *
 * Flux :
 *  - A03 (capture) appelle shouldIntercept(); si true -> startPick() lance le
 *    sélecteur photo système et la capture caméra est annulée.
 *  - ModalActivity.onActivityResult() transmet le résultat ici : on copie
 *    l'image choisie vers IMAGE_PATH puis on RE-déclenche A03 (injecting=true),
 *    ce qui fait repartir le pipeline avec la photo choisie (lue depuis le
 *    fichier par le patch).
 */
public final class InstantsGallery {
    private static final int REQ = 0x1A57;
    private static final String IMAGE_PATH =
            "/sdcard/Android/data/com.instagram.android/files/instant.jpg";

    private static volatile boolean injecting = false;
    private static Context pendingContext;
    private static Object pendingViewModel;

    /** Appelé tout au début de A03. true => intercepter la capture caméra. */
    public static boolean shouldIntercept() {
        return !injecting;
    }

    /** Lance le sélecteur photo. context = arg0 de A03, viewModel = arg2 de A03. */
    public static void startPick(Context context, Object viewModel) {
        try {
            pendingContext = context;
            pendingViewModel = viewModel;
            Activity activity = getForegroundActivity();
            if (activity == null) return;

            Intent intent = new Intent("android.provider.action.PICK_IMAGES"); // Photo Picker (API 33+)
            intent.setType("image/*");
            try {
                activity.startActivityForResult(intent, REQ);
            } catch (Exception noPhotoPicker) {
                Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
                fallback.setType("image/*");
                fallback.addCategory(Intent.CATEGORY_OPENABLE);
                activity.startActivityForResult(fallback, REQ);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Branché au début de ModalActivity.onActivityResult(int, int, Intent). */
    public static void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ || resultCode != Activity.RESULT_OK || data == null) return;
        final Uri uri = data.getData();
        final Context ctx = pendingContext;
        final Object vm = pendingViewModel;
        if (uri == null || ctx == null || vm == null) return;

        // Copier l'image choisie vers le fichier lu par le patch.
        try {
            ContentResolver cr = ctx.getContentResolver();
            InputStream in = cr.openInputStream(uri);
            if (in == null) return;
            File out = new File(IMAGE_PATH);
            File parent = out.getParentFile();
            if (parent != null) parent.mkdirs();
            FileOutputStream fos = new FileOutputStream(out);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
            fos.close();
            in.close();
        } catch (Throwable t) {
            return;
        }

        // Re-déclencher A03 sur le thread principal avec la photo choisie.
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                injecting = true;
                Method a03 = vm.getClass().getDeclaredMethod(
                        "A03", Context.class, Bitmap.class, vm.getClass());
                a03.setAccessible(true);
                a03.invoke(null, ctx, null, vm); // static : receiver null
            } catch (Throwable ignored) {
            } finally {
                injecting = false;
            }
        });
    }

    /** Activité au premier plan via ActivityThread (sans dépendances). */
    private static Activity getForegroundActivity() {
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Object at = atClass.getMethod("currentActivityThread").invoke(null);
            Field activitiesField = atClass.getDeclaredField("mActivities");
            activitiesField.setAccessible(true);
            Map<?, ?> activities = (Map<?, ?>) activitiesField.get(at);
            for (Object record : activities.values()) {
                Class<?> recordClass = record.getClass();
                Field pausedField = recordClass.getDeclaredField("paused");
                pausedField.setAccessible(true);
                if (!pausedField.getBoolean(record)) {
                    Field activityField = recordClass.getDeclaredField("activity");
                    activityField.setAccessible(true);
                    return (Activity) activityField.get(record);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
