// End-to-end test of the Phase 1 backend against the Firebase emulators (auth, functions, firestore).
// Creates users through the Auth emulator, calls the callables over HTTP with real emulator ID tokens,
// and reads Firestore through its REST API with those tokens so the security rules are enforced.
const PROJECT = "driver-safety-monitor-58fff";
const REGION = "asia-south1";
const AUTH = "http://127.0.0.1:9099";
const FUNCS = `http://127.0.0.1:5001/${PROJECT}/${REGION}`;
const FS = `http://127.0.0.1:8080/v1/projects/${PROJECT}/databases/(default)/documents`;

let failures = 0;
function check(cond, label, extra = "") {
  if (cond) console.log(`  ok   ${label}`);
  else { failures++; console.log(`  FAIL ${label} ${extra}`); }
}

async function createUser(email, name) {
  const r = await fetch(`${AUTH}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake`, {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password: "password123", returnSecureToken: true }),
  });
  const j = await r.json();
  if (!j.idToken) throw new Error("signUp failed: " + JSON.stringify(j));
  const u = await fetch(`${AUTH}/identitytoolkit.googleapis.com/v1/accounts:update?key=fake`, {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ idToken: j.idToken, displayName: name, returnSecureToken: true }),
  });
  const uj = await u.json();
  return { uid: j.localId, email, name, token: uj.idToken || j.idToken };
}

async function call(user, fn, data = {}) {
  const r = await fetch(`${FUNCS}/${fn}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${user.token}` },
    body: JSON.stringify({ data }),
  });
  const j = await r.json();
  if (j.error) return { error: { status: j.error.status, message: j.error.message, key: j.error.details?.key } };
  return { result: j.result };
}

async function fsGet(user, path) {
  const r = await fetch(`${FS}/${path}`, { headers: user ? { Authorization: `Bearer ${user.token}` } : {} });
  return { status: r.status, body: await r.json() };
}

async function fsQuery(user, collection, field, value) {
  const r = await fetch(`${FS}:runQuery`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...(user ? { Authorization: `Bearer ${user.token}` } : {}) },
    body: JSON.stringify({ structuredQuery: { from: [{ collectionId: collection }], where: { fieldFilter: { field: { fieldPath: field }, op: "EQUAL", value: { stringValue: value } } } } }),
  });
  const body = await r.json();
  return { status: r.status, body };
}

async function fsWrite(user, path, fields) {
  const r = await fetch(`${FS}/${path}`, {
    method: "PATCH", headers: { "Content-Type": "application/json", Authorization: `Bearer ${user.token}` },
    body: JSON.stringify({ fields }),
  });
  return r.status;
}

const admin = await createUser(`admin${Date.now()}@test.com`, "Asha Admin");
const driver = await createUser(`driver${Date.now()}@test.com`, "Dev Driver");
const second = await createUser(`second${Date.now()}@test.com`, "Sunil Second");
const outsider = await createUser(`out${Date.now()}@test.com`, "Olga Outsider");

console.log("\n# roles");
let r = await call(admin, "createPairingCode");
check(r.error?.key === "not_admin", "admin without role cannot create code", JSON.stringify(r));
r = await call(admin, "setRole", { role: "boss" });
check(r.error?.key === "invalid_role", "invalid role rejected", JSON.stringify(r));
for (const [u, role] of [[admin, "admin"], [driver, "driver"], [second, "admin"], [outsider, "admin"]]) {
  r = await call(u, "setRole", { role });
  check(r.result?.ok === true, `setRole ${role} for ${u.name}`, JSON.stringify(r));
}
let g = await fsGet(admin, `users/${admin.uid}`);
check(g.status === 200 && g.body.fields.role.stringValue === "admin" && g.body.fields.displayName.stringValue === "Asha Admin", "user can read own profile with role and name", JSON.stringify(g.body).slice(0, 200));
g = await fsGet(driver, `users/${admin.uid}`);
check(g.status === 403, "user cannot read another profile", String(g.status));
check((await fsWrite(admin, `users/${admin.uid}`, { role: { stringValue: "driver" } })) === 403, "client cannot write role");

