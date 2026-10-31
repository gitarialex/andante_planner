import { useEffect, useState } from "react";
import {
  AppState,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from "react-native";
import AndanteCore, {
  subscribeToChanges,
  type PermissionStatus,
  type TestTask,
} from "./modules/andante-core";

export default function App() {
  const [tasks, setTasks] = useState<TestTask[]>([]);
  const [permissionStatus, setPermissionStatus] =
    useState<PermissionStatus | null>(null);
  const [logs, setLogs] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);

  const log = (message: string) => {
    setLogs((current) => [
      `${new Date().toLocaleTimeString()}: ${message}`,
      ...current,
    ]);
  };

  const refresh = async () => {
    const [nextTasks, nextPermissions] = await Promise.all([
      AndanteCore.getTasks(),
      AndanteCore.getPermissionStatus(),
    ]);
    setTasks(nextTasks);
    setPermissionStatus(nextPermissions);
  };

  useEffect(() => {
    const unsubscribe = subscribeToChanges(() => {
      void refresh().catch((error: unknown) =>
        log(`Refresh failed: ${String(error)}`),
      );
    });
    const stateSubscription = AppState.addEventListener("change", (state) => {
      if (state === "active") {
        void refresh().catch((error: unknown) =>
          log(`Refresh failed: ${String(error)}`),
        );
      }
    });
    void refresh().catch((error: unknown) =>
      log(`Initial load failed: ${String(error)}`),
    );
    return () => {
      unsubscribe();
      stateSubscription.remove();
    };
  }, []);

  const tests = [
    "Exact alarm: +2 minutes",
    "Five alarms, then reboot",
    "Done action with app closed",
    "Timezone change",
    "Battery restrictions",
    "Four-nudge chain",
    "Refresh widget task",
    "Create/read/change event",
    "Permission status + notification request",
  ];

  const runTest = async (testNumber: number) => {
    setBusy(true);
    try {
      if (testNumber === 9) {
        const before = await AndanteCore.getPermissionStatus();
        setPermissionStatus(before);
        const result = await AndanteCore.requestNotificationPermission();
        const after = await AndanteCore.getPermissionStatus();
        setPermissionStatus(after);
        log(
          `Test 9: POST_NOTIFICATIONS ${JSON.stringify(result)}; status: ${after.notifications}. Use the exact-alarm and battery settings buttons below to complete the flow.`,
        );
      } else {
        log(`Test ${testNumber}: ${await AndanteCore.runScenario(testNumber)}`);
      }
      await refresh();
    } catch (error) {
      log(`Test ${testNumber} failed: ${String(error)}`);
    } finally {
      setBusy(false);
    }
  };

  const requestSpecialAccess = async (
    label: string,
    request: () => Promise<string>,
  ) => {
    try {
      log(`${label}: ${await request()}`);
    } catch (error) {
      log(`${label} request failed: ${String(error)}`);
    }
    try {
      const status = await AndanteCore.getPermissionStatus();
      setPermissionStatus(status);
    } catch (error) {
      log(`Permission status check failed: ${String(error)}`);
    }
  };

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.heading}>M0 reliability spike</Text>
      <Text style={styles.body}>
        Throwaway Android test harness. Run tests on the connected Android 16
        phone; results are not assumed until you verify them.
      </Text>

      <Text style={styles.section}>Appendix D tests</Text>
      {tests.map((title, index) => {
        const number = index + 1;
        return (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={`Run test ${number}: ${title}`}
            disabled={busy}
            key={number}
            onPress={() => void runTest(number)}
            style={({ pressed }) => [
              styles.button,
              pressed && styles.pressed,
              busy && styles.disabled,
            ]}
          >
            <Text style={styles.buttonText}>
              {number}. {title}
            </Text>
          </Pressable>
        );
      })}

      <Text style={styles.section}>Permission helpers (Test 9)</Text>
      <Text style={styles.body}>
        Notifications: {permissionStatus?.notifications ?? "checking"} | Exact
        alarms: {permissionStatus?.exactAlarms ?? "checking"} | Battery
        exemption: {permissionStatus?.ignoringBatteryOptimizations ?? "checking"}
        {"\n"}Battery saver: {permissionStatus?.powerSaveMode ?? "checking"} |
        App background: {permissionStatus?.backgroundRestricted ?? "checking"}
      </Text>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Open exact alarm access settings"
        onPress={() =>
          void requestSpecialAccess(
            "Exact alarm settings",
            AndanteCore.requestExactAlarmAccess,
          )
        }
        style={styles.button}
      >
        <Text style={styles.buttonText}>Open exact alarm access</Text>
      </Pressable>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Request battery optimization exemption"
        onPress={() =>
          void requestSpecialAccess(
            "Battery exemption settings",
            AndanteCore.requestBatteryOptimizationExemption,
          )
        }
        style={styles.button}
      >
        <Text style={styles.buttonText}>Request battery exemption</Text>
      </Pressable>

      <Text style={styles.section}>Tasks (Room via Kotlin)</Text>
      {tasks.length === 0 ? (
        <Text style={styles.body}>No spike tasks yet.</Text>
      ) : (
        tasks.map((task) => (
          <View key={task.id} style={styles.task}>
            <Text style={styles.taskTitle}>{task.title}</Text>
            <Text style={styles.body}>{task.status}</Text>
          </View>
        ))
      )}

      <Text style={styles.section}>Run log</Text>
      {logs.length === 0 ? (
        <Text style={styles.body}>No test results yet.</Text>
      ) : (
        logs.map((entry, index) => (
          <Text key={`${entry}-${index}`} style={styles.log}>
            {entry}
          </Text>
        ))
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: "#F4F8F7",
    flexGrow: 1,
    padding: 20,
    paddingBottom: 36,
  },
  heading: {
    color: "#191C1B",
    fontSize: 24,
    fontWeight: "700",
    marginBottom: 8,
  },
  section: {
    color: "#191C1B",
    fontSize: 18,
    fontWeight: "700",
    marginBottom: 8,
    marginTop: 24,
  },
  body: {
    color: "#4B5755",
    fontSize: 14,
    lineHeight: 21,
    marginBottom: 8,
  },
  button: {
    alignItems: "flex-start",
    backgroundColor: "#CDE9E4",
    borderRadius: 12,
    justifyContent: "center",
    marginBottom: 8,
    minHeight: 48,
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  buttonText: {
    color: "#0B3A36",
    fontSize: 15,
    fontWeight: "600",
  },
  pressed: {
    opacity: 0.75,
  },
  disabled: {
    opacity: 0.5,
  },
  task: {
    backgroundColor: "#FFFFFF",
    borderRadius: 12,
    marginBottom: 8,
    padding: 12,
  },
  taskTitle: {
    color: "#191C1B",
    fontSize: 16,
    fontWeight: "600",
  },
  log: {
    color: "#191C1B",
    fontSize: 13,
    lineHeight: 19,
    marginBottom: 6,
  },
});
