
# react-native-zebra-printer

## Getting started

`$ npm install react-native-zebra-printer --save`

### Mostly automatic installation

`$ react-native link react-native-zebra-printer`

### Manual installation


#### iOS

1. In XCode, in the project navigator, right click `Libraries` ➜ `Add Files to [your project's name]`
2. Go to `node_modules` ➜ `react-native-zebra-printer` and add `RNReactNativeZebraPrinter.xcodeproj`
3. In XCode, in the project navigator, select your project. Add `libRNReactNativeZebraPrinter.a` to your project's `Build Phases` ➜ `Link Binary With Libraries`
4. Run your project (`Cmd+R`)<

#### Android

1. Open up `android/app/src/main/java/[...]/MainActivity.java`
  - Add `import com.reactlibrary.RNReactNativeZebraPrinterPackage;` to the imports at the top of the file
  - Add `new RNReactNativeZebraPrinterPackage()` to the list returned by the `getPackages()` method
2. Append the following lines to `android/settings.gradle`:
  	```
  	include ':react-native-zebra-printer'
  	project(':react-native-zebra-printer').projectDir = new File(rootProject.projectDir, 	'../node_modules/react-native-zebra-printer/android')
  	```
3. Insert the following lines inside the dependencies block in `android/app/build.gradle`:
  	```
      compile project(':react-native-zebra-printer')
  	```

## Usage
```typescript
import * as React from "react";
import { Button } from "react-native";
import RNReactNativeZebraPrinter from "react-native-zebra-printer";

interface IProps {}

const PrinterExample: React.FC<IProps> = () => {
    const [printer, setPrinter] = React.useState<any | null>(null);

    React.useEffect(() => {
        connectPrinter();

        return () => {
            if (printer !== null) {
                RNReactNativeZebraPrinter.closeConnection();
            }
        };
    }, []);

    /**
     * Create a connection to the printer.
     */
    const connectPrinter = async () => {
        const devices = await RNReactNativeZebraPrinter.getBondedDevices();
        const printers = devices.filter((device: any) => device.class === 1664);
        const p = printers.length ? printers[0] : null;
        if (p === null) {
            console.warn("unable to find printer. Found devices:", devices);
        }

        setPrinter(p);

        RNReactNativeZebraPrinter.initConnection(p.id);
    };

    const print = () => {
        RNReactNativeZebraPrinter.print(
            printer.id,
            "Tenant name",
            "Trip name",
            "Depot 1",
            "Levering",
            "2020-01-01",
            "23:59",
            "1/10",
            "1234 AB",
			"Amsterdam"
        );
    };

    return <Button title="print" onPress={print} />;
};

export { PrinterExample };
```
  
