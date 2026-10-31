const fs = require("node:fs");
const path = require("node:path");
const { withDangerousMod } = require("@expo/config-plugins");

module.exports = function withReleaseNoInternet(config) {
  return withDangerousMod(config, [
    "android",
    async (modConfig) => {
      const releaseManifest = path.join(
        modConfig.modRequest.platformProjectRoot,
        "app",
        "src",
        "release",
        "AndroidManifest.xml",
      );
      fs.mkdirSync(path.dirname(releaseManifest), { recursive: true });
      fs.writeFileSync(
        releaseManifest,
        `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
    <uses-permission android:name="android.permission.INTERNET" tools:node="remove" />
</manifest>
`,
      );
      return modConfig;
    },
  ]);
};
