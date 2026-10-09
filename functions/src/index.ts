/**
 * Cloud Functions for Driver Safety Monitor.
 *
 * Everything that links a driver to an admin runs here, never from the app directly:
 *   setRole              - a user picks Admin or Driver (changeable only while unlinked)
 *   createPairingCode    - primary admin gets a 6-digit code for a new driver
 *   redeemPairingCode    - driver enters that code; a link waits for the driver's consent
 *   createCoAdminCode    - primary admin gets a 6-digit code for a second admin
 *   redeemCoAdminCode    - second admin enters it; a link waits for the driver's consent
 *   respondToConsent     - driver taps Agree or Decline on the consent screen
 *   removeAdmin          - driver removes an admin from "Who can see my data"
 *   leaveDriver          - an admin leaves a driver (primary leaving ends the whole link)
 *   removeSecondaryAdmin - primary admin removes the second admin
 *
 * Phase 3 (admin side):
 *   registerFcmToken / unregisterFcmToken - the phone tells us where to send push notifications
 *   updateDriverSettings - primary admin changes the speed limit and the other settings
 *   requestTripStart     - an admin asks the driver to start a trip (push to the driver's phone)
 *   endTripNow           - primary admin ends the current trip (a command the driver's phone obeys)
 *   onDriverEvent        - Firestore trigger: an overspeed event becomes an alert in both admins' inboxes
 *                          plus a push notification
 *
 * Firestore layout:
 *   users/{uid}                 role + display name + fcmTokens, readable only by that user
 *   users/{uid}/alerts/{id}     the admin's alert inbox, readable by that admin (only "read" is writable)
 *   links/{driverId}_{adminId}  one doc per driver-admin pair, readable by both parties
 *   drivers/{driverId}          driver record, settings, live block, command; readable by the driver and ACTIVE admins
 *   drivers/{driverId}/events   written by the driver's phone (overspeed started, back to normal, trip start/end)
 *   drivers/{driverId}/trips    trip summaries, with a points subcollection of GPS batches
 *   pairingCodes/{sha256(code)} hashed codes, never readable by clients
 *   codeAttempts/{uid}          wrong-code counter for rate limiting, never readable by clients
 *
 * Firestore transactions must do ALL reads before the first write. Every transaction below keeps that order.
 * Alerts and push messages are sent AFTER a transaction commits, never inside it.
 */

import { onCall, HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { setGlobalOptions } from "firebase-functions/v2";
import * as logger from "firebase-functions/logger";
import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, DocumentData, FieldValue, Timestamp, Transaction } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { createHash, randomInt } from "node:crypto";

initializeApp();
// Mumbai region: closest to users in India. The Android app calls functions in this same region.
// The Firestore trigger (onDriverEvent) runs here too; the Firebase CLI points it at the database's own
// location automatically, whatever that is.
setGlobalOptions({ region: "asia-south1", maxInstances: 10 });

const db = getFirestore();

const CODE_TTL_MS = 10 * 60 * 1000; // a code is valid for 10 minutes
// TODO(Phase 4): a scheduled function deletes used/expired pairingCodes and stale codeAttempts docs. Until then they stay (tiny docs).
const MAX_WRONG_CODES = 5; // after 5 wrong codes...
const WRONG_CODE_WINDOW_MS = 15 * 60 * 1000; // ...the user must wait 15 minutes

// Default driver settings. The primary admin edits them with updateDriverSettings.
const DEFAULT_SETTINGS = {
  speedLimitKmh: 60,
  toleranceKmh: 0,
  adminAlertDelaySec: 10,
  autoEndMinutes: 15,
  driverCanEndTrip: false,
};

type Role = "admin" | "driver";

/**
 * Error keys the Android app maps to translated strings (see FunctionErrors.kt).
 * The message stays human-readable in English for logs.
 */
function fail(code: "not-found" | "failed-precondition" | "permission-denied" | "invalid-argument" | "resource-exhausted" | "unauthenticated" | "internal", key: string, message: string): HttpsError {
  return new HttpsError(code, message, { key });
}

function requireAuth(request: CallableRequest): string {
  if (!request.auth) throw fail("unauthenticated", "sign_in", "Please sign in first.");
  return request.auth.uid;
}

async function requireRole(uid: string, role: Role): Promise<{ displayName: string }> {
  const snap = await db.collection("users").doc(uid).get();
  const data = snap.data();
  if (!data || data.role !== role) {
    throw role === "admin"
      ? fail("permission-denied", "not_admin", "Only admins can do this.")
      : fail("permission-denied", "not_driver", "Only drivers can do this.");
  }
  return { displayName: (data.displayName as string) || "Unknown" };
}

