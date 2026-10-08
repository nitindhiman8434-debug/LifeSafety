/**
 * Cloud Functions for Driver Safety Monitor (Phase 1).
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
 * Firestore layout:
 *   users/{uid}                 role + display name, readable only by that user
 *   links/{driverId}_{adminId}  one doc per driver-admin pair, readable by both parties
 *   drivers/{driverId}          driver record + settings, readable by the driver and ACTIVE admins only
 *   pairingCodes/{sha256(code)} hashed codes, never readable by clients
 *   codeAttempts/{uid}          wrong-code counter for rate limiting, never readable by clients
 *
 * Firestore transactions must do ALL reads before the first write. Every transaction below keeps that order.
 */

import { onCall, HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { setGlobalOptions } from "firebase-functions/v2";
import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, DocumentData, FieldValue, Timestamp, Transaction } from "firebase-admin/firestore";
import { createHash, randomInt } from "node:crypto";

initializeApp();
// Mumbai region: closest to users in India. The Android app calls functions in this same region.
setGlobalOptions({ region: "asia-south1", maxInstances: 10 });

const db = getFirestore();

const CODE_TTL_MS = 10 * 60 * 1000; // a code is valid for 10 minutes
// TODO(Phase 3): a scheduled function deletes used/expired pairingCodes and stale codeAttempts docs. Until then they stay (tiny docs).
const MAX_WRONG_CODES = 5; // after 5 wrong codes...
const WRONG_CODE_WINDOW_MS = 15 * 60 * 1000; // ...the user must wait 15 minutes

// Default driver settings. The primary admin edits them from Phase 3.
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

/**
 * Ends the whole link: every link doc of this driver is deleted and the driver goes back to "unlinked".
 * Contains a read (the links query), so it must run before any write in the same transaction.
 */
async function endWholeLink(tx: Transaction, driverId: string): Promise<void> {
  const links = await tx.get(db.collection("links").where("driverId", "==", driverId));
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
  await requireRole(uid, "driver");
  const adminId = parseId(request.data?.adminId, "adminId");
  const accept = parseBool(request.data?.accept, "accept");
  await db.runTransaction(async (tx) => {
    // Reads
    const l = (await tx.get(linkRef(uid, adminId))).data();
    if (!l || l.status !== "pending_consent") {
      throw fail("failed-precondition", "nothing_pending", "Nothing is waiting for your approval.");
    }
    const driverSnap = await tx.get(driverRef(uid));
    if (!driverSnap.exists) throw fail("failed-precondition", "not_linked", "Driver record missing.");
    const now = FieldValue.serverTimestamp();
    if (accept) {
      // Writes: the admin becomes active and can now read the driver's data.
      tx.update(linkRef(uid, adminId), { status: "active", activatedAt: now });
      if (l.role === "primary") {
        tx.update(driverRef(uid), { linkStatus: "active", adminIds: FieldValue.arrayUnion(adminId), updatedAt: now });
      } else {
        tx.update(driverRef(uid), { secondaryStatus: "active", adminIds: FieldValue.arrayUnion(adminId), updatedAt: now });
      }
    } else if (l.role === "primary") {
      await endWholeLink(tx, uid); // its own read happens before its writes
    } else {
      removeSecondary(tx, uid, adminId);
    }
  });
  return { ok: true };
});

export const removeAdmin = onCall(async (request) => {
  const uid = requireAuth(request);
  await requireRole(uid, "driver");
  const adminId = parseId(request.data?.adminId, "adminId");
  await db.runTransaction(async (tx) => {
    const l = (await tx.get(linkRef(uid, adminId))).data();
    if (!l) throw fail("not-found", "not_linked", "This admin is not linked to you.");
    if (l.role === "primary") {
      await endWholeLink(tx, uid);
    } else {
      removeSecondary(tx, uid, adminId);
    }
  });
  return { ok: true };
});

export const leaveDriver = onCall(async (request) => {
  const uid = requireAuth(request);
  await requireRole(uid, "admin");
  const driverId = parseId(request.data?.driverId, "driverId");
  await db.runTransaction(async (tx) => {
    const l = (await tx.get(linkRef(driverId, uid))).data();
    if (!l) throw fail("not-found", "not_linked", "You are not linked to this driver.");
    if (l.role === "primary") {
      await endWholeLink(tx, driverId);
    } else {
      removeSecondary(tx, driverId, uid);
    }
  });
  return { ok: true };
});

export const removeSecondaryAdmin = onCall(async (request) => {
  const uid = requireAuth(request);
  await requireRole(uid, "admin");
  const driverId = parseId(request.data?.driverId, "driverId");
  await db.runTransaction(async (tx) => {
    const d = (await tx.get(driverRef(driverId))).data();
    if (!d || d.primaryAdminId !== uid || d.linkStatus !== "active") {
      throw fail("permission-denied", "not_primary", "Only the primary admin can remove the second admin.");
    }
    const secondaryId = d.secondaryAdminId as string | null;
    if (!secondaryId) throw fail("failed-precondition", "no_secondary", "There is no second admin.");
    removeSecondary(tx, driverId, secondaryId);
  });
  return { ok: true };
});
