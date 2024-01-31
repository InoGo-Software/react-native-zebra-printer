
package nl.inogo.ZebraPrinter;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.zebra.sdk.comm.BluetoothConnection;
import com.zebra.sdk.comm.ConnectionException;
import com.zebra.sdk.printer.PrinterLanguage;
import com.zebra.sdk.printer.ZebraPrinter;
import com.zebra.sdk.printer.ZebraPrinterFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class RNReactNativeZebraPrinterModule extends ReactContextBaseJavaModule {

    static final String TAG = "ZebraPrinter";

    private final ReactApplicationContext reactContext;
    private BluetoothAdapter bluetoothAdapter;

    private BluetoothConnection connection;
    private ZebraPrinter printer;
    private String template;

    public RNReactNativeZebraPrinterModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    @Override
    public String getName() {
        return "RNReactNativeZebraPrinter";
    }

    /**
     * Check if the device is connected to the printer.
     */
    @ReactMethod
    public boolean isConnected() {
        if (this.connection == null) {
            return false;
        }
        return this.connection.isConnected();
    }

    /**
     * List paired bluetooth devices.
     * This can be used to find the printer. External libraries can also be used as long as the
     * mac address can be found.
     */
    @ReactMethod
    public void getBondedDevices(Promise promise) {
        WritableArray deviceList = Arguments.createArray();
        if (bluetoothAdapter != null) {
            Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();

            for (BluetoothDevice rawDevice : bondedDevices) {
                WritableMap device = deviceToWritableMap(rawDevice);
                deviceList.pushMap(device);
            }
        }
        promise.resolve(deviceList);
    }


    @ReactMethod
    public void initConnection(String macAddress) {

        if (this.connection == null) {
            Log.d(TAG, "Init connection");
            this.connection = new BluetoothConnection(macAddress);
            this.openConnection();
            return;
        }

        if (!this.isConnected()) {
            this.openConnection();
            Log.d(TAG, "Connection reopened");
        }

        Log.d(TAG, "Connection was already initiated");
    }

    private void openConnection() {
        if (!this.isConnected()) {
            try {
                this.connection.open();
                Log.d(TAG, "Connection successfully opened");
                this.setTemplate();
                this.printer = ZebraPrinterFactory.getInstance(PrinterLanguage.ZPL, this.connection);
            } catch (ConnectionException e) {
                Log.d(TAG, "Couldn't init the connection");
                Log.e(TAG, e.getMessage());
            }
        }
    }

    @ReactMethod
    public void closeConnection(final Promise promise) {

        if (this.isConnected()) {
            Log.d(TAG, "Closing connection");
            try {
                this.connection.close();
                Log.d(TAG, "Successfully closed connection");
                this.connection = null;
                promise.resolve(true);
                return;
            } catch (ConnectionException e) {
                Log.e(TAG, e.getMessage());
                this.connection = null;
                promise.reject("Not disconnected");
                return;
            }
        } else {
            Log.d(TAG, "Connection was never open");
        }

        this.connection = null;
        promise.resolve(true);
    }

    private void closeConnectionLocal() {
        if (this.isConnected()) {
            Log.d(TAG, "Closing connection");
            try {
                this.connection.close();
                Log.d(TAG, "Successfully closed connection");
            } catch (ConnectionException e) {
                Log.e(TAG, e.getMessage());
            }
        } else {
            Log.d(TAG, "Connection was never open");
        }

        this.connection = null;
    }

    /**
     * Print a label with the printer.
     *
     * @param macAddress      the mac address of the printer to print to.
     * @param tripName        the name of the trip.
     * @param depotName       the name of the depot where the package is picked up.
     * @param delivery        the name of the place to deliver the package to.
     * @param date            the date the label is printed on.
     * @param time            the time the label is printed.
     * @param packageProgress indicator for the amount of pacakges that are scanned. This is basically
     *                        a string in which you can add whatever but it is intended as a counter.
     *                        For example, if there are 10 packages and 2 are already printed, the next
     *                        label would show '3/10'.
     * @param zipCode         the zipcode of the delivery location.
     * @param promise         callback for when the printing is finished.
     */
    @ReactMethod
    public void print(
            String macAddress,
            String tenantName,
            String tripName,
            String depotName,
            String delivery,
            String date,
            String time,
            String packageProgress,
            String zipCode,
            String stop,
            final Promise promise) {


        HashMap<String, String> data = new HashMap<>();
        data.put("varTenantName", tenantName != null ? tenantName : "");
        data.put("varTripName", tripName != null ? tripName : "");
        data.put("varDepotName", depotName != null ? depotName : "");
        data.put("varDeliveryDate", delivery != null ? delivery : "");
        data.put("varScannedTime", date != null && time != null ? date + " " + time : "");
        data.put("varPackage", packageProgress != null ? packageProgress : "");
        data.put("varZipcode", zipCode != null ? zipCode : "");
        data.put("varStop", stop != null ? stop : "");

        Log.d(TAG, this.getLabel(data));

        boolean wasOpen = this.isConnected();

        Log.d(TAG, "wasOpen=" + wasOpen + " macAddress=" + macAddress);


        if (wasOpen) {
            // If the connection was open but the macAdress is different.
            if (this.connection != null && this.connection.getMACAddress() != macAddress) {
                Log.d(TAG, "Closing previous connection and opening new one since the macAddress is different");

                // Close the previous connection.
                try {
                    this.connection.close();
                } catch (ConnectionException e) {
                    Log.e(TAG, "Something went wrong while closing the connection");
                    Log.e(TAG, e.getMessage());
                }

                // Open the new connection with the correct macAddress.
                this.initConnection(macAddress);
            }

            Log.d(TAG, "Was already connected");
        } else {
            Log.d(TAG, "No connection was found");
            this.initConnection(macAddress);
        }

        this.sendFile(this.printer, data);

        // Close connection if it wasn't open before
        if (!wasOpen) {
            try {
                this.connection.close();
            } catch (ConnectionException e) {
                Log.e(TAG, "Something went wrong while closing the connection");
                Log.e(TAG, e.getMessage());
            }

            this.connection = null;
        }

        promise.resolve(true);
    }

    private void sendFile(final ZebraPrinter printer, HashMap<String, String> data) {
        Log.d(TAG, "Within sendFile method");

        final File file = new File(this.reactContext.getCacheDir(), "LABEL.ZPL");

        try {
            generateFile(printer, file.getAbsolutePath(), data);
        } catch (IOException e) {
            e.printStackTrace();
            Log.d(TAG, "Something went wrong while writing the file to the filesystem");
            Log.e(TAG, e.getMessage());
        }

        ExecutorService executor = Executors.newCachedThreadPool();
        Callable<String> task = new Callable<String>() {
            public String call() {
                try {
                    Log.d(TAG, "Start sending file contents");
                    printer.sendFileContents(file.getAbsolutePath());
                    Log.d(TAG, "Done sending file contents");
                } catch (ConnectionException e) {
                    e.printStackTrace();
                    Log.e(TAG, e.getMessage());
                    Log.d(TAG, "Something is wrong with the connection while sending the file to the printer");
                }
                return "";
            }
        };
        Future<String> future = executor.submit(task);
        try {
            String result = future.get(1, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            // handle the timeout
            Log.d(TAG, "Timeout here");
            this.closeConnectionLocal();
        } catch (InterruptedException e) {
            // handle the interrupts
            Log.d(TAG, "Interrupted here");
        } catch (ExecutionException e) {
            // handle other exceptions
            Log.d(TAG, "Other exception here");
        } finally {
            Log.d(TAG, "Future canceled here");
            future.cancel(true); // may or may not desire this
        }
    }

    private void generateFile(ZebraPrinter printer, String fileName, HashMap<String, String> data) throws IOException {
        Log.d(TAG, "Within generateFile method");

        File file = new File(fileName);

        if (file.exists()) {
            Log.d(TAG, "File did already exist");
            file.delete();
        }

        FileOutputStream os = new FileOutputStream(fileName);

        byte[] configLabel = getLabel(data).getBytes();


        os.flush();
        os.write(configLabel);
        os.flush();
        os.close();
    }

    private void setTemplate() {
        String fileAsString = null;
        try {
            InputStream input = this.reactContext.getAssets().open("Omega-300.zpl");
            fileAsString = readFullyAsString(input, "UTF8");
            Log.d(TAG, fileAsString);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
        }

        this.template = fileAsString;
    }

    private String getLabel(HashMap<String, String> replaceData) {
        if (this.template == null) {
            this.setTemplate();
        }

        // If template is still not set
        if (this.template == null) {
            Log.e(TAG, "Template couldn't be loaded");
            return "";
        }

        String labelData = this.template;

        for (Map.Entry<String, String> item : replaceData.entrySet()) {
            String key = item.getKey();
            String value = item.getValue();

            labelData = labelData.replace(key, value);
        }

        return labelData;
    }

    private String readFullyAsString(InputStream inputStream, String encoding)
            throws IOException {
        return readFully(inputStream).toString(encoding);
    }

    private byte[] readFullyAsBytes(InputStream inputStream)
            throws IOException {
        return readFully(inputStream).toByteArray();
    }

    private ByteArrayOutputStream readFully(InputStream inputStream)
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length = 0;
        while ((length = inputStream.read(buffer)) != -1) {
            baos.write(buffer, 0, length);
        }
        return baos;
    }

    /**
     * Convert BluetoothDevice into WritableMap
     *
     * @param device Bluetooth device
     */
    private WritableMap deviceToWritableMap(BluetoothDevice device) {
        WritableMap params = Arguments.createMap();

        params.putString("name", device.getName());
        params.putString("address", device.getAddress());
        params.putString("id", device.getAddress());

        if (device.getBluetoothClass() != null) {
            params.putInt("class", device.getBluetoothClass().getDeviceClass());
        }

        return params;
    }
}