function parseCode(raw: unknown): string {
  if (typeof raw !== "string" || !/^\d{6}$/.test(raw)) {
    throw fail("invalid-argument", "invalid_code_format", "Enter the 6-digit code.");
  }
  return raw;
}

// Firebase Auth UIDs are plain alphanumeric strings. Anything else ("/", ".", "_") could form a different
// Firestore path or an ambiguous links/{driverId}_{adminId} key, so it is rejected up front.
const UID_PATTERN = /^[A-Za-z0-9]{1,128}$/;

function parseId(raw: unknown, name: string): string {
  if (typeof raw !== "string" || !UID_PATTERN.test(raw)) {
    throw fail("invalid-argument", "missing_argument", `Missing ${name}.`);
  }
  return raw;
}

function parseBool(raw: unknown, name: string): boolean {
  if (typeof raw !== "boolean") throw fail("invalid-argument", "missing_argument", `Missing ${name}.`);
  return raw;
}

/** A whole number inside [min, max]; anything else is rejected with the invalid_settings key. */
function parseIntIn(raw: unknown, min: number, max: number, name: string): number {
  if (typeof raw !== "number" || !Number.isInteger(raw) || raw < min || raw > max) {
    throw fail("invalid-argument", "invalid_settings", `${name} must be a whole number from ${min} to ${max}.`);
  }
  return raw;
}

/** Optional phone number for the Call button: digits, spaces, +, -, ( ). Empty means "none". */
function parsePhone(raw: unknown): string | null {
  if (raw === undefined || raw === null) return null;
  if (typeof raw !== "string") throw fail("invalid-argument", "invalid_phone", "Phone must be text.");
  const phone = raw.trim();
  if (phone === "") return null;
  if (!/^\+?[0-9 ()-]{6,20}$/.test(phone)) throw fail("invalid-argument", "invalid_phone", "Enter a valid phone number.");
  return phone;
}

/** An FCM registration token as the Firebase Messaging SDK hands it out (long, no spaces). */
function parseToken(raw: unknown): string {
  if (typeof raw !== "string" || raw.length < 20 || raw.length > 4096 || /\s/.test(raw)) {
    throw fail("invalid-argument", "missing_argument", "Missing token.");
  }
  return raw;
}

// Codes are stored only as a hash. With a 10-minute life, single use and rate limiting, SHA-256 is enough here.
function hashCode(code: string): string {
  return createHash("sha256").update(code).digest("hex");
}

async function newUniqueCode(): Promise<{ code: string; hash: string }> {
  for (let attempt = 0; attempt < 5; attempt++) {
    const code = randomInt(0, 1_000_000).toString().padStart(6, "0");
    const hash = hashCode(code);
    const existing = await db.collection("pairingCodes").doc(hash).get();
    const data = existing.data();
    const stillLive = data && !data.used && (data.expiresAt as Timestamp).toMillis() > Date.now();
    if (!stillLive) return { code, hash };
  }
  throw fail("internal", "code_generation_failed", "Could not generate a code. Try again.");
}

// Code failures the rate limiter counts as a guess; the key tells the app which message to show.
function wrongCode(key = "wrong_code", message = "Wrong code. Check the 6 digits and try again."): HttpsError {
  return fail("not-found", key, message);
}

/** Returns the error for a missing, used or expired code, or null when the code is still good. */
function validateCode(c: DocumentData | undefined, type: "primary" | "secondary", askWhom: string): HttpsError | null {
  if (!c || c.type !== type) return wrongCode();
  if (c.used) return wrongCode("code_used", `This code was already used. Ask ${askWhom} for a new one.`);
  if ((c.expiresAt as Timestamp).toMillis() < Date.now()) {
    return wrongCode("code_expired", `This code has expired. Ask ${askWhom} for a new one.`);
  }
  return null;
}

// ---- Rate limiting for code guessing ----------------------------------------------------------
// The wrong-code counter is read and written inside the same transaction as the code lookup. Firestore runs
// transactions that touch the same document one after another, so parallel guesses cannot slip past the limit.

interface WrongCodeGuard {
  /** Counts one wrong code (a write, so only after all reads) and hands back the error to throw once the transaction committed. */
  reject(error: HttpsError): HttpsError;
  /** Forgets earlier wrong codes after a correct one (a write). */
  clear(): void;
}

