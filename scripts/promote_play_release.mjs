import { google } from "googleapis";

const packageName = process.env.PLAY_PACKAGE_NAME;
const versionCode = String(process.env.PLAY_VERSION_CODE);
const serviceAccount = JSON.parse(process.env.PLAY_SERVICE_ACCOUNT_JSON);

if (!packageName || !versionCode || !serviceAccount.client_email) {
  throw new Error("Missing Play package, version code, or service account credentials.");
}

const auth = new google.auth.GoogleAuth({
  credentials: serviceAccount,
  scopes: ["https://www.googleapis.com/auth/androidpublisher"],
});
const publisher = google.androidpublisher({ version: "v3", auth });
const edit = await publisher.edits.insert({ packageName });
const editId = edit.data.id;

if (!editId) {
  throw new Error("Google Play returned an edit without an ID.");
}

const allTracks = await publisher.edits.tracks.list({
  packageName,
  editId,
});
const track = (allTracks.data.tracks ?? []).find((item) =>
  (item.releases ?? []).some((release) =>
    (release.versionCodes ?? []).some((code) => String(code) === versionCode),
  ),
);
const release = track?.releases?.find((item) =>
  (item.versionCodes ?? []).some((code) => String(code) === versionCode),
);

if (!release) {
  const available = (allTracks.data.tracks ?? [])
    .map((item) => `${item.track}: ${JSON.stringify(item.releases ?? [])}`)
    .join("\n");
  throw new Error(
    `No release found for version code ${versionCode}. Available tracks:\n${available}`,
  );
}

release.status = "completed";
await publisher.edits.tracks.update({
  packageName,
  editId,
  track: "internal",
  requestBody: {
    track: "internal",
    releases: [
      {
        ...release,
        status: "completed",
      },
    ],
  },
});
await publisher.edits.commit({ packageName, editId });

console.log(
  `Promoted version code ${versionCode} to completed on the internal track.`,
);
