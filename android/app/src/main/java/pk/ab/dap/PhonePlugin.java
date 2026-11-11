package pk.ab.dap;

import android.Manifest;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Phone;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads names and numbers from the phone's contacts (read only, kept in memory by the page) and places calls.
 * Calls go straight out when the phone allows it; otherwise the dialer opens with the number filled in.
 */
@CapacitorPlugin(
    name = "Phone",
    permissions = {
        @Permission(strings = { Manifest.permission.READ_CONTACTS }, alias = "contacts"),
        @Permission(strings = { Manifest.permission.CALL_PHONE }, alias = "call")
    }
)
public class PhonePlugin extends Plugin {

    @PluginMethod
    public void contacts(PluginCall call) {
        if (getPermissionState("contacts") != PermissionState.GRANTED) {
            requestPermissionForAlias("contacts", call, "afterContactsPermission");
            return;
        }
        readContacts(call);
    }

    @PermissionCallback
    private void afterContactsPermission(PluginCall call) {
        if (getPermissionState("contacts") == PermissionState.GRANTED) readContacts(call);
        else call.reject("Permission to read contacts was not given", "DENIED");
    }

    private void readContacts(final PluginCall call) {
        new Thread(() -> {
            Map<String, JSObject> people = new LinkedHashMap<>();
            Map<String, JSArray> numbers = new LinkedHashMap<>();
            String[] columns = { Phone.CONTACT_ID, Phone.DISPLAY_NAME, Phone.NUMBER, Phone.TYPE, Phone.LABEL, Phone.IS_SUPER_PRIMARY };
            Cursor c = null;
            try {
                c = getContext().getContentResolver().query(Phone.CONTENT_URI, columns, null, null, Phone.DISPLAY_NAME + " ASC");
                while (c != null && c.moveToNext()) {
                    String id = c.getString(0), name = c.getString(1), number = c.getString(2);
                    if (number == null || number.trim().isEmpty()) continue;
                    if (!people.containsKey(id)) {
                        JSObject p = new JSObject();
                        p.put("id", id);
                        p.put("name", name == null ? "" : name);
                        people.put(id, p);
                        numbers.put(id, new JSArray());
                    }
                    JSObject n = new JSObject();
                    n.put("number", number);
                    CharSequence label = Phone.getTypeLabel(getContext().getResources(), c.getInt(3), c.getString(4));
                    n.put("type", label == null ? "" : label.toString());
                    n.put("primary", c.getInt(5) != 0);
                    numbers.get(id).put(n);
                }
            } catch (Exception e) {
                call.reject("Contacts could not be read: " + e.getMessage(), "FAILED");
                return;
            } finally {
                if (c != null) c.close();
            }
            JSArray list = new JSArray();
            for (Map.Entry<String, JSObject> e : people.entrySet()) {
                JSObject p = e.getValue();
                p.put("numbers", numbers.get(e.getKey()));
                list.put(p);
            }
            JSObject out = new JSObject();
            out.put("contacts", list);
            call.resolve(out);
        }).start();
    }

    @PluginMethod
    public void call(PluginCall call) {
        String number = call.getString("number");
        if (number == null || number.trim().isEmpty()) { call.reject("No number to call", "NO_NUMBER"); return; }
        if (getPermissionState("call") != PermissionState.GRANTED) {
            requestPermissionForAlias("call", call, "afterCallPermission");
            return;
        }
        dial(call, true);
    }

    @PermissionCallback
    private void afterCallPermission(PluginCall call) {
        dial(call, getPermissionState("call") == PermissionState.GRANTED);      // without permission the dialer opens instead
    }

    private void dial(PluginCall call, boolean direct) {
        String number = call.getString("number", "").trim();
        Intent intent = new Intent(direct ? Intent.ACTION_CALL : Intent.ACTION_DIAL, Uri.fromParts("tel", number, null));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            getActivity().startActivity(intent);
            JSObject out = new JSObject();
            out.put("direct", direct);
            call.resolve(out);
        } catch (Exception e) {
            call.reject("The call could not be started: " + e.getMessage(), "FAILED");
        }
    }
}
