import {
  requireNativeModule,
  type EventEmitter as ExpoEventEmitter,
} from "expo-modules-core";

export type TestTask = {
  id: string;
  title: string;
  status: string;
};

export type PermissionStatus = {
  notifications: string;
  exactAlarms: string;
  ignoringBatteryOptimizations: string;
  powerSaveMode: string;
  backgroundRestricted: string;
};

type AndanteCoreEvents = {
  onDataChanged: (event: { testNumber?: number }) => void;
};

type AndanteCoreModuleType = ExpoEventEmitter<AndanteCoreEvents> & {
  getTasks: () => Promise<TestTask[]>;
  getPermissionStatus: () => Promise<PermissionStatus>;
  requestNotificationPermission: () => Promise<unknown>;
  requestExactAlarmAccess: () => Promise<string>;
  requestBatteryOptimizationExemption: () => Promise<string>;
  runScenario: (testNumber: number) => Promise<string>;
};

const nativeModule = requireNativeModule<AndanteCoreModuleType>("AndanteCore");

export function subscribeToChanges(listener: () => void) {
  const subscription = nativeModule.addListener("onDataChanged", listener);
  return () => subscription.remove();
}

export default nativeModule;
