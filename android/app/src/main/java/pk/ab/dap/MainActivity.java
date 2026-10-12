package pk.ab.dap;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(DriveAuthPlugin.class);      // Google Drive permission for sync, asked for natively
        super.onCreate(savedInstanceState);
    }
}
