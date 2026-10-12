package pk.ab.dap;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;

import java.util.Collections;

/**
 * Gets a Google access token for the DAP's own files in Drive (scope drive.file).
 * The first time, Google shows its account and consent screens; after that the token comes back without any screen.
 * No password is seen or kept by the app.
 */
@CapacitorPlugin(name = "DriveAuth")
public class DriveAuthPlugin extends Plugin {
    private static final String DEFAULT_SCOPE = "https://www.googleapis.com/auth/drive.file";
    private ActivityResultLauncher<IntentSenderRequest> consent;
    private PluginCall waiting;

    @Override
    public void load() {
        consent = getActivity().registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), this::onConsent);
    }

    @PluginMethod
    public void getToken(PluginCall call) {
        final boolean interactive = Boolean.TRUE.equals(call.getBoolean("interactive", false));
        final String scope = call.getString("scope", DEFAULT_SCOPE);
        AuthorizationRequest request = AuthorizationRequest.builder()
            .setRequestedScopes(Collections.singletonList(new Scope(scope)))
            .build();
        Identity.getAuthorizationClient(getActivity()).authorize(request)
            .addOnSuccessListener(result -> {
                if (result.hasResolution()) {
                    if (!interactive || result.getPendingIntent() == null) { call.reject("Google needs your consent first", "NEED_CONSENT"); return; }
                    if (waiting != null) waiting.reject("Replaced by a newer request", "BUSY");
                    waiting = call;
                    try {
                        consent.launch(new IntentSenderRequest.Builder(result.getPendingIntent().getIntentSender()).build());
                    } catch (Exception e) {
                        waiting = null;
                        call.reject("Could not open Google sign-in: " + e.getMessage(), "LAUNCH_FAILED");
                    }
                } else {
                    answer(call, result);
                }
            })
            .addOnFailureListener(e -> call.reject("Google authorization failed: " + e.getMessage(), "FAILED"));
    }

    private void onConsent(ActivityResult r) {
        PluginCall call = waiting;
        waiting = null;
        if (call == null) return;
        try {
            AuthorizationResult result = Identity.getAuthorizationClient(getActivity()).getAuthorizationResultFromIntent(r.getData());
            answer(call, result);
        } catch (ApiException e) {
            call.reject("Google sign-in was not completed", "CANCELLED");
        } catch (Exception e) {
            call.reject("Google sign-in was not completed: " + e.getMessage(), "CANCELLED");
        }
    }

    private void answer(PluginCall call, AuthorizationResult result) {
        String token = result.getAccessToken();
        if (token == null || token.isEmpty()) { call.reject("Google gave no access token", "NO_TOKEN"); return; }
        JSObject out = new JSObject();
        out.put("accessToken", token);
        call.resolve(out);
    }

    /** A token Drive refused: forget it, so the next request gets a fresh one. */
    @PluginMethod
    public void clearToken(PluginCall call) {
        final String token = call.getString("token");
        if (token == null) { call.resolve(); return; }
        new Thread(() -> {
            try { GoogleAuthUtil.clearToken(getContext(), token); } catch (Exception ignored) { }
            call.resolve();
        }).start();
    }

    /** Sync switched off on the phone. Access can be withdrawn fully in the Google account's security settings. */
    @PluginMethod
    public void signOut(PluginCall call) {
        call.resolve();
    }
}
