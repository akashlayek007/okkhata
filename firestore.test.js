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

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp() {
  return new Date(Date.now() - 2000);
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

test("Unauthenticated user: cannot read or write customers or transactions", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/customers`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/customers`).doc("cust_1").set({
      name: "Test",
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/transactions`).get());
});

test("Authenticated owner: can create, update, list, and delete customer", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const custRef = aliceDb.collection(`users/${ALICE_UID}/customers`).doc("cust_1");

  await assertSucceeds(
    custRef.set({
      name: "Ramesh Kumar",
      phone: "9876543210",
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/customers`).get());

  await assertSucceeds(
    custRef.update({
      name: "Ramesh K",
      phone: "9999999999",
      updatedAt: pastTimestamp(),
    })
  );

  await assertSucceeds(custRef.delete());
});

test("Cross-user isolation: Bob cannot read, create, update, or delete Alice's records", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection(`users/${ALICE_UID}/customers`).doc("cust_1").set({
      name: "Alice Customer",
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection(`users/${ALICE_UID}/customers`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/customers`).doc("cust_1").get());
  await assertFails(
    bobDb.collection(`users/${ALICE_UID}/customers`).doc("cust_2").set({
      name: "Injected",
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(
    bobDb.collection(`users/${ALICE_UID}/customers`).doc("cust_1").update({
      name: "Hacked",
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(bobDb.collection(`users/${ALICE_UID}/customers`).doc("cust_1").delete());
});

test("Validation: rejects empty customer name, ghost fields, and invalid transaction amounts", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const custRef = aliceDb.collection(`users/${ALICE_UID}/customers`).doc("cust_1");

  await assertFails(
    custRef.set({
      name: "",
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  // Ghost field check
  await assertFails(
    custRef.set({
      name: "Valid Name",
      isVerified: true,
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  // Orphaned transaction (customer does not exist)
  const txRef = aliceDb.collection(`users/${ALICE_UID}/transactions`).doc("tx_1");
  await assertFails(
    txRef.set({
      customerId: "cust_1",
      type: "CREDIT",
      amount: 10000,
      transactionDate: pastTimestamp(),
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  // Create valid customer first
  await assertSucceeds(
    custRef.set({
      name: "Valid Name",
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  // Reject zero or negative or float amount
  await assertFails(
    txRef.set({
      customerId: "cust_1",
      type: "CREDIT",
      amount: 0,
      transactionDate: pastTimestamp(),
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(
    txRef.set({
      customerId: "cust_1",
      type: "CREDIT",
      amount: 10.5,
      transactionDate: pastTimestamp(),
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  // Reject invalid type
  await assertFails(
    txRef.set({
      customerId: "cust_1",
      type: "REFUND",
      amount: 5000,
      transactionDate: pastTimestamp(),
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  // Accept valid transaction
  await assertSucceeds(
    txRef.set({
      customerId: "cust_1",
      type: "CREDIT",
      amount: 50000,
      note: "Groceries",
      transactionDate: pastTimestamp(),
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  // Reject changing customerId on update
  await assertFails(
    txRef.update({
      customerId: "cust_other",
      updatedAt: pastTimestamp(),
    })
  );

  // Accept valid update
  await assertSucceeds(
    txRef.update({
      amount: 75000,
      note: "Updated groceries",
      updatedAt: pastTimestamp(),
    })
  );
});