/** Must be the FIRST read of a redeem transaction. Throws when the user is locked out. */
async function checkWrongCodes(tx: Transaction, uid: string): Promise<WrongCodeGuard> {
  const ref = db.collection("codeAttempts").doc(uid);
  const data = (await tx.get(ref)).data();
  const now = Date.now();
  const inWindow = !!data && now - (data.windowStart as Timestamp).toMillis() < WRONG_CODE_WINDOW_MS;
  if (inWindow && (data!.count as number) >= MAX_WRONG_CODES) {
    throw fail("resource-exhausted", "too_many_attempts", "Too many wrong codes. Wait 15 minutes and try again.");
  }
  return {
    reject(error) {
      if (inWindow) tx.update(ref, { count: FieldValue.increment(1) });
      else tx.set(ref, { count: 1, windowStart: Timestamp.fromMillis(now) });
      return error;
    },
    clear() {
      if (data) tx.delete(ref);
    },
  };
}

// ---- Link helpers -----------------------------------------------------------------------------

function linkRef(driverId: string, adminId: string) {
  return db.collection("links").doc(`${driverId}_${adminId}`);
}

function driverRef(driverId: string) {
  return db.collection("drivers").doc(driverId);
}

/** Removes the second admin only. Write-only: call after all reads in the transaction. */
function removeSecondary(tx: Transaction, driverId: string, adminId: string): void {
  tx.delete(linkRef(driverId, adminId));
  tx.update(driverRef(driverId), {
    secondaryAdminId: null,
    secondaryAdminName: null,
    secondaryStatus: "none",
    adminIds: FieldValue.arrayRemove(adminId),
    updatedAt: FieldValue.serverTimestamp(),
  });
}

interface RemovedAdmin {
  adminId: string;
  adminName: string;
  role: string;
}

/**
 * Ends the whole link: every link doc of this driver is deleted and the driver goes back to "unlinked".
 * Contains a read (the links query), so it must run before any write in the same transaction.
 * Returns the admins that were linked, so the caller can alert them after the commit.
 */
async function endWholeLink(tx: Transaction, driverId: string): Promise<RemovedAdmin[]> {
  const links = await tx.get(db.collection("links").where("driverId", "==", driverId));
  const removed: RemovedAdmin[] = links.docs.map((doc) => ({
    adminId: doc.data().adminId as string,
    adminName: (doc.data().adminName as string) || "Unknown",
    role: doc.data().role as string,
  }));
  links.docs.forEach((doc) => tx.delete(doc.ref));
  tx.update(driverRef(driverId), {
    linkStatus: "unlinked",
    primaryAdminId: null,
    primaryAdminName: null,
    secondaryAdminId: null,
    secondaryAdminName: null,
    secondaryStatus: "none",
    adminIds: [],
    updatedAt: FieldValue.serverTimestamp(),
  });
  return removed;
}

// ---- Alerts and push notifications ------------------------------------------------------------
// Every alert is one document per recipient under users/{adminId}/alerts, plus a data-only push message to
// that admin's phones. The phone builds the notification text itself (so it is in the phone's language).
// Alert types the app knows (AlertType in alerts/Alert.kt): overspeed_started, back_to_normal,
// admin_added, admin_removed, driver_unlinked. Phase 4 adds sos, low_battery, tracking_lost and the tamper alerts.

const ALERT_EVENT_TYPES = new Set(["overspeed_started", "back_to_normal"]);
const MAX_TOKENS_PER_USER = 5;

type PushData = Record<string, string>;

/** FCM data values must be strings; nulls and undefined are left out. */
function pushData(fields: Record<string, unknown>): PushData {
  const data: PushData = {};
  for (const [key, value] of Object.entries(fields)) {
    if (value !== null && value !== undefined) data[key] = String(value);
  }
  return data;
}

/**
 * Sends a data-only message to every registered phone of these users. Tokens FCM reports as dead are
 * dropped from the user's list. Never throws: a push that fails must not undo the work before it.
 * Returns how many phones accepted the message.
 */
