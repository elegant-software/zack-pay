const assert = require("node:assert/strict");
const { mkdirSync, mkdtempSync, rmSync, writeFileSync } = require("node:fs");
const { tmpdir } = require("node:os");
const { join } = require("node:path");
const test = require("node:test");
const publishRelease = require("./publish-release.cjs");

function inReleaseWorkspace(callback) {
  const originalDirectory = process.cwd();
  const workspace = mkdtempSync(join(tmpdir(), "publish-release-"));
  mkdirSync(join(workspace, "release-output"));
  writeFileSync(join(workspace, "release-output/release-plan.json"), JSON.stringify({
    releaseRequired: true,
    nextVersion: "1.3.0",
    targetSha: "target-sha",
  }));
  writeFileSync(join(workspace, "release-output/release-notes.md"), "## Features\n- add release planning\n");
  process.chdir(workspace);
  return Promise.resolve(callback()).finally(() => {
    process.chdir(originalDirectory);
    rmSync(workspace, { recursive: true, force: true });
  });
}

function context() {
  return { repo: { owner: "elegant-software", repo: "blitz-pay" } };
}

test("creates a missing tag and release at the planned target SHA", async () => {
  const calls = [];
  const github = {
    rest: {
      git: {
        getRef: async () => { throw Object.assign(new Error("not found"), { status: 404 }); },
        createRef: async (parameters) => { calls.push(["createRef", parameters]); },
      },
      repos: {
        createRelease: async (parameters) => { calls.push(["createRelease", parameters]); },
      },
    },
  };

  await inReleaseWorkspace(() => publishRelease({ github, context: context(), core: { info() {} } }));

  assert.equal(calls[0][0], "createRef");
  assert.equal(calls[0][1].sha, "target-sha");
  assert.equal(calls[1][0], "createRelease");
  assert.equal(calls[1][1].tag_name, "v1.3.0");
});

test("refuses to publish when an existing version tag targets another commit", async () => {
  const github = {
    rest: {
      git: {
        getRef: async () => ({ data: { object: { sha: "different-sha" } } }),
      },
    },
  };

  await assert.rejects(
    inReleaseWorkspace(() => publishRelease({ github, context: context(), core: { info() {} } })),
    /different commit/,
  );
});