console.log("\n# primary pairing");
r = await call(driver, "createPairingCode");
check(r.error?.key === "not_admin", "driver cannot create pairing code");
r = await call(admin, "createPairingCode");
check(/^\d{6}$/.test(r.result?.code || "") && r.result.expiresAtMillis > Date.now() + 9 * 60 * 1000, "admin gets 6-digit code valid ~10 min", JSON.stringify(r));
const code1 = r.result.code;
r = await call(admin, "redeemPairingCode", { code: code1 });
check(r.error?.key === "not_driver", "admin cannot redeem a pairing code");
r = await call(driver, "redeemPairingCode", { code: "12" });
check(r.error?.key === "invalid_code_format", "bad format rejected");
r = await call(driver, "redeemPairingCode", { code: code1 === "000000" ? "000001" : "000000" });
check(r.error?.key === "wrong_code", "wrong code rejected", JSON.stringify(r));
r = await call(driver, "redeemPairingCode", { code: code1 });
check(r.result?.adminName === "Asha Admin", "driver redeems code, gets admin name", JSON.stringify(r));
r = await call(driver, "redeemPairingCode", { code: code1 });
check(r.error?.key === "code_used" || r.error?.key === "already_linked", "code cannot be used twice", JSON.stringify(r));

g = await fsGet(admin, `drivers/${driver.uid}`);
check(g.status === 403, "admin cannot read driver record before consent", String(g.status));
g = await fsGet(driver, `drivers/${driver.uid}`);
check(g.status === 200 && g.body.fields.linkStatus.stringValue === "pending", "driver reads own record, status pending");
let q = await fsQuery(admin, "links", "adminId", admin.uid);
check(q.status === 200 && q.body.some((d) => d.document?.fields?.status?.stringValue === "pending_consent"), "admin list query on links allowed, sees pending link", JSON.stringify(q.body).slice(0, 300));
q = await fsQuery(driver, "links", "driverId", driver.uid);
check(q.status === 200 && q.body.some((d) => d.document?.fields?.adminName?.stringValue === "Asha Admin"), "driver list query on links allowed", JSON.stringify(q.body).slice(0, 300));
q = await fsQuery(outsider, "links", "adminId", admin.uid);
check(q.status === 403, "outsider cannot query someone else's links", String(q.status));
g = await fsGet(driver, `pairingCodes/anything`);
check(g.status === 403, "pairingCodes not readable");

r = await call(driver, "respondToConsent", { adminId: outsider.uid, accept: true });
check(r.error?.key === "nothing_pending", "consent for unknown admin rejected");
r = await call(driver, "respondToConsent", { adminId: admin.uid, accept: true });
check(r.result?.ok === true, "driver agrees", JSON.stringify(r));
g = await fsGet(admin, `drivers/${driver.uid}`);
check(g.status === 200 && g.body.fields.linkStatus.stringValue === "active", "admin can read driver record after consent", String(g.status));
g = await fsGet(outsider, `drivers/${driver.uid}`);
check(g.status === 403, "outsider still cannot read driver record");
check((await fsWrite(admin, `drivers/${driver.uid}`, { linkStatus: { stringValue: "x" } })) === 403, "admin cannot write driver record (phase 1)");
r = await call(admin, "setRole", { role: "driver" });
check(r.error?.key === "remove_links_first", "linked admin cannot change role");

console.log("\n# second admin");
r = await call(second, "createCoAdminCode", { driverId: driver.uid });
check(r.error?.key === "not_primary", "non-primary cannot create co-admin code");
r = await call(admin, "createCoAdminCode", { driverId: driver.uid });
check(/^\d{6}$/.test(r.result?.code || ""), "primary creates co-admin code", JSON.stringify(r));
const code2 = r.result.code;
r = await call(driver, "redeemPairingCode", { code: code2 });
check(r.error?.key === "wrong_code" || r.error?.key === "already_linked", "co-admin code cannot be used as pairing code", JSON.stringify(r));
r = await call(admin, "redeemCoAdminCode", { code: code2 });
check(r.error?.key === "own_code", "primary cannot redeem own co-admin code", JSON.stringify(r));
r = await call(second, "redeemCoAdminCode", { code: code2 });
check(r.result?.driverName === "Dev Driver", "second admin redeems co-admin code", JSON.stringify(r));
g = await fsGet(second, `drivers/${driver.uid}`);
check(g.status === 403, "second admin cannot read driver record before consent");
q = await fsQuery(second, "links", "adminId", second.uid);
check(q.status === 200 && q.body.some((d) => d.document?.fields?.role?.stringValue === "secondary" && d.document?.fields?.status?.stringValue === "pending_consent"), "second admin sees own pending link");
r = await call(admin, "createCoAdminCode", { driverId: driver.uid });
check(r.error?.key === "has_secondary", "no second co-admin code while one is pending");
r = await call(driver, "respondToConsent", { adminId: second.uid, accept: true });
check(r.result?.ok === true, "driver agrees to second admin");
g = await fsGet(second, `drivers/${driver.uid}`);
check(g.status === 200 && g.body.fields.secondaryStatus.stringValue === "active" && g.body.fields.adminIds.arrayValue.values.length === 2, "second admin reads record after consent; adminIds has 2", JSON.stringify(g.body.fields.adminIds));