async function sendPush(userIds: string[], data: PushData, highPriority: boolean): Promise<number> {
  let delivered = 0;
  for (const userId of userIds) {
    const ref = db.collection("users").doc(userId);
    const tokens = ((await ref.get()).data()?.fcmTokens as string[] | undefined) ?? [];
    if (tokens.length === 0) continue;
    try {
      const response = await getMessaging().sendEachForMulticast({
        tokens,
        data,
        android: { priority: highPriority ? "high" : "normal", ttl: 60 * 60 * 1000 },
      });
      const dead: string[] = [];
      response.responses.forEach((res, i) => {
        if (res.success) {
          delivered++;
        } else if (
          res.error?.code === "messaging/registration-token-not-registered" ||
          res.error?.code === "messaging/invalid-registration-token"
        ) {
          dead.push(tokens[i]);
        } else {
          logger.warn("push not delivered", { userId, code: res.error?.code });
        }
      });
      if (dead.length > 0) await ref.update({ fcmTokens: FieldValue.arrayRemove(...dead) });
    } catch (e) {
      logger.warn("push failed", { userId, error: String(e) });
    }
  }
  return delivered;
}

/** Writes the same alert into each admin's inbox, then pushes it. alertId keeps a retried write from duplicating. */
async function alertAdmins(adminIds: string[], alertId: string, alert: Record<string, unknown>, highPriority: boolean): Promise<void> {
  const recipients = [...new Set(adminIds)];
  if (recipients.length === 0) return;
  const batch = db.batch();
  for (const adminId of recipients) {
    batch.set(db.collection("users").doc(adminId).collection("alerts").doc(alertId), {
      ...alert,
      read: false,
      createdAt: FieldValue.serverTimestamp(),
    });
  }
  await batch.commit();
  await sendPush(recipients, pushData({ type: "alert", alertId, ...alert }), highPriority);
}

function newAlertId(): string {
  return db.collection("users").doc().id;
}

/** An alert about the link itself (admin added or removed, driver unlinked). Not about a trip. */
function linkAlert(alertType: string, driverId: string, driverName: string, extra: Record<string, unknown> = {}) {
  return { alertType, driverId, driverName, timestampUtc: Date.now(), ...extra };
}

// ---- Callable functions -----------------------------------------------------------------------

export const setRole = onCall(async (request) => {
  const uid = requireAuth(request);
  const role = request.data?.role;
  if (role !== "admin" && role !== "driver") {
    throw fail("invalid-argument", "invalid_role", "Role must be admin or driver.");
  }
  const [asAdmin, asDriver] = await Promise.all([
    db.collection("links").where("adminId", "==", uid).limit(1).get(),
    db.collection("links").where("driverId", "==", uid).limit(1).get(),
  ]);
  if (!asAdmin.empty || !asDriver.empty) {
    throw fail("failed-precondition", "remove_links_first", "Remove all links before changing your role.");
  }
  // The Auth user record is the reliable source for the Google profile name; the ID token may lack it.
  const [account, existing] = await Promise.all([getAuth().getUser(uid), db.collection("users").doc(uid).get()]);
  const token = request.auth!.token;
  const email = account.email ?? token.email ?? null;
  const userRef = db.collection("users").doc(uid);
  await userRef.set(
    {
      displayName: account.displayName ?? token.name ?? email ?? "Unknown",
      email,
      photoUrl: account.photoURL ?? token.picture ?? null,
      role,
      ...(existing.exists ? {} : { createdAt: FieldValue.serverTimestamp() }),
      updatedAt: FieldValue.serverTimestamp(),
    },
    { merge: true },
  );
  return { ok: true };
});

export const createPairingCode = onCall(async (request) => {
  const uid = requireAuth(request);
  const admin = await requireRole(uid, "admin");
  const { code, hash } = await newUniqueCode();
  const expiresAt = Timestamp.fromMillis(Date.now() + CODE_TTL_MS);
  await db.collection("pairingCodes").doc(hash).set({
    type: "primary",
    adminId: uid,
    adminName: admin.displayName,
    driverId: null,
    driverName: null,
    expiresAt,
    used: false,
    createdAt: FieldValue.serverTimestamp(),
  });
  // validForMillis lets the app count down from its own clock, so a wrong phone clock does not matter.
  return { code, expiresAtMillis: expiresAt.toMillis(), validForMillis: CODE_TTL_MS };
});

