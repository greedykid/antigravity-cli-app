const { test } = require("node:test");
const assert = require("node:assert");
const quota = require("../quota");

// Exactly what `agy -p "/usage"` prints on this machine.
const REAL_OUTPUT = [
  "Gemini Models\tWeekly Limit Remaining\t66%\t2026-08-28T22:56:41Z",
  "Gemini Models\tFive Hour Limit Remaining\t99%\t2026-08-23T15:23:38Z",
  "Claude and GPT models\tWeekly Limit Remaining\t63%\t2026-08-30T03:23:01Z",
  "Claude and GPT models\tFive Hour Limit Remaining\t91%\t2026-08-23T14:26:11Z"
].join("\n");

test("parses the real report into groups", () => {
  const groups = quota.parseUsage(REAL_OUTPUT);
  assert.equal(groups.length, 2);
  assert.equal(groups[0].group, "Gemini Models");
  assert.equal(groups[0].limits.length, 2);
  assert.equal(groups[1].group, "Claude and GPT models");
});

test("keeps percentages and reset timestamps", () => {
  const weekly = quota.parseUsage(REAL_OUTPUT)[0].limits[0];
  assert.equal(weekly.label, "Weekly Limit Remaining");
  assert.equal(weekly.percent, 66);
  assert.equal(weekly.resetAt, "2026-08-28T22:56:41Z");
});

test("group order follows the report, not insertion per line", () => {
  const shuffled = [
    "Gemini Models\tWeekly\t50%\t2026-08-28T22:56:41Z",
    "Claude and GPT models\tWeekly\t40%\t2026-08-30T03:23:01Z",
    "Gemini Models\tFive Hour\t80%\t2026-08-23T15:23:38Z"
  ].join("\n");
  const groups = quota.parseUsage(shuffled);
  assert.equal(groups.length, 2);
  assert.equal(groups[0].group, "Gemini Models");
  assert.equal(groups[0].limits.length, 2, "both Gemini rows land in one group");
});

test("falls back to multi-space separation when there are no tabs", () => {
  const spaced = "Gemini Models    Weekly Limit Remaining    66%    2026-08-28T22:56:41Z";
  const groups = quota.parseUsage(spaced);
  assert.equal(groups.length, 1);
  assert.equal(groups[0].limits[0].label, "Weekly Limit Remaining");
  assert.equal(groups[0].limits[0].percent, 66);
});

test("ignores banners and blank lines that carry no percentage", () => {
  const noisy = "Loading usage...\n\n" + REAL_OUTPUT + "\nDone.";
  assert.equal(quota.parseUsage(noisy).length, 2);
});

test("percentages are clamped to 0-100", () => {
  const odd = "Group A\tLimit\t140%\t2026-08-28T22:56:41Z\nGroup B\tLimit\t0%\t2026-08-28T22:56:41Z";
  const groups = quota.parseUsage(odd);
  assert.equal(groups[0].limits[0].percent, 100);
  assert.equal(groups[1].limits[0].percent, 0);
});

test("a missing timestamp yields null rather than a bogus date", () => {
  const groups = quota.parseUsage("Group A\tLimit\t55%");
  assert.equal(groups[0].limits[0].percent, 55);
  assert.equal(groups[0].limits[0].resetAt, null);
});

test("empty or garbage output parses to no groups", () => {
  assert.deepEqual(quota.parseUsage(""), []);
  assert.deepEqual(quota.parseUsage("Error: not logged in"), []);
  assert.deepEqual(quota.parseUsage(null), []);
});

test("a failed CLI read returns no groups instead of throwing", () => {
  quota.reset();
  const result = quota.get("/definitely/not/a/binary");
  assert.equal(result.groups, null);
  assert.equal(result.stale, false);
});
