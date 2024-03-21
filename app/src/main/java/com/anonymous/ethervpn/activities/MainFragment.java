package com.anonymous.ethervpn.activities;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;

import androidx.fragment.app.Fragment;

import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;

import com.anonymous.ethervpn.R;
import com.anonymous.ethervpn.model.Server;
import com.anonymous.ethervpn.utilities.CheckInternetConnection;
import com.anonymous.ethervpn.utilities.SharedPreference;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import de.blinkt.openvpn.api.APIVpnProfile;
import de.blinkt.openvpn.api.IOpenVPNAPIService;
import de.blinkt.openvpn.api.IOpenVPNStatusCallback;
import de.blinkt.openvpn.core.VpnStatus;
import de.blinkt.openvpn.utils.SharedPreferencesManager;

public class MainFragment extends Fragment {

    private Server server;
    private CheckInternetConnection connection;

    boolean vpnStart = false;
    private SharedPreference preference;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);
        initializeAll();

        return view;
    }

    private static final int ICS_OPENVPN_PERMISSION = 7;

    private static final int NOTIFICATIONS_PERMISSION_REQUEST_CODE = 11;

    protected IOpenVPNAPIService mService = null;

    private Boolean auth_failed = false;

    /**
     * Initialize all variable and object
     */
    private void initializeAll() {
        preference = new SharedPreference(getContext());
        server = preference.getServer();
        String username = "7E3PM";
        String password = "$2y$10$z8bIs.T6vm9PiYpkHeLIXucj6Q1xjlNG4e.uwp1ykUchEVYeLaC7i";
        SharedPreferencesManager.getInstance(requireContext()).saveCredentials(username, password);


        connection = new CheckInternetConnection();

    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Switch switchVPN = view.findViewById(R.id.switchVPN);
        switchVPN.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if (isChecked) {
                    try {
                        String username = SharedPreferencesManager.getInstance(requireContext()).getUsername();
                        String password = SharedPreferencesManager.getInstance(requireContext()).getPassword();

                        Log.d("VPN PRUEBAS Main", "askForPW:  username: " +username);
                        Log.d("VPN PRUEBAS Main", "askForPW:  password: " +password);
                        prepareVpn();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                } else {
                    if (vpnStart) {
                        confirmDisconnect();
                    }
                }
            }
        });

        VpnStatus.initLogCache(getActivity().getCacheDir());
    }


    /**
     * Show show disconnect confirm dialog
     */
    public void confirmDisconnect() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(getActivity().getString(R.string.connection_close_confirm));

        builder.setPositiveButton(getActivity().getString(R.string.yes), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                stopVpn();
            }
        });
        builder.setNegativeButton(getActivity().getString(R.string.no), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User cancelled the dialog
            }
        });

        // Create the AlertDialog
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    /**
     * Stop vpn
     *
     * @return boolean: VPN status
     */
    public boolean stopVpn() {
        try {
            mService.disconnect();
            vpnStart = false;
            return true;
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Prepare for vpn connect with required permission
     */
    private void prepareVpn() throws RemoteException {
        if (!vpnStart) {
            if (getInternetStatus()) {

                // Checking permission for network monitor
                Intent intent = mService.prepareVPNService();

                if (intent != null) {
                    startActivityForResult(intent, 1);
                } else {
                    startVpn();//have already permission
                }

            } else {
                System.out.println("you have no internet connection !!");
            }

        } else if (stopVpn()) {
            System.out.println("Disconnect Successfully");
        }
    }


    /**
     * Taking permission for network access
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == ICS_OPENVPN_PERMISSION) {
            try {
                mService.registerStatusCallback(mCallback);
            } catch (RemoteException e) {
                e.printStackTrace();
            }

        }
    }

    private IOpenVPNStatusCallback mCallback = new IOpenVPNStatusCallback.Stub() {
        /**
         * This is called by the remote service regularly to tell us about
         * new values.  Note that IPC calls are dispatched through a thread
         * pool running in each process, so the code executing here will
         * NOT be running in our main thread like most other things -- so,
         * to update the UI, we need to use a Handler to hop over there.
         */

        @Override
        public void newStatus(String uuid, String state, String message, String level) throws RemoteException {

            if (state.equals("AUTH_FAILED") || state.equals("CONNECTRETRY")) {
                auth_failed = true;
            }
            if (!auth_failed) {
                try {
                    setStatus(state);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (auth_failed) {
                setStatus("CONNECTRETRY");
            }
            if (state.equals("CONNECTED")) {
                auth_failed = false;
                if (ActivityCompat.checkSelfPermission(getContext(),
                        android.Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(getActivity(),
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATIONS_PERMISSION_REQUEST_CODE);
                }
            }

        }

    };

    /**
     * Internet connection status.
     */
    public boolean getInternetStatus() {
        return connection.netCheck(getContext());
    }

    /**
     * Get service status
     */

    /**
     * Start the VPN
     */
    private void startVpn() {
        try {
            // .ovpn file
            InputStream conf = getActivity().getAssets().open(server.getOvpn());
            InputStreamReader isr = new InputStreamReader(conf);
            BufferedReader br = new BufferedReader(isr);
            String config = "";
            String line;

            while (true) {
                line = br.readLine();
                if (line == null) break;
                config += line + "\n";
            }

            br.readLine();
            APIVpnProfile profile = mService.addNewVPNProfile(server.getCountry(),
                    false, config.toString());
            mService.startProfile(profile.mUUID);
            mService.startVPN(config.toString());

            auth_failed = false;


        } catch (IOException | RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     * Status change with corresponding vpn connection status
     *
     * @param connectionState
     */
    public void setStatus(String connectionState) {
        if (connectionState != null)
            switch (connectionState) {
                case "NOPROCESS":
                    vpnStart = false;
                    break;
                case "CONNECTED":
                    vpnStart = true;// it will use after restart this activity
                    break;
                case "WAIT":
                    break;
                case "AUTH":
                    break;
                case "CONNECTRETRY":
                    try {
                        mService.disconnect();
                    } catch (RemoteException ex) {
                        ex.printStackTrace();
                    }

                    break;
                case "AUTH_FAILED":
                    break;
                case "EXITING":
                    break;
                default:
                    vpnStart = false;
                    break;
            }

    }


    /**
     * Class for interacting with the main interface of the service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  We are communicating with our
            // service through an IDL interface, so get a client-side
            // representation of that from the raw service object.

            mService = IOpenVPNAPIService.Stub.asInterface(service);

            try {
                // Request permission to use the API
                Intent i = mService.prepare(getActivity().getPackageName());
                if (i != null) {
                    startActivityForResult(i, ICS_OPENVPN_PERMISSION);
                } else {
                    onActivityResult(ICS_OPENVPN_PERMISSION, Activity.RESULT_OK, null);
                }

            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            mService = null;

        }
    };

    private void bindService() {

        Intent icsopenvpnService = new Intent(IOpenVPNAPIService.class.getName());
        icsopenvpnService.setPackage("com.anonymous.ethervpn");

        getActivity().bindService(icsopenvpnService, mConnection, Context.BIND_AUTO_CREATE);

    }

    private void unbindService() {
        getActivity().unbindService(mConnection);
    }

    @Override
    public void onStart() {
        super.onStart();
        bindService();
    }

    @Override
    public void onResume() {
        if (server == null) {
            server = preference.getServer();
        }
        super.onResume();
        bindService();
    }

    @Override
    public void onPause() {
        if (mService != null) {
            unbindService();
        }
        super.onPause();
    }

    /**
     * Save current selected server on local shared preference
     */
    @Override
    public void onStop() {
        if (server != null) {
            preference.saveServer(server);
        }
        super.onStop();
    }

}