export const redeemPairingCode = onCall(async (request) => {
  const uid = requireAuth(request);
  const driver = await requireRole(uid, "driver");
  const code = parseCode(request.data?.code);
  const codeRef = db.collection("pairingCodes").doc(hashCode(code));
  const outcome = await db.runTransaction(
    async (tx): Promise<{ error?: HttpsError; adminId?: string; adminName?: string }> => {
      // Reads
      const guard = await checkWrongCodes(tx, uid);
      const c = (await tx.get(codeRef)).data();
      const d = (await tx.get(driverRef(uid))).data();
      const codeError = validateCode(c, "primary", "your admin");
      if (codeError) return { error: guard.reject(codeError) };
      const adminId = c!.adminId as string;
      // The admin may have switched to the Driver role after creating the code.
      const creator = (await tx.get(db.collection("users").doc(adminId))).data();
      if (!creator || creator.role !== "admin") {
        return { error: guard.reject(wrongCode("code_invalid_now", "This code is no longer valid. Ask your admin for a new one.")) };
      }
      if (d && d.linkStatus !== "unlinked") {
        throw fail("failed-precondition", "already_linked", "You are already linked to an admin. Remove that admin first.");
      }
      // Writes
      guard.clear();
      const now = FieldValue.serverTimestamp();
      tx.update(codeRef, { used: true, usedBy: uid, usedAt: now });
      tx.set(
        driverRef(uid),
        {
          displayName: driver.displayName,
          linkStatus: "pending",
          primaryAdminId: adminId,
          primaryAdminName: c!.adminName,
          secondaryAdminId: null,
          secondaryAdminName: null,
          secondaryStatus: "none",
          adminIds: [],
          settings: d?.settings ?? DEFAULT_SETTINGS,
          updatedAt: now,
        },
        { merge: true },
      );
      tx.set(linkRef(uid, adminId), {
        driverId: uid,
        adminId,
        role: "primary",
        status: "pending_consent",
        driverName: driver.displayName,
        adminName: c!.adminName,
        createdAt: now,
        activatedAt: null,
      });
      return { adminId, adminName: c!.adminName as string };
    },
  );
  if (outcome.error) throw outcome.error;
  return { adminId: outcome.adminId, adminName: outcome.adminName };
});

export const createCoAdminCode = onCall(async (request) => {
  const uid = requireAuth(request);
  const admin = await requireRole(uid, "admin");
  const driverId = parseId(request.data?.driverId, "driverId");
  const d = (await driverRef(driverId).get()).data();
  if (!d || d.primaryAdminId !== uid || d.linkStatus !== "active") {
    throw fail("permission-denied", "not_primary", "Only the primary admin can add a second admin.");
  }
  if (d.secondaryAdminId) {
    throw fail("failed-precondition", "has_secondary", "This driver already has a second admin. Remove them first.");
  }
  const { code, hash } = await newUniqueCode();
  const expiresAt = Timestamp.fromMillis(Date.now() + CODE_TTL_MS);
  await db.collection("pairingCodes").doc(hash).set({
    type: "secondary",
    adminId: uid,
    adminName: admin.displayName,
    driverId,
    driverName: d.displayName,
    expiresAt,
    used: false,
    createdAt: FieldValue.serverTimestamp(),
  });
  return { code, expiresAtMillis: expiresAt.toMillis(), validForMillis: CODE_TTL_MS };
});

export const redeemCoAdminCode = onCall(async (request) => {
  const uid = requireAuth(request);
  const admin = await requireRole(uid, "admin");
  const code = parseCode(request.data?.code);
  const codeRef = db.collection("pairingCodes").doc(hashCode(code));
  const outcome = await db.runTransaction(
    async (tx): Promise<{ error?: HttpsError; driverId?: string; driverName?: string }> => {
      // Reads
      const guard = await checkWrongCodes(tx, uid);
      const c = (await tx.get(codeRef)).data();
      const codeError = validateCode(c, "secondary", "the primary admin");
      if (codeError) return { error: guard.reject(codeError) };
      if (c!.adminId === uid) {
        throw fail("failed-precondition", "own_code", "You are already the primary admin of this driver.");
      }
      const driverId = c!.driverId as string;
      const d = (await tx.get(driverRef(driverId))).data();
      if (!d || d.linkStatus !== "active" || d.primaryAdminId !== c!.adminId) {
        return { error: guard.reject(wrongCode("code_invalid_now", "This code is no longer valid.")) };
      }
      if (d.secondaryAdminId) {
        throw fail("failed-precondition", "has_secondary", "This driver already has a second admin.");
      }
      // Writes
      guard.clear();
      const now = FieldValue.serverTimestamp();
      tx.update(codeRef, { used: true, usedBy: uid, usedAt: now });
      tx.update(driverRef(driverId), {
        secondaryAdminId: uid,
        secondaryAdminName: admin.displayName,
        secondaryStatus: "pending_consent",
        updatedAt: now,
      });
      tx.set(linkRef(driverId, uid), {
        driverId,
        adminId: uid,
        role: "secondary",
        status: "pending_consent",
        driverName: d.displayName,
        adminName: admin.displayName,
        createdAt: now,
        activatedAt: null,
      });
      return { driverId, driverName: d.displayName as string };
    },
  );
  if (outcome.error) throw outcome.error;
  return { driverId: outcome.driverId, driverName: outcome.driverName };
});

