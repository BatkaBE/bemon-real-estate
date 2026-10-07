import { Platform } from 'react-native';
import * as SecureStore from 'expo-secure-store';
import type { HandleStorage } from './api';
// Keep the stable storage key for builds that already hold an opaque session.
const KEY = 'bemon.device.session';
let webHandle: string | null = null;
/** Native handles use Keychain/Keystore; the optional web preview retains its handle only in memory. */
export const storage: HandleStorage = {
  async get() { return Platform.OS === 'web' ? webHandle : SecureStore.getItemAsync(KEY); },
  async set(value) { if (Platform.OS === 'web') webHandle = value; else await SecureStore.setItemAsync(KEY, value, { keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY }); },
  async remove() { if (Platform.OS === 'web') webHandle = null; else await SecureStore.deleteItemAsync(KEY); },
};
