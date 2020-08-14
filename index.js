
import { NativeModules } from 'react-native';

const { RNReactNativeZebraPrinter } = NativeModules;

// Export the module from NativeModules or an 
// empty object as fallback for iOS and windows.
export default RNReactNativeZebraPrinter || {};
