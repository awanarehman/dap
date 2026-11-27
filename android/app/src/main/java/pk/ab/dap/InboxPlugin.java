package pk.ab.dap;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Base64;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Files sent to the DAP from other apps (Share > DAP, or Open with > DAP): the attendance report PDF or an organogram file.
 * The page asks for the waiting file with take(); a file that arrives while the app is open also fires "received".
 */
@CapacitorPlugin(name = "Inbox")
public class InboxPlugin extends Plugin {
    private static final int MAX = 40 * 1024 * 1024;
    private Uri waiting = null;
    private String waitingType = null;

    @Override
    public void load() { hold(getActivity().getIntent()); }

    @Override
    protected void handleOnNewIntent(Intent intent) {
        super.handleOnNewIntent(intent);
        if (hold(intent)) notifyListeners("received", new JSObject(), true);
    }

    private boolean hold(Intent intent) {
        if (intent == null) return false;
        Uri uri = null;
        if (Intent.ACTION_SEND.equals(intent.getAction())) uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        else if (Intent.ACTION_VIEW.equals(intent.getAction())) uri = intent.getData();
        if (uri == null) return false;
        waiting = uri; waitingType = intent.getType();
        intent.setAction(Intent.ACTION_MAIN);           // taken once, not again when the app comes back
        return true;
    }

    @PluginMethod
    public void take(final PluginCall call) {
        final Uri uri = waiting; final String type = waitingType;
        waiting = null; waitingType = null;
        if (uri == null) { call.resolve(new JSObject()); return; }
        new Thread(() -> {
            String name = "shared file";
            try (Cursor c = getContext().getContentResolver().query(uri, new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null)) {
                if (c != null && c.moveToFirst() && c.getString(0) != null) name = c.getString(0);
            } catch (Exception e) { /* the name is only for messages */ }
            try (InputStream in = getContext().getContentResolver().openInputStream(uri)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[65536]; int n;
                while ((n = in.read(buf)) > 0) { out.write(buf, 0, n); if (out.size() > MAX) throw new Exception("the file is larger than 40 MB"); }
                JSObject r = new JSObject();
                r.put("name", name);
                r.put("type", type == null ? getContext().getContentResolver().getType(uri) : type);
                r.put("data", Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP));
                call.resolve(r);
            } catch (Exception e) {
                call.reject("The shared file could not be read: " + e.getMessage(), "FAILED");
            }
        }).start();
    }
}