console.log("\n# removal paths");
r = await call(second, "removeSecondaryAdmin", { driverId: driver.uid });
check(r.error?.key === "not_primary", "secondary cannot remove secondary");
r = await call(outsider, "leaveDriver", { driverId: driver.uid });
check(r.error?.key === "not_linked", "outsider cannot leave");
r = await call(second, "leaveDriver", { driverId: driver.uid });
check(r.result?.ok === true, "second admin leaves");
g = await fsGet(admin, `drivers/${driver.uid}`);
check(g.body.fields.secondaryAdminId.nullValue === null && g.body.fields.adminIds.arrayValue.values.length === 1, "record shows no secondary after leave", JSON.stringify(g.body.fields.adminIds));
g = await fsGet(second, `drivers/${driver.uid}`);
check(g.status === 403, "ex-second admin can no longer read record");
// re-add second admin, then primary removes them
r = await call(admin, "createCoAdminCode", { driverId: driver.uid });
r = await call(second, "redeemCoAdminCode", { code: r.result.code });
r = await call(driver, "respondToConsent", { adminId: second.uid, accept: true });
r = await call(admin, "removeSecondaryAdmin", { driverId: driver.uid });
check(r.result?.ok === true, "primary removes second admin");
q = await fsQuery(second, "links", "adminId", second.uid);
check(q.status === 200 && !q.body.some((d) => d.document), "second admin has no links left");
// driver removes primary -> everything ends
r = await call(admin, "createCoAdminCode", { driverId: driver.uid });
r = await call(second, "redeemCoAdminCode", { code: r.result.code });
r = await call(driver, "respondToConsent", { adminId: second.uid, accept: true });
r = await call(driver, "removeAdmin", { adminId: admin.uid });
check(r.result?.ok === true, "driver removes primary admin");
g = await fsGet(driver, `drivers/${driver.uid}`);
check(g.body.fields.linkStatus.stringValue === "unlinked" && g.body.fields.adminIds.arrayValue.values === undefined, "driver record reset to unlinked", JSON.stringify(g.body.fields));
q = await fsQuery(driver, "links", "driverId", driver.uid);
check(!q.body.some((d) => d.document), "all links deleted when primary removed");
g = await fsGet(admin, `drivers/${driver.uid}`);
check(g.status === 403, "ex-primary cannot read record");
r = await call(driver, "setRole", { role: "admin" });
check(r.result?.ok === true, "unlinked user can change role");
r = await call(driver, "setRole", { role: "driver" });

console.log("\n# decline + primary leaves + rate limit");
r = await call(admin, "createPairingCode");
r = await call(driver, "redeemPairingCode", { code: r.result.code });
r = await call(driver, "respondToConsent", { adminId: admin.uid, accept: false });
check(r.result?.ok === true, "driver declines primary");
q = await fsQuery(driver, "links", "driverId", driver.uid);
check(!q.body.some((d) => d.document), "decline removes link");
r = await call(admin, "createPairingCode");
r = await call(driver, "redeemPairingCode", { code: r.result.code });
r = await call(driver, "respondToConsent", { adminId: admin.uid, accept: true });
r = await call(admin, "leaveDriver", { driverId: driver.uid });
check(r.result?.ok === true, "primary admin leaves (remove driver)");
g = await fsGet(driver, `drivers/${driver.uid}`);
check(g.body.fields.linkStatus.stringValue === "unlinked", "driver unlinked after primary left");

let last;
for (let i = 0; i < 6; i++) last = await call(driver, "redeemPairingCode", { code: "999999" });
check(last.error?.key === "too_many_attempts", "6th wrong code is rate limited", JSON.stringify(last));
r = await call(admin, "createPairingCode");
const goodCode = r.result.code;
r = await call(driver, "redeemPairingCode", { code: goodCode });
check(r.error?.key === "too_many_attempts", "rate limit also blocks a correct code", JSON.stringify(r));

console.log("\n# concurrency: two drivers redeem the same code at once");
const driver2 = await createUser(`driver2${Date.now()}@test.com`, "Second Driver");
await call(driver2, "setRole", { role: "driver" });
const driver3 = await createUser(`driver3${Date.now()}@test.com`, "Third Driver");
await call(driver3, "setRole", { role: "driver" });
r = await call(admin, "createPairingCode");
const raceCode = r.result.code;
const [a, b] = await Promise.all([
  call(driver2, "redeemPairingCode", { code: raceCode }),
  call(driver3, "redeemPairingCode", { code: raceCode }),
]);
const wins = [a, b].filter((x) => x.result).length;
check(wins === 1, "exactly one driver wins a concurrent redeem", JSON.stringify([a, b]));

console.log(`\n${failures === 0 ? "ALL PASSED" : failures + " FAILURE(S)"}`);
process.exit(failures === 0 ? 0 : 1);
