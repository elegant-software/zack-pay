import { appendFileSync, mkdirSync, writeFileSync } from "node:fs";
import { execFileSync, spawnSync } from "node:child_process";
import { buildReleasePlan, isVersionTag } from "./release-plan.mjs";

const OUTPUT_DIRECTORY = "release-output";

function git(args) {
  return execFileSync("git", args, {
    encoding: "utf8",
    stdio: ["ignore", "pipe", "pipe"],
  }).trim();
}

function gitSucceeds(args) {
  return spawnSync("git", args, { stdio: "ignore" }).status === 0;
}

function setOutput(name, value) {
  const outputFile = process.env.GITHUB_OUTPUT;
  if (!outputFile) throw new Error("GITHUB_OUTPUT is not set.");
  appendFileSync(outputFile, `${name}=${String(value)}\n`);
}

function readCommits(range) {
  const rawLog = git(["log", "--reverse", "--pretty=format:%H%x1f%s%x1f%b%x1e", range]);
  return rawLog
    .split("\x1e")
    .map((record) => record.trim())
    .filter(Boolean)
    .map((record) => {
      const [sha, subject, ...body] = record.split("\x1f");
      return { sha, subject, body: body.join("\x1f") };
    });
}

function resolveBoundary(requestedBoundary, targetRef) {
  if (requestedBoundary) {
    if (!isVersionTag(requestedBoundary)) {
      throw new Error(`Release boundary "${requestedBoundary}" is not a semantic version tag such as v1.2.3.`);
    }
    return requestedBoundary;
  }

  return git(["tag", "--merged", targetRef, "--sort=-v:refname"])
    .split("\n")
    .find((tag) => isVersionTag(tag)) || "";
}

function writeOutputs(plan) {
  setOutput("release_required", plan.releaseRequired);
  setOutput("version_bump", plan.versionBump);
  setOutput("next_version", plan.nextVersion);
  setOutput("target_sha", plan.targetSha);
  setOutput("changed_commit_count", plan.changedCommitCount);
  setOutput("decision_reason", plan.decisionReason);
}

function writeReleaseFiles(plan) {
  mkdirSync(OUTPUT_DIRECTORY, { recursive: true });
  writeFileSync(`${OUTPUT_DIRECTORY}/release-plan.json`, `${JSON.stringify(plan, null, 2)}\n`);
  writeFileSync(`${OUTPUT_DIRECTORY}/release-notes.md`, `${plan.releaseNotesMarkdown}\n`);
}

function main() {
  const branch = (process.env.BASE_BRANCH || "main").trim();
  const requestedBoundary = (process.env.RELEASE_BOUNDARY || "").trim();
  const repositoryUrl = String(process.env.REPOSITORY_URL || "").replace(/\/$/, "");
  const targetRef = `origin/${branch}`;

  if (!repositoryUrl) throw new Error("REPOSITORY_URL is required.");
  if (!gitSucceeds(["check-ref-format", "--branch", branch])) {
    throw new Error(`Base branch "${branch}" is not a valid branch name.`);
  }

  git(["fetch", "origin", `+refs/heads/${branch}:refs/remotes/origin/${branch}`, "--force", "--no-tags"]);
  git(["fetch", "origin", "--force", "--tags"]);

  if (!gitSucceeds(["rev-parse", "--verify", `${targetRef}^{commit}`])) {
    throw new Error(`Branch "${branch}" was not found on origin.`);
  }

  const targetSha = git(["rev-parse", `${targetRef}^{commit}`]);
  const boundary = resolveBoundary(requestedBoundary, targetRef);
  if (boundary && !gitSucceeds(["rev-parse", "--verify", `refs/tags/${boundary}^{commit}`])) {
    throw new Error(`Release boundary tag "${boundary}" was not found.`);
  }
  if (boundary && !gitSucceeds(["merge-base", "--is-ancestor", boundary, targetRef])) {
    throw new Error(`Release boundary tag "${boundary}" is not an ancestor of ${targetRef}.`);
  }

  const range = boundary ? `${boundary}..${targetRef}` : targetRef;
  const commits = readCommits(range);
  const plan = buildReleasePlan({ branch, boundary, targetSha, commits, repositoryUrl });

  writeOutputs(plan);
  writeReleaseFiles(plan);
  console.log(plan.decisionReason);
  console.log(`Next version: ${plan.nextVersion || "none"}`);
}

main();
