const VERSION_TAG_PATTERN = /^v?(\d+)\.(\d+)\.(\d+)(?:[-+].*)?$/;

const SECTION_ORDER = [
  "Breaking Changes",
  "Features",
  "Fixes",
  "Documentation",
  "Maintenance",
];

const BUMP_PRIORITY = { patch: 1, minor: 2, major: 3 };

export function parseVersion(tagName) {
  const match = String(tagName || "").match(VERSION_TAG_PATTERN);
  if (!match) {
    throw new Error(`Release boundary "${tagName}" is not a semantic version tag such as v1.2.3.`);
  }
  return { major: Number(match[1]), minor: Number(match[2]), patch: Number(match[3]) };
}

export function isVersionTag(tagName) {
  return VERSION_TAG_PATTERN.test(tagName);
}

export function bumpVersion(current, bump) {
  if (bump === "major") return { major: current.major + 1, minor: 0, patch: 0 };
  if (bump === "minor") return { major: current.major, minor: current.minor + 1, patch: 0 };
  return { ...current, patch: current.patch + 1 };
}

export function formatVersion(version) {
  return `${version.major}.${version.minor}.${version.patch}`;
}

export function classifyCommit(subject, body = "") {
  const message = `${subject}\n${body}`;
  if (/^[a-z]+(?:\([^)]*\))?!:/i.test(subject) || /(^|\n)BREAKING[ -]CHANGE:/i.test(message)) {
    return { section: "Breaking Changes", bump: "major" };
  }
  if (/^feat(?:\([^)]*\))?:/i.test(subject)) {
    return { section: "Features", bump: "minor" };
  }
  if (/^fix(?:\([^)]*\))?:/i.test(subject)) {
    return { section: "Fixes", bump: "patch" };
  }
  if (/^docs(?:\([^)]*\))?:/i.test(subject)) {
    return { section: "Documentation", bump: "patch" };
  }
  return { section: "Maintenance", bump: "patch" };
}

export function buildReleasePlan({ branch, boundary, targetSha, commits, repositoryUrl }) {
  if (commits.length === 0) {
    return {
      releaseRequired: false,
      branch,
      boundary,
      targetSha,
      versionBump: "none",
      nextVersion: "",
      changedCommitCount: 0,
      decisionReason: `No commits found after ${boundary || "repository start"} on ${branch}.`,
      releaseNotesMarkdown: "No releasable changes.",
    };
  }

  let versionBump = "patch";
  const sections = new Map(SECTION_ORDER.map((section) => [section, []]));

  for (const commit of commits) {
    const classification = classifyCommit(commit.subject, commit.body);
    if (BUMP_PRIORITY[classification.bump] > BUMP_PRIORITY[versionBump]) {
      versionBump = classification.bump;
    }
    sections.get(classification.section).push(commit);
  }

  const currentVersion = boundary ? parseVersion(boundary) : { major: 0, minor: 0, patch: 0 };
  const nextVersion = formatVersion(bumpVersion(currentVersion, versionBump));
  const markdown = [];
  for (const [section, entries] of sections.entries()) {
    if (entries.length === 0) continue;
    markdown.push(`## ${section}`);
    for (const entry of entries) {
      markdown.push(`- ${entry.subject} ([${entry.sha.slice(0, 7)}](${repositoryUrl}/commit/${entry.sha}))`);
    }
    markdown.push("");
  }

  return {
    releaseRequired: true,
    branch,
    boundary,
    targetSha,
    versionBump,
    nextVersion,
    changedCommitCount: commits.length,
    decisionReason: `Found ${commits.length} commit(s) after ${boundary || "repository start"} on ${branch}; selected ${versionBump} bump.`,
    releaseNotesMarkdown: markdown.join("\n").trim(),
  };
}
