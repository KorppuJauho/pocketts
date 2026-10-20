// Reference results for the Kotlin tests, made with WebTS's TeamSpeak library (the proven implementation).
//
//   cd <a WebTS checkout> && npx tsx <this repo>/tools/make-test-vectors.mts <this repo>/tsproto/src/test/resources/webts-vectors.json
//
// Run from the WebTS checkout so its own dependencies are used.
import { createRequire } from "node:module";
import { randomBytes, createHash } from "node:crypto";
import { writeFileSync } from "node:fs";
import path from "node:path";
import { pathToFileURL } from "node:url";

const out = process.argv[2];
if (!out) throw new Error("Usage: make-test-vectors.mts <output.json>");
const webts = process.cwd();
const lib = (f: string) => import(pathToFileURL(path.join(webts, "src/tslib", f)).href);

const crypto = await lib("crypto.ts");
const identity = await lib("identity.ts");
const license = await lib("license.ts");
const commands = await lib("commands.ts");
const require = createRequire(path.join(webts, "package.json"));
const sodium = require("libsodium-wrappers-sumo");
await sodium.ready;

const hex = (b: Uint8Array) => Buffer.from(b).toString("hex");

const eax = [0, 5, 16, 33, 64].map((n) => {
  const key = randomBytes(16), nonce = randomBytes(16), header = randomBytes(5), plain = randomBytes(n);
  const { ciphertext, mac } = crypto.eaxEncrypt(key, nonce, header, plain, 8);
  return { key: hex(key), nonce: hex(nonce), header: hex(header), plain: hex(plain), ciphertext: hex(ciphertext), mac: hex(mac) };
});

const keyNonce = [20, 64].flatMap((ivLen) => [true, false].map((fromServer) => {
  const iv = randomBytes(ivLen);
  const packetId = Math.floor(Math.random() * 65536), gen = Math.floor(Math.random() * 3), type = 2;
  const { key, nonce } = crypto.deriveKeyNonce(fromServer, packetId, gen, type, iv);
  return { iv: hex(iv), fromServer, packetId, gen, type, key: hex(key), nonce: hex(nonce) };
}));

// Identities: two new ones, the older handshake's shared secret between them, and a signature.
const a = identity.generateIdentity(8);
const b = identity.generateIdentity(8);
const signed = randomBytes(40);
const { sign } = await import("node:crypto");
const identities = {
  a: { scalar: a.privateKeyBigInt.toString(16), offset: a.keyOffset.toString(), publicKeyString: a.publicKeyString, uid: a.uid },
  b: { scalar: b.privateKeyBigInt.toString(16), offset: b.keyOffset.toString(), publicKeyString: b.publicKeyString, uid: b.uid },
  sharedSecretAB: hex(identity.getSharedSecret(a.privateKey, Buffer.from(b.publicKeyString, "base64"))),
  signedData: hex(signed),
  signatureA: hex(sign("SHA256", signed, a.privateKey)),
};

// Identity "a" in the TeamSpeak export format. (WebTS's own reader takes the scrambling key as hex; TeamSpeak's
// format uses its characters as text, so the export is made here.)
const OBF = Buffer.from(
  "b9dfaa7bee6ac57ac7b65f1094a1c155e747327bc2fe5d51c512023fe54a2802" +
  "01004e90ad1daaae1075d53b7d571c30e063b5a62a4a017bb394833aa0983e6e", "ascii");
function derInt(v: bigint) {
  let h = v.toString(16); if (h.length % 2) h = "0" + h;
  let buf = Buffer.from(h, "hex"); if (buf[0] & 0x80) buf = Buffer.concat([Buffer.from([0]), buf]);
  return Buffer.concat([Buffer.from([0x02, buf.length]), buf]);
}
function exportTs(scalar: bigint, x: bigint, y: bigint, offset: bigint) {
  const content = Buffer.concat([Buffer.from([0x03, 0x02, 0x07, 0x80]), derInt(32n), derInt(x), derInt(y), derInt(scalar)]);
  const len = content.length < 0x80 ? [content.length] : [0x81, content.length];
  const der = Buffer.concat([Buffer.from([0x30, ...len]), content]);
  const inner = Buffer.from(der.toString("base64"), "ascii");
  for (let i = 0; i < Math.min(100, inner.length); i++) inner[i] ^= OBF[i];
  let end = inner.length;
  for (let i = 20; i < inner.length; i++) if (inner[i] === 0) { end = i; break; }
  const h = createHash("sha1").update(inner.subarray(20, end)).digest();
  for (let i = 0; i < 20; i++) inner[i] ^= h[i];
  return `${offset}V${inner.toString("base64")}`;
}
const jwk = a.publicKey.export({ format: "jwk" }) as any;
const ax = BigInt("0x" + Buffer.from(jwk.x, "base64url").toString("hex"));
const ay = BigInt("0x" + Buffer.from(jwk.y, "base64url").toString("hex"));
const exported = exportTs(a.privateKeyBigInt, ax, ay, a.keyOffset);
const tsExport = { exported, scalar: a.privateKeyBigInt.toString(16), uid: a.uid };

// A license chain: an intermediate and an ephemeral block on random points.
function point() {
  const s = randomBytes(32); s[0] &= 248; s[31] &= 127; s[31] |= 64;
  return Buffer.from(sodium.crypto_scalarmult_ed25519_base_noclamp(s));
}
const intermediate = Buffer.concat([Buffer.from([0]), point(), Buffer.from([0]), randomBytes(8), randomBytes(4), Buffer.from("Test\0", "ascii")]);
const ephemeral = Buffer.concat([Buffer.from([0]), point(), Buffer.from([32]), randomBytes(8)]);
const licenseBytes = Buffer.concat([Buffer.from([1]), intermediate, ephemeral]);
const serverKey = await license.deriveLicenseKey(license.parseLicense(licenseBytes));
const temp = randomBytes(32); temp[0] &= 248; temp[31] &= 127; temp[31] |= 64;
const licenseVec = {
  license: hex(licenseBytes),
  serverKey: hex(serverKey),
  tempPrivate: hex(temp),
  tempPublic: hex(sodium.crypto_scalarmult_ed25519_base_noclamp(temp)),
  sharedSecret: hex(await license.getSharedSecret2(serverKey, temp)),
};

const cmd = {
  built: commands.buildCommand("sendtextmessage", { targetmode: 2, target: 1, msg: "Hei | maailma / \\ äö\n" }),
  hashedPassword: crypto.hashPassword("salainen sana ä"),
};

// Where avatars and icons are in the server's files (WebTS's src/pictures.ts)
const pictures = await import(pathToFileURL(path.join(webts, "src/pictures.ts")).href);
const files = {
  uid: a.uid,
  avatarNames: pictures.avatarFileNames(a.uid),
  serverNamed: pictures.avatarFileNames(a.uid, "abcdefghijklmnopabcdefghijklmnop"),
  icon: pictures.iconFileName(3000000000),
  builtinIcon: pictures.iconFileName(300),
  pictureMessage: pictures.pictureMessage("2026-10-12_18-35-01_a7k3f9.jpg", 5),
};

writeFileSync(out, JSON.stringify({ eax, keyNonce, identities, tsExport, license: licenseVec, cmd, files }, null, 2) + "\n");
console.log(`Wrote ${out}`);