export const respondToConsent = onCall(async (request) => {
  const uid = requireAuth(request);
  const driver = await requireRole(uid, "driver");
  const adminId = parseId(request.data?.adminId, "adminId");
  const accept = parseBool(request.data?.accept, "accept");
  // Who to tell afterwards: the primary admin when a second admin was added.
  const outcome = await db.runTransaction(async (tx): Promise<{ notifyPrimary?: string; adminName: string }> => {
    // Reads
    const l = (await tx.get(linkRef(uid, adminId))).data();
    if (!l || l.status !== "pending_consent") {
      throw fail("failed-precondition", "nothing_pending", "Nothing is waiting for your approval.");
    }
    const driverSnap = await tx.get(driverRef(uid));
    const d = driverSnap.data();
    if (!d) throw fail("failed-precondition", "not_linked", "Driver record missing.");
    const adminName = (l.adminName as string) || "Unknown";
    const now = FieldValue.serverTimestamp();
    if (accept) {
      // Writes: the admin becomes active and can now read the driver's data.
      tx.update(linkRef(uid, adminId), { status: "active", activatedAt: now });
      if (l.role === "primary") {
        tx.update(driverRef(uid), { linkStatus: "active", adminIds: FieldValue.arrayUnion(adminId), updatedAt: now });
        return { adminName };
      }
      tx.update(driverRef(uid), { secondaryStatus: "active", adminIds: FieldValue.arrayUnion(adminId), updatedAt: now });
      return { notifyPrimary: d.primaryAdminId as string, adminName };
    }
    if (l.role === "primary") {
      await endWholeLink(tx, uid); // its own read happens before its writes
    } else {
      removeSecondary(tx, uid, adminId);
    }
    return { adminName };
  });
  if (outcome.notifyPrimary) {
    await alertAdmins(
      [outcome.notifyPrimary],
      newAlertId(),
      linkAlert("admin_added", uid, driver.displayName, { adminName: outcome.adminName, adminRole: "secondary" }),
      false,
    );
  }
  return { ok: true };
});

export const removeAdmin = onCall(async (request) => {
  const uid = requireAuth(request);
  const driver = await requireRole(uid, "driver");
  const adminId = parseId(request.data?.adminId, "adminId");
  const outcome = await db.runTransaction(async (tx): Promise<{ removed: RemovedAdmin[]; wholeLink: boolean }> => {
    const l = (await tx.get(linkRef(uid, adminId))).data();
    if (!l) throw fail("not-found", "not_linked", "This admin is not linked to you.");
    if (l.role === "primary") {
      return { removed: await endWholeLink(tx, uid), wholeLink: true };
    }
    removeSecondary(tx, uid, adminId);
    return { removed: [{ adminId, adminName: (l.adminName as string) || "Unknown", role: "secondary" }], wholeLink: false };
  });
  // The driver removed the primary: everyone who was linked hears that the link ended.
  // The driver removed the second admin: the second admin and the primary hear it.
  if (outcome.wholeLink) {
    await alertAdmins(
      outcome.removed.map((a) => a.adminId),
      newAlertId(),
      linkAlert("driver_unlinked", uid, driver.displayName, { reason: "driver_removed_primary" }),
      false,
    );
  } else {
    const secondary = outcome.removed[0];
    const d = (await driverRef(uid).get()).data();
    const recipients = [secondary.adminId, ...(d?.primaryAdminId ? [d.primaryAdminId as string] : [])];
    await alertAdmins(
      recipients,
      newAlertId(),
      linkAlert("admin_removed", uid, driver.displayName, { adminName: secondary.adminName, adminRole: "secondary", reason: "driver_removed" }),
      false,
    );
  }
  return { ok: true };
});

