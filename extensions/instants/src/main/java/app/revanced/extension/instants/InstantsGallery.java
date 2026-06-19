package app.revanced.extension.instants;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
 *  - A03 (capture) appelle requestPickForCapture(); si true -> le sélecteur
 *    photo système est lancé et la capture caméra est annulée.
 *  - A01/A02 appellent requestPickForUpload() comme filet de sécurité si un
 *    premier upload caméra démarre sans passer par A03.
 *  - ModalActivity.onActivityResult() transmet le résultat ici : on copie
 *    l'image choisie vers un fichier privé unique puis on RE-déclenche A03
 *    (injecting=true), ce qui fait repartir le pipeline avec la photo choisie.
 */
public final class InstantsGallery {
    private static final int REQ = 0x1A57;
    private static final String IMAGE_DIR =
            "/sdcard/Android/data/com.instagram.android/files/revanced_instants";
    private static final String IMAGE_PREFIX = "instant_";

    private static volatile boolean injecting = false;
    private static volatile boolean picking = false;
    private static volatile boolean allowUpload = false;
    private static volatile Context pendingContext;
    private static volatile Object pendingViewModel;
    private static volatile File selectedFile;
    private static int generation = 0;

    /** Compat ancien patch. */
    public static boolean shouldIntercept() {
        return !injecting;
    }

    /** Compat ancien patch. */
    public static void startPick(Context context, Object viewModel) {
        requestPickForCapture(context, viewModel);
    }

    /** Appelé au début de A03. true => le picker est lancé et la capture est annulée. */
    public static boolean requestPickForCapture(Context context, Object viewModel) {
        if (injecting) return false;
        clearSelection();
        return beginPick(context, viewModel);
    }

    /**
     * Filet de sécurité au début de A01/A02 : certains premiers uploads propres
     * peuvent partir sans repasser par A03. true => le picker est lancé et
     * l'upload caméra courant doit être annulé.
     */
    public static boolean requestPickForUpload(Context context, Object viewModel) {
        if (injecting || allowUpload) return false;
        clearSelection();
        return beginPick(context, viewModel);
    }

    /**
     * Bitmap de l'image choisie, ou null si absente.
     */
    public static Bitmap imageBitmap() {
        File f = imageFile();
        if (f == null) return null;
        try {
            return BitmapFactory.decodeFile(f.getAbsolutePath());
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * File de l'image choisie, ou null si absente.
     *
     * Le vrai upload de QuickSnap NE part PAS du Bitmap : A02 construit un
     * `Medium` (com.instagram.common.gallery.Medium) soit depuis un Bitmap,
     * soit depuis un File, puis l'envoie. Le runtime utilise le chemin File
     * -> on doit donc rediriger ce File vers notre image. Null-safe : si le
     * fichier manque, on ne touche pas le paramètre (upload d'origine préservé).
     */
    public static File imageFile() {
        if (!allowUpload) return null;
        File f = selectedFile;
        return f != null && f.exists() ? f : null;
    }

    private static synchronized void clearSelection() {
        allowUpload = false;
        selectedFile = null;
    }

    private static synchronized File nextImageFile() {
        generation++;
        File dir = new File(IMAGE_DIR);
        dir.mkdirs();
        return new File(dir, IMAGE_PREFIX + System.currentTimeMillis() + "_" + generation + ".jpg");
    }

    /** Lance le sélecteur photo. context = arg0, viewModel = arg2. */
    private static boolean beginPick(Context context, Object viewModel) {
        try {
            synchronized (InstantsGallery.class) {
                pendingContext = context;
                pendingViewModel = viewModel;
                if (picking) return true;
            }

            Activity activity = context instanceof Activity
                    ? (Activity) context
                    : getForegroundActivity();
            if (activity == null) {
                synchronized (InstantsGallery.class) {
                    pendingContext = null;
                    pendingViewModel = null;
                }
                return false;
            }

            Intent intent = new Intent("android.provider.action.PICK_IMAGES"); // Photo Picker (API 33+)
            intent.setType("image/*");
            try {
                picking = true;
                activity.startActivityForResult(intent, REQ);
            } catch (Exception noPhotoPicker) {
                Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
                fallback.setType("image/*");
                fallback.addCategory(Intent.CATEGORY_OPENABLE);
                activity.startActivityForResult(fallback, REQ);
            }
            return true;
        } catch (Throwable ignored) {
            synchronized (InstantsGallery.class) {
                picking = false;
                pendingContext = null;
                pendingViewModel = null;
            }
            return false;
        }
    }

    /** Branché au début de ModalActivity.onActivityResult(int, int, Intent). */
    public static void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ) return;
        final Context ctx;
        final Object vm;
        synchronized (InstantsGallery.class) {
            picking = false;
            ctx = pendingContext;
            vm = pendingViewModel;
            // Relâche les refs statiques tout de suite : évite de retenir l'Activity.
            pendingContext = null;
            pendingViewModel = null;
        }
        if (resultCode != Activity.RESULT_OK || data == null) {
            clearSelection();
            return;
        }
        final Uri uri = data.getData();
        if (uri == null || ctx == null || vm == null) return;

        // Copie + ré-injection HORS du thread UI : openInputStream + la boucle de
        // lecture peuvent bloquer (gros fichier, URI cloud/distant) -> ANR si
        // exécuté sur le main thread (onActivityResult y est appelé).
        new Thread(() -> {
            File copied = copyToImagePath(ctx, uri);
            if (copied == null) {
                clearSelection();
                return; // copie ratée -> on n'injecte rien
            }
            selectedFile = copied;
            allowUpload = true;
            cleanupOldImages(copied);
            new Handler(Looper.getMainLooper()).post(() -> reinjectA03(ctx, vm));
        }, "instants-copy").start();
    }

    /**
     * Copie l'URI choisie vers un fichier unique. Retourne le fichier si OK.
     *
     * Le nom unique est volontaire : certains chemins QuickSnap gardent des
     * Medium/File en cache par chemin, donc réutiliser toujours instant.jpg peut
     * produire un upload de la sélection précédente.
     */
    private static File copyToImagePath(Context ctx, Uri uri) {
        File dest = nextImageFile();
        File parent = dest.getParentFile();
        if (parent != null) parent.mkdirs();
        try (InputStream in = ctx.getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(dest)) { // truncate + overwrite
            if (in == null) return null;
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) fos.write(buf, 0, n); // != -1, pas > 0
            fos.flush();
            fos.getFD().sync(); // octets garantis sur disque avant la relecture
        } catch (Throwable t) {
            return null;
        }
        return dest;
    }

    private static void cleanupOldImages(File keep) {
        try {
            File dir = new File(IMAGE_DIR);
            File[] files = dir.listFiles();
            if (files == null) return;
            for (File file : files) {
                if (!file.equals(keep) && file.getName().startsWith(IMAGE_PREFIX)) {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** Re-déclenche A03 sur le thread principal avec la photo choisie. */
    private static void reinjectA03(Context ctx, Object vm) {
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
