import assert from "node:assert/strict";
import test from "node:test";
import { buildReleasePlan, classifyCommit, parseVersion } from "./release-plan.mjs";

test("parses semantic version boundary tags", () => {
  assert.deepEqual(parseVersion("v1.2.3"), { major: 1, minor: 2, patch: 3 });
  assert.deepEqual(parseVersion("v1.2.3-rc.1"), { major: 1, minor: 2, patch: 3 });
  assert.throws(() => parseVersion("release-candidate"), /semantic version/i);
});

test("classifies conventional commits and breaking changes", () => {
  assert.deepEqual(classifyCommit("feat(api): add endpoint"), { section: "Features", bump: "minor" });
  assert.deepEqual(classifyCommit("fix: repair release"), { section: "Fixes", bump: "patch" });
  assert.deepEqual(classifyCommit("feat!: remove field"), { section: "Breaking Changes", bump: "major" });
  assert.deepEqual(classifyCommit("chore: cleanup", "BREAKING CHANGE: change configuration"), {
    section: "Breaking Changes",
    bump: "major",
  });
});

test("builds release notes and selects the highest version bump", () => {
  const plan = buildReleasePlan({
    branch: "main",
    boundary: "v1.2.3",
    targetSha: "abcdef1234567",
    repositoryUrl: "https://github.com/elegant-software/blitz-pay",
    commits: [
      { sha: "1111111abcdef", subject: "fix: repair payment", body: "" },
      { sha: "2222222abcdef", subject: "feat: add voice payment", body: "" },
    ],
  });

  assert.equal(plan.releaseRequired, true);
  assert.equal(plan.versionBump, "minor");
  assert.equal(plan.nextVersion, "1.3.0");
  assert.equal(plan.changedCommitCount, 2);
  assert.match(plan.releaseNotesMarkdown, /## Features/);
  assert.match(plan.releaseNotesMarkdown, /\[2222222\]/);
});

test("does not publish when there are no changes after the boundary", () => {
  const plan = buildReleasePlan({
    branch: "main",
    boundary: "v1.2.3",
    targetSha: "abcdef1234567",
    repositoryUrl: "https://github.com/elegant-software/blitz-pay",
    commits: [],
  });

  assert.equal(plan.releaseRequired, false);
  assert.equal(plan.versionBump, "none");
  assert.equal(plan.nextVersion, "");
});