export const leaveDriver = onCall(async (request) => {
  const uid = requireAuth(request);
  const admin = await requireRole(uid, "admin");
  const driverId = parseId(request.data?.driverId, "driverId");
  const outcome = await db.runTransaction(
    async (tx): Promise<{ others: string[]; wholeLink: boolean; driverName: string }> => {
      const l = (await tx.get(linkRef(driverId, uid))).data();
      if (!l) throw fail("not-found", "not_linked", "You are not linked to this driver.");
      const driverName = (l.driverName as string) || "Unknown";
      if (l.role === "primary") {
        const removed = await endWholeLink(tx, driverId);
        return { others: removed.filter((a) => a.adminId !== uid).map((a) => a.adminId), wholeLink: true, driverName };
      }
      const d = (await tx.get(driverRef(driverId))).data();
      removeSecondary(tx, driverId, uid);
      return { others: d?.primaryAdminId ? [d.primaryAdminId as string] : [], wholeLink: false, driverName };
    },
  );
  // Primary left: the second admin (if any) hears that the link ended. Second admin left: the primary hears it.
  await alertAdmins(
    outcome.others,
    newAlertId(),
    outcome.wholeLink
      ? linkAlert("driver_unlinked", driverId, outcome.driverName, { reason: "primary_left", adminName: admin.displayName })
      : linkAlert("admin_removed", driverId, outcome.driverName, { adminName: admin.displayName, adminRole: "secondary", reason: "left" }),
    false,
  );
  return { ok: true };
});

export const removeSecondaryAdmin = onCall(async (request) => {
  const uid = requireAuth(request);
  const admin = await requireRole(uid, "admin");
  const driverId = parseId(request.data?.driverId, "driverId");
  const removed = await db.runTransaction(async (tx): Promise<{ adminId: string; adminName: string; driverName: string }> => {
    const d = (await tx.get(driverRef(driverId))).data();
    if (!d || d.primaryAdminId !== uid || d.linkStatus !== "active") {
      throw fail("permission-denied", "not_primary", "Only the primary admin can remove the second admin.");
    }
    const secondaryId = d.secondaryAdminId as string | null;
    if (!secondaryId) throw fail("failed-precondition", "no_secondary", "There is no second admin.");
    removeSecondary(tx, driverId, secondaryId);
    return {
      adminId: secondaryId,
      adminName: (d.secondaryAdminName as string) || "Unknown",
      driverName: (d.displayName as string) || "Unknown",
    };
  });
  await alertAdmins(
    [removed.adminId],
    newAlertId(),
    linkAlert("admin_removed", driverId, removed.driverName, { adminName: removed.adminName, adminRole: "secondary", reason: "primary_removed", byName: admin.displayName }),
    false,
  );
  return { ok: true };
});

// ---- Phase 3: push tokens ---------------------------------------------------------------------

export const registerFcmToken = onCall(async (request) => {
  const uid = requireAuth(request);
  const token = parseToken(request.data?.token);
  const ref = db.collection("users").doc(uid);
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw fail("failed-precondition", "no_role", "Choose a role first.");
    // Newest token last; a phone that signs in again just moves to the end. At most 5 phones per user.
    const tokens = ((snap.data()?.fcmTokens as string[] | undefined) ?? []).filter((t) => t !== token);
    tokens.push(token);
    while (tokens.length > MAX_TOKENS_PER_USER) tokens.shift();
    tx.update(ref, { fcmTokens: tokens, fcmUpdatedAt: FieldValue.serverTimestamp() });
  });
  return { ok: true };
});

export const unregisterFcmToken = onCall(async (request) => {
  const uid = requireAuth(request);
  const token = parseToken(request.data?.token);
  await db.collection("users").doc(uid).set({ fcmTokens: FieldValue.arrayRemove(token) }, { merge: true });
  return { ok: true };
});

// ---- Phase 3: admin actions -------------------------------------------------------------------

export const updateDriverSettings = onCall(async (request) => {
  const uid = requireAuth(request);
  await requireRole(uid, "admin");
  const driverId = parseId(request.data?.driverId, "driverId");
  const raw = request.data?.settings;
  if (!raw || typeof raw !== "object") throw fail("invalid-argument", "missing_argument", "Missing settings.");
  // Same limits as DriverSettings.fromMap in the app.
  const settings = {
    speedLimitKmh: parseIntIn(raw.speedLimitKmh, 10, 200, "speedLimitKmh"),
    toleranceKmh: parseIntIn(raw.toleranceKmh, 0, 30, "toleranceKmh"),
    adminAlertDelaySec: parseIntIn(raw.adminAlertDelaySec, 0, 120, "adminAlertDelaySec"),
    autoEndMinutes: parseIntIn(raw.autoEndMinutes, 1, 120, "autoEndMinutes"),
    driverCanEndTrip: parseBool(raw.driverCanEndTrip, "driverCanEndTrip"),
  };
  const phone = parsePhone(request.data?.phone);
  await db.runTransaction(async (tx) => {
    const d = (await tx.get(driverRef(driverId))).data();
    if (!d || d.primaryAdminId !== uid || d.linkStatus !== "active") {
      throw fail("permission-denied", "not_primary", "Only the primary admin can change settings.");
    }
    tx.update(driverRef(driverId), {
      settings,
      phone,
      settingsUpdatedAt: FieldValue.serverTimestamp(),
      settingsUpdatedBy: uid,
      updatedAt: FieldValue.serverTimestamp(),
    });
  });
  return { ok: true };
});

