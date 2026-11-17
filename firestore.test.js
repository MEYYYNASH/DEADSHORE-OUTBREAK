const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function nowTimestamp() {
  return new Date(Date.now() - 1000);
}

function validSavePayload(userId) {
  const ts = nowTimestamp();
  return {
    userId,
    displayName: "El Lobo",
    wave: 2,
    highestWave: 4,
    totalKills: 18,
    waveKills: 3,
    health: 88.5,
    food: 72.0,
    water: 64.0,
    stamina: 95.0,
    activeHotbarSlot: 0,
    m4Mag: 24,
    glockMag: 17,
    flashlightOn: true,
    dayMode: false,
    backpackItems: [
      { slotIndex: 0, itemId: "ammo_556", quantity: 60 },
      { slotIndex: 1, itemId: "water_bottle", quantity: 2 },
    ],
    hotbarSlots: ["m4_carbine", "glock_17", "baseball_bat", "medkit", "water_bottle", ""],
    equipment: {
      head: "tactical_cap",
      vest: "kevlar_vest",
      body: "coastal_jacket",
      back: "hiking_pack",
      legs: "cargo_pants",
      feet: "combat_boots",
    },
    createdAt: ts,
    updatedAt: ts,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated caller cannot read or list saves", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("saves").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("saves").doc("current").get()
  );
});

test("Authenticated owner can create, read, list, update, and delete own save", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ref = aliceDb.collection("users").doc(ALICE_UID).collection("saves").doc("current");

  await assertSucceeds(ref.set(validSavePayload(ALICE_UID)));
  await assertSucceeds(ref.get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("saves").get());
  await assertSucceeds(
    ref.update({
      wave: 3,
      totalKills: 30,
      updatedAt: nowTimestamp(),
    })
  );
  await assertSucceeds(ref.delete());
});

test("Cross-user access is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("saves")
      .doc("current")
      .set(validSavePayload(ALICE_UID))
  );

  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("saves").doc("current").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("saves").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("saves")
      .doc("current")
      .update({ wave: 99, updatedAt: nowTimestamp() })
  );
});

test("Shadow update with ghost field is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ref = aliceDb.collection("users").doc(ALICE_UID).collection("saves").doc("current");
  await assertFails(
    ref.set({
      ...validSavePayload(ALICE_UID),
      isGodMode: true,
    })
  );
});

test("Out-of-bounds health or wave is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ref = aliceDb.collection("users").doc(ALICE_UID).collection("saves").doc("current");
  await assertFails(
    ref.set({
      ...validSavePayload(ALICE_UID),
      health: 999.0,
    })
  );
});