/** Either admin asks the driver to start a trip. The driver's phone shows a notification; tapping it starts the trip. */
export const requestTripStart = onCall(async (request) => {
  const uid = requireAuth(request);
  const admin = await requireRole(uid, "admin");
  const driverId = parseId(request.data?.driverId, "driverId");
  const d = (await driverRef(driverId).get()).data();
  const adminIds = (d?.adminIds as string[] | undefined) ?? [];
  if (!d || !adminIds.includes(uid)) {
    throw fail("permission-denied", "not_linked", "You are not an active admin of this driver.");
  }
  if ((d.live as DocumentData | undefined)?.status === "on_trip") {
    throw fail("failed-precondition", "already_on_trip", "The driver is already on a trip.");
  }
  await driverRef(driverId).update({
    lastStartRequest: { byUid: uid, byName: admin.displayName, at: FieldValue.serverTimestamp() },
    updatedAt: FieldValue.serverTimestamp(),
  });
  const delivered = await sendPush([driverId], pushData({ type: "start_request", driverId, adminName: admin.displayName }), true);
  return { ok: true, delivered };
});

/**
 * The primary admin ends the driver's current trip. Writes a command on the driver record (the trip service
 * listens to it, even if the push does not arrive) and pushes it for speed. The command names the trip, so a
 * late delivery can never end a different, later trip.
 */
export const endTripNow = onCall(async (request) => {
  const uid = requireAuth(request);
  const admin = await requireRole(uid, "admin");
  const driverId = parseId(request.data?.driverId, "driverId");
  const tripId = await db.runTransaction(async (tx): Promise<string> => {
    const d = (await tx.get(driverRef(driverId))).data();
    if (!d || d.primaryAdminId !== uid || d.linkStatus !== "active") {
      throw fail("permission-denied", "not_primary", "Only the primary admin can end a trip.");
    }
    const live = d.live as DocumentData | undefined;
    if (!live || live.status !== "on_trip" || typeof live.tripId !== "string") {
      throw fail("failed-precondition", "no_active_trip", "The driver is not on a trip right now.");
    }
    tx.update(driverRef(driverId), {
      command: { type: "end_trip", tripId: live.tripId, byUid: uid, byName: admin.displayName, at: FieldValue.serverTimestamp() },
      updatedAt: FieldValue.serverTimestamp(),
    });
    return live.tripId as string;
  });
  const delivered = await sendPush([driverId], pushData({ type: "end_trip", driverId, tripId, adminName: admin.displayName }), true);
  return { ok: true, delivered };
});

// ---- Phase 3: driver events become admin alerts ------------------------------------------------

/**
 * Runs once for every event the driver's phone uploads. Overspeed events are copied into each active admin's
 * inbox and pushed to their phones. Trip start/end events are kept for the trip list only.
 * Retries are off (the default), so one event never produces two alerts.
 */
export const onDriverEvent = onDocumentCreated("drivers/{driverId}/events/{eventId}", async (event) => {
  const snap = event.data;
  if (!snap) return;
  const e = snap.data();
  const { driverId, eventId } = event.params;
  const type = e.eventType as string | undefined;
  if (!type || !ALERT_EVENT_TYPES.has(type) || e.driverId !== driverId) return;
  const d = (await driverRef(driverId).get()).data();
  const adminIds = (d?.adminIds as string[] | undefined) ?? [];
  if (adminIds.length === 0) return;
  const alert = {
    alertType: type,
    driverId,
    driverName: (d?.displayName as string) || "Unknown",
    eventId,
    tripId: e.tripId ?? null,
    speedKmh: e.speedKmh ?? null,
    speedLimitKmh: e.speedLimitKmh ?? null,
    topSpeedKmh: e.topSpeedKmh ?? null,
    durationSec: e.durationSec ?? null,
    timestampUtc: e.timestampUtc ?? Date.now(),
    timezoneId: e.timezoneId ?? null,
    latitude: e.latitude ?? null,
    longitude: e.longitude ?? null,
    address: e.address ?? null,
    batteryPercent: e.batteryPercent ?? null,
    delayed: e.delayed === true,
  };
  await alertAdmins(adminIds, eventId, alert, true);
});
